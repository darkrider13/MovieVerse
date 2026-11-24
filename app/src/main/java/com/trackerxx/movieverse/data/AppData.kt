package com.trackerxx.movieverse.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's data, the same globals the web version keeps at the top of its script:
 * movies, folders, statuses (+ SL), uname, theme, API keys, backup "dirty" marker.
 * Created once in MovieVerseApplication.
 */
object AppData {
    lateinit var db: MovieDb
        private set
    lateinit var apis: ApiKeys
        private set

    /** the library (loaded from the database at start) */
    var movies: MutableList<Movie> = ArrayList()

    /** ready: the library has finished loading from the database */
    var ready by mutableStateOf(false)

    /** bumped by render(): every screen that shows movies reads it, so changes show up (the web version's render()) */
    private val revState = mutableIntStateOf(0)
    val rev: Int get() = revState.intValue

    /** render(): tell the screens that movies / folders / statuses changed (safe to call from any thread) */
    fun render() {
        revState.intValue = revState.intValue + 1
    }

    var folders: MutableList<Folder> = ArrayList()
    var statuses: MutableList<Status> = ArrayList()

    /** status id -> status name */
    var SL: Map<String, String> = emptyMap()
        private set

    /** the user's name (hero title on the library page) */
    private val unameState = mutableStateOf("")
    var uname: String
        get() = unameState.value
        private set(v) { unameState.value = v }

    /** start time of the backup being written (bkT) */
    private var bkT = 0L

    fun init(ctx: Context) {
        db = MovieDb(ctx)

        // folders = JSON.parse(LS 'library.folders' || '[]')
        folders = try {
            val a = JSONArray(Ls.getItem("library.folders") ?: "[]")
            val out = ArrayList<Folder>()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(Folder(it)) }
            out
        } catch (e: Exception) {
            ArrayList()
        }

        // statuses = JSON.parse(LS 'library.statuses') || the five defaults
        var st: MutableList<Status>? = try {
            val raw = Ls.getItem("library.statuses")
            if (raw == null) null else {
                val a = JSONArray(raw)
                val out = ArrayList<Status>()
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(Status(it)) }
                out
            }
        } catch (e: Exception) {
            null
        }
        if (st == null) {
            // a new library starts with three pills: Coming Soon, Plan to watch, Watched (everything else the person adds himself)
            st = arrayListOf(
                Status.create("upcoming", "Coming Soon"),
                Status.create("plan", "Plan to watch"),
                Status.create("watched", "Watched")
            )
            Ls.setItem("library.st5", "1")
            Ls.setItem("library.ord3", "1")
        }
        statuses = st
        uname = Ls.getItem("library.name") ?: ""
        rebuildSL()

        // one-time upgrade 1: make sure "Coming Soon" and "Favourite" exist
        if (Ls.getItem("library.st5").isNullOrEmpty()) {
            for ((id, name) in listOf("upcoming" to "Coming Soon", "favourite" to "Favourite")) {
                if (statuses.none { it.id == id }) statuses.add(Status.create(id, name))
            }
            saveStatuses()
            Ls.setItem("library.st5", "1")
        }
        // one-time upgrade 2: "Upcoming" is now called "Coming Soon"
        if (Ls.getItem("library.csn").isNullOrEmpty()) {
            var ch = false
            for (x in statuses) {
                if (x.id == "upcoming" && x.name == "Upcoming") {
                    x.name = "Coming Soon"
                    ch = true
                }
            }
            if (ch) saveStatuses()
            Ls.setItem("library.csn", "1")
        }
        // one-time upgrade 3: the pills start with Coming Soon, Plan to watch, Watched (the "All" tab is gone). Other pills keep
        // their order after them; nothing is deleted.
        if (Ls.getItem("library.ord3").isNullOrEmpty()) {
            val first = listOf("upcoming", "plan", "watched").mapNotNull { id -> statuses.firstOrNull { it.id == id } }
            val rest = statuses.filter { s -> first.none { it.id == s.id } }
            statuses = (first + rest).toMutableList()
            saveStatuses()
            Ls.setItem("library.ord3", "1")
        }
        rebuildSL()

        apis = ApiKeys()

        initBk()
    }

    // ---- statuses / folders -----------------------------------------------
    fun rebuildSL() {
        val m = LinkedHashMap<String, String>()
        for (x in statuses) m[x.id] = x.name
        SL = m
    }

    fun saveStatuses() {
        Ls.setItem("library.statuses", jsonArrayOf(statuses.map { it.j }).toString())
    }

    fun saveFolders() {
        Ls.setItem("library.folders", jsonArrayOf(folders.map { it.j }).toString())
    }

    fun foldersJson(): String = jsonArrayOf(folders.map { it.j }).toString()
    fun statusesJson(): String = jsonArrayOf(statuses.map { it.j }).toString()

    // ---- user name ----------------------------------------------------------
    fun setName(v: String) {
        uname = v.trim()
        Ls.setItem("library.name", uname)
    }

    // ---- ids ----------------------------------------------------------------
    private const val B36 = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** uid(): Date.now() in base 36 + 3 random base-36 characters */
    fun uid(): String {
        val sb = StringBuilder(java.lang.Long.toString(System.currentTimeMillis(), 36))
        for (i in 0 until 3) sb.append(B36[(Math.random() * 36).toInt().coerceIn(0, 35)])
        return sb.toString()
    }

    // ---- backup "dirty" marker (the red dot on Settings / Export) ---------------
    private fun bkTime(): Double = Ls.getItem("library.bk")?.trim()?.toDoubleOrNull() ?: 0.0

    /** true when a movie was added after the last export */
    fun isDirty(): Boolean {
        val b = bkTime()
        return movies.any { it.added.toDouble() > b }
    }

    /** call when an export starts (bkT = Date.now()) */
    fun beginExport() {
        bkT = System.currentTimeMillis()
    }

    /** setDirty(false): the library has just been exported */
    fun markExported() {
        Ls.setItem("library.bk", (if (bkT != 0L) bkT else System.currentTimeMillis()).toString())
        render()   // paintDirty(): the red blink of the gear / Export button stops
    }

    private fun initBk() {
        if (Ls.getItem("library.bkv2") != null) return
        Ls.setItem("library.bk", System.currentTimeMillis().toString())
        Ls.setItem("library.bkv2", "1")
        Ls.removeItem("library.dirty")
    }

    // ---- settings part of the backup file ---------------------------------------
    /** setStr(): {"name":..,"theme":..,"apis":[..]} */
    fun settingsJson(): String {
        val o = JSONObject()
        o.put("name", uname)
        o.put("theme", "dark")
        o.put("apis", apis.apisJson())
        return o.toString()
    }

    /** restoreSet(t): name, theme and API keys of an imported backup */
    fun restoreSettings(t: JSONObject?) {
        if (t == null) return
        val nm = t.opt("name")
        if (nm is String && nm.trim().isNotEmpty()) {
            uname = nm.trim()
            Ls.setItem("library.name", uname)
        }
        val a = t.optJSONArray("apis")
        if (a != null) apis.restore(a)
    }
}
