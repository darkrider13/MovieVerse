package com.trackerxx.movieverse.enrich

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateMapOf
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.net.StateDispatcher
import com.trackerxx.movieverse.ui.components.ToastState
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Pull-to-refresh (the IIFE after `paintDirty()` at the end of the web script): `snapF`, `sig`, `refreshHot`, `msg`, `run`.
 * Pull down on Your Library: up to 10 times in each 6-hour slot, only Coming Soon titles that are "hot" (see Enrich.hotMode) are refreshed.
 * Pull down on Trending is Trending.silent() (decided by the caller).
 */
object Refresh {
    /** updSet: ids of the movies being refreshed right now (their card shows the dark cover with the spinner) */
    val upd = mutableStateMapOf<String, Boolean>()

    @Volatile
    private var busy = false

    val isBusy: Boolean get() = busy

    // ---- snapF / sig ---------------------------------------------------------------------------------------------------

    private fun v(m: Movie, k: String): String = m.str(k) ?: ""                         // undefined / null -> '' (Array.join)
    private fun t(m: Movie, k: String): String = if (m.truthy(k)) (m.str(k) ?: "") else "" // (m.x || '')

    private fun ottJoin(m: Movie): String {
        val a = m.arr("ott") ?: return ""
        return (0 until a.length()).joinToString(",") { if (a.isNull(it)) "" else a.get(it).toString() }
    }

    /** snapF(m): the visible fields of a movie as text, so a refresh can tell which fields changed */
    fun snapF(m: Movie): Map<String, Any?> {
        val md = m.obj("md")?.let { o -> JSONObject(o.toString()).also { it.remove("d") } }
        val seasons = if (m.isNull("seasons")) "" else (m.str("seasons") ?: "")
        val eps = if (m.isNull("eps")) "" else (m.str("eps") ?: "")
        val xmom = Js.or(m.j.opt("xm"), m.j.opt("om"), "")
        return linkedMapOf(
            "Rating" to ((if (Js.truthy(xmom)) xmom.toString() else "") + "|" + (md?.toString() ?: "{}") + "|" + t(m, "tm")),
            "Votes" to t(m, "vc"),
            "Rotten Tomatoes" to t(m, "rt"),
            "In cinemas" to t(m, "rel"),
            "OTT release" to listOf(v(m, "ottDate"), ottJoin(m), if (m.tmdbId < 0) v(m, "rel") else "").joinToString("|"),
            "Budget" to (v(m, "wbud") + "|" + v(m, "wcur")),
            "Collection" to (v(m, "wrev") + "|" + v(m, "wcur")),
            "Seasons" to seasons,
            "Episodes" to eps
        )
    }

    /** sig(m): one text that changes when anything a refresh looks up changes */
    private fun sig(m: Movie): String {
        val md = if (m.truthy("md")) (m.j.opt("md")?.toString() ?: "\"\"") else "\"\""
        return listOf(
            v(m, "imdb"), v(m, "xm"), v(m, "om"), md, v(m, "tm"), v(m, "vc"), v(m, "rt"), v(m, "wbud"), v(m, "wrev"), v(m, "wcur"),
            ottJoin(m), v(m, "ottDate"), v(m, "rel"), v(m, "runtime")
        ).joinToString("|")
    }

    // ---- refreshHot ----------------------------------------------------------------------------------------------------

    /**
     * refreshHot(): 4 at a time, each hot movie gets its lookups (All: OTT + details + Wikipedia + MDBList, Rating: details only).
     * Returns the titles of the movies that really changed.
     */
    private suspend fun refreshHot(): List<String> = withContext(StateDispatcher) {
        val todo = AppData.movies.toTypedArray().filter { Enrich.hot(it) }
        val ch = ArrayList<Movie>()
        val names = ArrayList<String>()
        if (todo.isEmpty()) return@withContext names
        for (m in todo) upd[m.id] = true
        var i = 0
        try {
            coroutineScope {
                repeat(minOf(4, todo.size)) {
                    launch {
                        while (i < todo.size) {
                            val m = todo[i++]
                            val b = sig(m)
                            val bf = snapF(m)
                            try {
                                if (Enrich.hotMode(m) == HotMode.All) {
                                    val a = Enrich.enrichOtt(m, true)
                                    val x = Enrich.enrich(m, true)
                                    val c = Enrich.enrichWiki(m, true)
                                    val l = Enrich.enrichMd(m)
                                    if ((a || x || c || l) && !ch.contains(m)) ch.add(m)
                                } else {
                                    val x = Enrich.enrich(m, true)
                                    if (x && !ch.contains(m)) ch.add(m)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // .catch(function(){})
                            }
                            // a rating / votes / Rotten Tomatoes change alone is quiet: no pulse, not counted in "updated"
                            if (sig(m) != b && Enrich.noteChg(m, bf)) {
                                names.add(m.title)
                            }
                            delay(20)
                        }
                    }
                }
            }
        } finally {
            for (m in todo) upd.remove(m.id)
        }
        if (ch.isNotEmpty()) {
            AppData.db.putMany(ch)
            AppData.render()
        }
        names
    }

    private fun msg(names: List<String>): String = names.size.toString() + (if (names.size == 1) " movie" else " movies") + " updated"

    private fun online(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            true
        }
    }

    // ---- run -----------------------------------------------------------------------------------------------------------

    /** run() for Your Library (the caller handles Trending): limits, checks, refresh, toast */
    suspend fun runLibrary(ctx: Context, toast: ToastState) {
        if (busy) return
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sl = h / 6 + 1
        val td = Fmt.todayL() + "#" + sl
        var cnt = 0
        val LIM = 10
        try {
            val pv = (Ls.getItem("library.pull") ?: "").split(":")
            if (pv[0] == td) cnt = Js.parseInt(pv.getOrNull(1)).let { if (it.isNaN()) 0 else it.toInt() }
        } catch (e: Exception) { }
        val api = AppData.apis
        if (api.key.isEmpty()) {
            api.keys = api.apiKeys("tmdb").filter { it.isNotEmpty() }.toMutableList()
            api.ki = 0
            api.pickKeys()
        }
        if (api.key.isEmpty()) {
            toast.show("Add your TMDB key in Settings", 3200L)
            return
        }
        if (cnt >= LIM) {
            toast.show("Recently Updated", 2600L)
            return
        }
        if (!online(ctx)) {
            toast.show("No internet connection", 2600L)
            return
        }
        busy = true
        var names: List<String>? = null
        try {
            names = refreshHot()
            try { Ls.setItem("library.pull", td + ":" + (cnt + 1)) } catch (e: Exception) { }
        } catch (e: CancellationException) {
            busy = false
            throw e
        } catch (e: Exception) {
            names = null
        }
        busy = false
        val n = names
        toast.show(if (n != null) msg(n) else "Could not update", if (n != null && n.isNotEmpty()) 5000L else 2600L)
    }
}
