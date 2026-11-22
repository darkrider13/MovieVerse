package com.trackerxx.movieverse.trending

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import coil.request.ImageRequest
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.net.AppScope
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.net.Omdb
import com.trackerxx.movieverse.net.QItem
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.net.XmRes
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** one title in a Trending row: id (TV = negative), title, poster, date, TMDB vote average / count, TV flag, ranks, IMDb rating */
@Stable
class TrItem(
    val id: Long,
    val t: String,
    val p: String?,
    val d: String,
    val v: Double,
    val n: Int,
    val tv: Boolean
) {
    var s: Double? = null            // how high it ranks across the lists it is in
    var f: Double? = null            // final blended score
    var x by mutableStateOf<String?>(null)   // IMDb rating text (shows on the card when it arrives)

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("t", t)
        o.put("p", p ?: JSONObject.NULL)
        o.put("d", d)
        o.put("v", v)
        o.put("n", n)
        o.put("tv", tv)
        s?.let { o.put("s", it) }
        f?.let { o.put("f", it) }
        x?.let { o.put("x", it) }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): TrItem {
            val it = TrItem(
                o.optLong("id"), o.optString("t", ""), if (o.isNull("p")) null else o.optString("p"),
                o.optString("d", ""), o.optDouble("v", 0.0), o.optInt("n", 0), o.optBoolean("tv", false)
            )
            if (!o.isNull("s") && o.has("s")) it.s = o.optDouble("s")
            if (!o.isNull("f") && o.has("f")) it.f = o.optDouble("f")
            if (!o.isNull("x") && o.has("x")) it.x = o.optString("x")
            return it
        }
    }
}

private class TrxEntry(val x: String, val t: Long)

/**
 * Trending page: Trending Movies (top 10, ranked from today's and this week's lists and IMDb ratings), Trending Today,
 * Weekly Trending, Trending TV Shows, Top Rated Movies. Cached for 3 hours; IMDb ratings are filled in afterwards.
 */
object Trending {
    private const val TRK = "library.tr3"
    private const val TRX = "library.trx2"
    private const val TTL = 108e5

    val ORDER = listOf("m", "d", "w", "t", "x")
    val TITLES = mapOf(
        "m" to "Trending Movies", "d" to "Trending Today", "w" to "Weekly Trending",
        "t" to "Trending TV Shows", "x" to "Top Rated Movies"
    )

    /** the rows (null / missing = not loaded yet) */
    val lists = mutableStateMapOf<String, List<TrItem>>()

    /** rows that could not be loaded ("Could not load." + Retry) */
    val failed = mutableStateMapOf<String, Boolean>()

    /** no TMDB key: the page shows the "add your key" note */
    var noKey by mutableStateOf(false)

    /** the Trending page is on screen (rating retries only run then) */
    @Volatile
    var visible = false

    private lateinit var appCtx: Context
    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    @Volatile
    private var netBusy = false
    @Volatile
    private var silentBusy = false

    private fun now(): Long = System.currentTimeMillis()
    private fun xmKeys(): Boolean = AppData.apis.apiKeys("xmdb").any { it.isNotEmpty() }
    private fun omKeys(): Boolean = AppData.apis.omdbs.any { it.isNotEmpty() }
    private fun hasSrc(): Boolean = xmKeys() || omKeys()

    // ---- cache of the rows -----------------------------------------------------------------------------------------------

    private class Cache(val t: Long, val d: Map<String, List<TrItem>?>)

    private fun readCache(): Cache? = try {
        val raw = Ls.getItem(TRK)
        if (raw == null) null else {
            val o = JSONObject(raw)
            val d = o.optJSONObject("d")
            if (d == null) null else {
                val m = HashMap<String, List<TrItem>?>()
                for (k in ORDER) {
                    val a = d.optJSONArray(k)
                    m[k] = if (a == null) null else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { j -> TrItem.fromJson(j) } }
                }
                Cache(o.optLong("t"), m)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun writeCache(d: Map<String, List<TrItem>?>) {
        try {
            val dj = JSONObject()
            for (k in ORDER) {
                val l = d[k]
                dj.put(k, if (l == null) JSONObject.NULL else JSONArray().also { a -> l.forEach { a.put(it.toJson()) } })
            }
            val o = JSONObject()
            o.put("t", now())
            o.put("d", dj)
            Ls.setItem(TRK, o.toString())
        } catch (e: Exception) {
        }
    }

    /** shows the cached rows at once (whatever their age) while a fresh load is on its way */
    fun showCached() {
        val c = readCache() ?: return
        if (lists.isNotEmpty()) return
        for (k in ORDER) c.d[k]?.let { lists[k] = it }
        enrich(ORDER.map { c.d[it] })
    }

    /** trFind(id): what the cache knows about a title (used to fill the Add form at once) */
    fun find(id: Long): QItem? {
        val c = readCache() ?: return null
        for (k in ORDER) {
            val l = c.d[k] ?: continue
            for (x in l) if (x.id == id) return QItem(id, x.t, x.d, x.p, "", 0.0, x.tv)
        }
        return null
    }

    // ---- loading the rows --------------------------------------------------------------------------------------------------

    /** tget(path, tv): one list from TMDB; no poster / fewer than 10 votes / repeated = dropped */
    private suspend fun tget(path: String, tv: Boolean = false): List<TrItem> {
        val j = Net.tmdb.get(path)
        val a = j.optJSONArray("results") ?: return emptyList()
        val seen = HashSet<Long>()
        val out = ArrayList<TrItem>()
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            val isTv = tv || x.opt("media_type") == "tv"
            val xid = x.optLong("id")
            val title = if (Js.truthy(x.opt("title"))) x.opt("title").toString() else if (Js.truthy(x.opt("name"))) x.opt("name").toString() else ""
            val poster = if (Js.truthy(x.opt("poster_path"))) x.opt("poster_path").toString() else null
            val date = if (Js.truthy(x.opt("release_date"))) x.opt("release_date").toString() else if (Js.truthy(x.opt("first_air_date"))) x.opt("first_air_date").toString() else ""
            val va = x.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it }
            val vc = x.optInt("vote_count", 0)
            val o = TrItem(if (isTv) -xid else xid, title, poster, date, va, vc, isTv)
            if (o.t.isEmpty() || o.p.isNullOrEmpty() || o.n < 10 || !seen.add(o.id)) continue
            out.add(o)
        }
        return out
    }

    /** trRank(L): one entry per title, scored by where it stands in each list */
    private fun rank(L: List<List<TrItem>>): List<TrItem> {
        val sc = LinkedHashMap<Long, Double>()
        val mp = LinkedHashMap<Long, TrItem>()
        for (l in L) l.forEachIndexed { i, o ->
            if (!mp.containsKey(o.id)) mp[o.id] = o
            sc[o.id] = (sc[o.id] ?: 0.0) + (1 - i.toDouble() / l.size) / L.size
        }
        return sc.keys.map { k -> mp[k]!!.also { it.s = sc[k] } }
    }

    private fun xNum(o: TrItem): Double = Js.parseFloat(o.x).let { if (it.isNaN()) 0.0 else it }

    private fun blendScore(o: TrItem): Double = 0.7 * (o.s ?: 0.0) + 0.3 * (if (!o.x.isNullOrEmpty()) xNum(o) / 10 else o.v / 10)

    /** trCombined(): "Trending Movies" = today's + this week's lists merged, best 18, re-ordered by IMDb rating when XMDb is set up */
    private suspend fun combined(): List<TrItem> {
        val L = coroutineScope {
            val a = async { tget("/trending/movie/day") }
            val b = async { tget("/trending/movie/week") }
            listOf(a.await(), b.await())
        }
        val top = rank(L).sortedByDescending { it.s ?: 0.0 }.take(18)
        fun fin(): List<TrItem> {
            top.forEach { it.f = blendScore(it) }
            return top.sortedByDescending { it.f ?: 0.0 }
        }
        if (!xmKeys()) return fin()
        val idx = AtomicInteger(0)
        coroutineScope {
            repeat(3) {
                launch {
                    while (true) {
                        val i = idx.getAndIncrement()
                        if (i >= top.size) break
                        val o = top[i]
                        try {
                            val e = Net.tmdb.get("/movie/" + o.id + "/external_ids")
                            val id = e.opt("imdb_id")
                            if (Js.truthy(id)) {
                                val x = Net.xmdb.lookup(id.toString())
                                if (x is XmRes.Ok && x.v != null && x.v.imdb.isNotEmpty()) o.x = x.v.imdb
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        return fin()
    }

    /** trBlend(list): re-order "Top Rated" by TMDB rank + IMDb rating (needs XMDb) */
    private suspend fun blend(list: List<TrItem>): List<TrItem> {
        if (list.size < 2 || !xmKeys()) return list
        list.forEachIndexed { i, o -> if (o.s == null) o.s = 1.0 - i.toDouble() / list.size }
        coroutineScope {
            list.map { o ->
                async {
                    if (o.x.isNullOrEmpty()) {
                        val v = xGet(o)
                        if (!v.isNullOrEmpty()) o.x = v
                    }
                }
            }.awaitAll()
        }
        list.forEach { it.f = blendScore(it) }
        return list.sortedByDescending { it.f ?: 0.0 }
    }

    private fun same(a: List<TrItem>, b: List<TrItem>): Boolean = a.size == b.size && a.indices.all { a[it].id == b[it].id }

    /** where each list is loaded from */
    private suspend fun fetchSection(k: String): List<TrItem> = when (k) {
        "m" -> combined()
        "d" -> tget("/trending/all/day")
        "w" -> tget("/trending/all/week")
        "t" -> tget("/trending/tv/week", true)
        else -> tget("/movie/top_rated")
    }

    /**
     * trendLoad(force): shows the cached rows (up to 3 hours old) or loads all five rows at the same time;
     * each row appears as soon as it is ready.
     */
    suspend fun load(force: Boolean = false) {
        while (netBusy) delay(100)
        if (AppData.apis.key.isEmpty()) {
            AppData.apis.keys = AppData.apis.apiKeys("tmdb").filter { it.isNotEmpty() }.toMutableList()
            AppData.apis.ki = 0
            AppData.apis.pickKeys()
        }
        if (AppData.apis.key.isEmpty()) {
            noKey = true
            return
        }
        noKey = false
        val c = readCache()
        if (c != null && !force && now() - c.t < TTL) {
            failed.clear()
            for (k in ORDER) c.d[k]?.let { lists[k] = it }
            enrich(ORDER.map { c.d[it] })
            return
        }
        netBusy = true
        try {
            val d = ConcurrentHashMap<String, List<TrItem>>()
            coroutineScope {
                ORDER.map { k ->
                    async {
                        try {
                            val l = fetchSection(k)
                            d[k] = l
                            failed.remove(k)
                            lists[k] = l
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed[k] = true
                            lists.remove(k)
                        }
                    }
                }.awaitAll()
            }
            val dm: Map<String, List<TrItem>?> = ORDER.associateWith { d[it] }
            if (!dm["m"].isNullOrEmpty()) writeCache(dm)
            enrich(ORDER.map { dm[it] })
            val xl = dm["x"]
            if (!xl.isNullOrEmpty()) {
                val bl = blend(xl)
                if (!same(bl, xl)) {
                    val nd = HashMap(dm)
                    nd["x"] = bl
                    lists["x"] = bl
                    if (!dm["m"].isNullOrEmpty()) writeCache(nd)
                }
            }
        } finally {
            netBusy = false
        }
    }

    /**
     * waitImgs(ms) of keyLoad: the posters at the top of the page (first row) are loaded, at most `ms`, so the loading dialog
     * closes on a filled page. (The web version waits for the images that are on screen and not loaded yet.)
     */
    suspend fun waitImgs(ms: Long) {
        val urls = (lists["m"] ?: emptyList()).take(10).mapNotNull { Tmdb.imgUrl(it.p, "w185") }
        if (urls.isEmpty()) return
        withTimeoutOrNull(ms) {
            coroutineScope { urls.map { u -> async { try { appCtx.imageLoader.execute(ImageRequest.Builder(appCtx).data(u).build()) } catch (e: Exception) { } } }.awaitAll() }
        }
    }

    /** pull-down on Trending: refresh quietly; a row changes only when its list really changed (new posters are loaded first) */
    suspend fun silent() {
        if (silentBusy) return
        if (AppData.apis.key.isEmpty()) {
            AppData.apis.keys = AppData.apis.apiKeys("tmdb").filter { it.isNotEmpty() }.toMutableList()
            AppData.apis.ki = 0
            AppData.apis.pickKeys()
        }
        if (AppData.apis.key.isEmpty()) return
        val c = readCache()
        if (c == null) {
            try { load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            return
        }
        silentBusy = true
        try {
            val nn = ConcurrentHashMap<String, List<TrItem>>()
            coroutineScope {
                ORDER.map { k ->
                    async {
                        try { nn[k] = fetchSection(k) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                    }
                }.awaitAll()
            }
            nn["x"]?.takeIf { it.isNotEmpty() }?.let { try { nn["x"] = blend(it) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
            val ch = ArrayList<String>()
            val dd = HashMap<String, List<TrItem>?>()
            for (k in ORDER) {
                val n = nn[k]
                val o = c.d[k] ?: emptyList()
                if (n == null || n.isEmpty()) { dd[k] = c.d[k]; continue }
                val L = if (k == "m") 10 else 20
                if (same(n.take(L), o.take(L))) { dd[k] = c.d[k]; continue }
                val old = HashMap<Long, String>()
                o.forEach { if (!it.x.isNullOrEmpty()) old[it.id] = it.x!! }
                n.forEach { if (it.x.isNullOrEmpty() && old[it.id] != null) it.x = old[it.id] }
                dd[k] = n
                ch.add(k)
            }
            if (ch.isEmpty()) return
            // new posters are loaded first (at most 6 s) so the row does not flash
            val urls = ch.flatMap { k -> (dd[k] ?: emptyList()).take(if (k == "m") 10 else 20).mapNotNull { Tmdb.imgUrl(it.p, "w185") } }
            withTimeoutOrNull(6000L) {
                coroutineScope { urls.map { u -> async { try { appCtx.imageLoader.execute(ImageRequest.Builder(appCtx).data(u).build()) } catch (e: Exception) { } } }.awaitAll() }
            }
            for (k in ch) dd[k]?.let { lists[k] = it }
            writeCache(dd)
            enrich(ch.map { dd[it] })
        } finally {
            silentBusy = false
        }
    }

    // ---- IMDb ratings on the cards -------------------------------------------------------------------------------------------

    private val trx = ConcurrentHashMap<String, TrxEntry>().also { m ->
        try {
            val raw = Ls.getItem(TRX)
            if (raw != null) {
                val o = JSONObject(raw)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val e = o.optJSONObject(k) ?: continue
                    m[k] = TrxEntry(e.optString("x", ""), e.optLong("t"))
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun trxSave() {
        try {
            val o = JSONObject()
            val keep = now() - 30L * 86400000L
            for ((k, e) in trx) if (e.t > keep) o.put(k, JSONObject().put("x", e.x).put("t", e.t))
            Ls.setItem(TRX, o.toString())
        } catch (e: Exception) {
        }
    }

    /** a rating stays 3 days, "no rating" 1 hour */
    private fun fresh(e: TrxEntry?): Boolean = e != null && now() - e.t < (if (e.x.isNotEmpty()) 2592e5 else 36e5)

    private val sem = Semaphore(3)
    private val busy = ConcurrentHashMap<Long, Deferred<String?>>()

    /** omRating(id): "" = none (or no OMDb key), null = failed */
    private suspend fun omRating(id: String): String? {
        if (!omKeys()) return ""
        return try {
            val j = Net.omdb.byId(id)
            if (Omdb.limitHit(j)) null else (Omdb.parseO(j)?.imdb ?: "")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchX(o: TrItem): String? {
        val v: String? = try {
            val ex = Net.tmdb.get(Tmdb.path(o.id) + "/external_ids")
            val id = ex.opt("imdb_id")
            if (!Js.truthy(id)) "" else {
                val a = Net.xmdb.lookup(id.toString())
                val ai = (a as? XmRes.Ok)?.v?.imdb
                if (!ai.isNullOrEmpty()) ai else {
                    val b = omRating(id.toString())
                    if (!b.isNullOrEmpty()) b else if (a is XmRes.Failed || b == null) null else ""
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (v != null) {
            trx[o.id.toString()] = TrxEntry(v, now())
            trxSave()
        }
        return v
    }

    /** trxGet(o): the IMDb rating of a title (cached; at most 3 requests at the same time) */
    private suspend fun xGet(o: TrItem): String? {
        val e = trx[o.id.toString()]
        if (fresh(e)) return e!!.x
        val d = busy.computeIfAbsent(o.id) {
            AppScope.async {
                try {
                    sem.withPermit { fetchX(o) }
                } finally {
                    busy.remove(o.id)
                }
            }
        }
        return d.await()
    }

    /** trEnrich(lists, retry): fill in IMDb ratings; titles still without one are tried again (15 s, 30 s, 30 s, 30 s later) */
    fun enrich(ls: List<List<TrItem>?>, retry: Int = 0) {
        if (!hasSrc()) return
        val by = LinkedHashMap<Long, MutableList<TrItem>>()
        ls.forEach { l -> l?.take(20)?.forEach { o -> by.getOrPut(o.id) { ArrayList() }.add(o) } }
        for ((k, os) in by) {
            val hit = os.firstOrNull { !it.x.isNullOrEmpty() }
            val ks = k.toString()
            if (hit != null) trx[ks] = TrxEntry(hit.x!!, now())
            val e = trx[ks]
            val x: String? = if (hit != null) hit.x else if (fresh(e)) e!!.x else null
            if (x != null) {
                if (x.isNotEmpty()) os.forEach { it.x = x }
            } else {
                AppScope.launch {
                    com.trackerxx.movieverse.util.Quiet.await()
                    val v = try { xGet(os[0]) } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { null }
                    if (!v.isNullOrEmpty()) os.forEach { it.x = v }
                }
            }
        }
        trxSave()
        if (retry < 4) {
            AppScope.launch {
                delay(if (retry > 0) 30000L else 15000L)
                val f = ArrayList<TrItem>()
                for ((k, os) in by) {
                    if (os.none { !it.x.isNullOrEmpty() } && !fresh(trx[k.toString()])) f.add(os[0])
                }
                if (f.isNotEmpty() && visible) enrich(listOf(f), retry + 1)
            }
        }
    }
}
