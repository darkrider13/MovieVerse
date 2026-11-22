package com.trackerxx.movieverse.releases

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.AppScope
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.net.QItem
import com.trackerxx.movieverse.ui.library.LibraryLogic
import com.trackerxx.movieverse.util.Countries
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** one card of the Releases page. TV shows have a NEGATIVE id. */
class UItem(
    val id: Long,
    val title: String,
    val release: String,
    val poster: String?,
    val overview: String,
    val popularity: Double,
    val tv: Boolean,
    val lang: String = "",
    val origin: List<String> = emptyList()
) {
    var kind: String? = null      // _k : Cinema / OTT / Cinema + OTT / TV
    var o: Movie? = null          // _o : release details (OTT date, certificate ...)
    var ex = false                // _ex : found by the extra "On OTT" search
    var idx = 0                   // _i

    fun toQItem() = QItem(id, title, release, poster, overview, popularity, tv)
}

/** what the list area shows instead of cards */
sealed class UMsg {
    object None : UMsg()
    object Loading : UMsg()
    class Empty(val text: String) : UMsg()
    /** error text + Retry */
    class Retry(val text: String) : UMsg()
}

/**
 * Releases page: Released Today / In Cinemas (coming soon in cinemas) / On OTT, with Type, Country, Genre, Year filters,
 * search, and "load more" while scrolling. Ported from loadUp / loadToday / cinLoad / ctryLoad / usearch.
 */
@Stable
object Releases {
    // ---- state ----------------------------------------------------------------------------------------------------------
    var umode by mutableStateOf("today")        // today | soon (In Cinemas) | cin (On OTT)
    var utype by mutableStateOf("movie")        // movie | tv  (On OTT only)
    var ucs by mutableStateOf<List<String>>(emptyList())   // countries
    var ugs by mutableStateOf<List<String>>(emptyList())   // genres
    var uy by mutableStateOf("")                // year (In Cinemas only)

    var items by mutableStateOf<List<UItem>>(emptyList())
    var msg by mutableStateOf<UMsg>(UMsg.None)
    var moreVisible by mutableStateOf(false)
    var moreText by mutableStateOf("Loading more…")
    var uhasMore by mutableStateOf(false)
    var uload by mutableStateOf(false)
    var uerr by mutableStateOf(false)

    /** the search text that is applied ("" = no search) and its results */
    var usq by mutableStateOf("")
    var sres by mutableStateOf<List<UItem>>(emptyList())

    private var upage = 1
    private val ugen = AtomicInteger(0)
    private val usgen = AtomicInteger(0)
    var udate: String = ""
    var ustamp = 0L

    /** this page is on screen */
    @Volatile
    var visible = false

    val CY: Int = Calendar.getInstance().get(Calendar.YEAR)
    val YEARS: List<Pair<String, String>> = (0..4).map { (CY + it).toString() to (CY + it).toString() }
    val GENRES = listOf(
        "28" to "Action", "12" to "Adventure", "16" to "Animation", "35" to "Comedy", "80" to "Crime", "99" to "Documentary",
        "18" to "Drama", "10751" to "Family", "14" to "Fantasy", "36" to "History", "27" to "Horror", "10402" to "Music",
        "9648" to "Mystery", "10749" to "Romance", "878" to "Science Fiction", "53" to "Thriller", "10752" to "War", "37" to "Western"
    )
    val UTYPES = listOf("movie" to "Movie", "tv" to "TV Series")
    private val CSHORT = mapOf("US" to "USA", "GB" to "UK", "KR" to "KR", "AE" to "UAE", "IN-BN" to "IN (BN)")

    private const val UTTL = 30 * 60 * 1000L

    private fun now() = System.currentTimeMillis()
    private fun today() = Fmt.todayL()

    /** dayAdd(n): the local date n days from today */
    fun dayAdd(n: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, n)
        fun z(x: Int) = if (x < 10) "0$x" else "$x"
        return c.get(Calendar.YEAR).toString() + "-" + z(c.get(Calendar.MONTH) + 1) + "-" + z(c.get(Calendar.DAY_OF_MONTH))
    }

    // ---- countries / genres helpers -----------------------------------------------------------------------------------------
    fun cCode(code: String): String = CSHORT[code] ?: code
    fun cLabel(code: String): String {
        val n = Countries.LIST.firstOrNull { it.first == code }?.second ?: code
        return if (n.length > 5) (CSHORT[code] ?: code) else n
    }

    private fun cMatch(cc: String?, lang: String?): Boolean = ucs.any { c ->
        if (c == "IN-BN") cc == "IN" && lang == "bn"
        else if (c == "IN") cc == "IN" && lang != "bn"
        else cc == c
    }

    private fun apiC(): String {
        val o = ArrayList<String>()
        for (c0 in ucs) {
            val c = if (c0 == "IN-BN") "IN" else c0
            if (!o.contains(c)) o.add(c)
        }
        return o.joinToString("|")
    }

    private fun tvGenres(g: List<String>): List<String> {
        val m = mapOf("28" to "10759", "12" to "10759", "14" to "10765", "878" to "10765", "10752" to "10768")
        val ok = setOf("16", "35", "80", "99", "18", "10751", "9648", "37", "10759", "10765", "10768")
        val o = ArrayList<String>()
        for (x in g) {
            val t = m[x] ?: x
            if (ok.contains(t) && !o.contains(t)) o.add(t)
        }
        return o
    }

    // ---- detail caches (30 minutes) ---------------------------------------------------------------------------------------
    private val dcache = ConcurrentHashMap<Long, Movie>()
    private val ccache = ConcurrentHashMap<Long, String?>()

    private fun dget(id: Long): Movie? {
        val o = dcache[id]
        return if (o != null && now() - (o.num("ottChk") ?: 0.0) < UTTL) o else null
    }

    /** release details of a movie: OTT date, platforms, certificate + its real country (cached) */
    private suspend fun details(id: Long): Movie? {
        dget(id)?.let { return it }
        return try {
            val d = Net.tmdb.get("/movie/$id", mapOf("append_to_response" to "release_dates,watch/providers"))
            val o = Enrich.applyRel(Movie(), d)
            val cc = Countries.effC(d.optJSONArray("origin_country"), d.optJSONArray("production_countries"))
            o.set("cc", cc)
            ccache[id] = cc
            dcache[id] = o
            o
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun ccOf(o: Movie?): String? = o?.str("cc")

    // ---- mapping TMDB results -----------------------------------------------------------------------------------------------
    private fun str(v: Any?): String = if (Js.truthy(v)) v.toString() else ""
    private fun poster(x: JSONObject): String? = if (Js.truthy(x.opt("poster_path"))) x.opt("poster_path").toString() else null
    private fun pop(x: JSONObject): Double = x.optDouble("popularity", 0.0).let { if (it.isNaN()) 0.0 else it }

    private fun movieItem(x: JSONObject, dflt: String = ""): UItem = UItem(
        x.optLong("id"), str(x.opt("title")), str(x.opt("release_date")).ifEmpty { dflt }, poster(x), str(x.opt("overview")),
        pop(x), false, str(x.opt("original_language"))
    )

    private fun tvItem(x: JSONObject, dflt: String = ""): UItem {
        val oc = ArrayList<String>()
        x.optJSONArray("origin_country")?.let { a -> for (i in 0 until a.length()) oc.add(a.opt(i).toString()) }
        return UItem(
            -x.optLong("id"), str(x.opt("name")), str(x.opt("first_air_date")).ifEmpty { dflt }, poster(x), str(x.opt("overview")),
            pop(x), true, str(x.opt("original_language")), oc
        ).also { it.kind = "TV" }
    }

    private val EMPTY_PAGE = JSONObject().put("results", org.json.JSONArray()).put("total_pages", 0)

    // ---- loading ----------------------------------------------------------------------------------------------------------------

    fun loadUp(reset: Boolean) {
        AppScope.launch { loadUpImpl(reset) }
    }

    private suspend fun loadUpImpl(reset: Boolean) {
        if (reset) {
            upage = 1
            items = emptyList()
            uhasMore = false
        }
        udate = today()
        ustamp = now()
        moreVisible = !reset
        moreText = "Loading more…"
        if (AppData.apis.key.isEmpty()) {
            msg = UMsg.Empty("Add your TMDB API key in Settings to see coming soon movies.")
            moreVisible = false
            uhasMore = false
            return
        }
        val gen = if (reset) ugen.incrementAndGet() else ugen.get()
        uload = true
        uerr = false
        if (reset) msg = UMsg.Loading
        val mode = umode
        if (mode == "today") {
            uhasMore = false
            try {
                loadToday(gen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != ugen.get()) return
                uerr = true
                msg = UMsg.Retry(e.message ?: "")
            }
            if (gen == ugen.get()) {
                uload = false
                moreVisible = false
            }
            return
        }
        val type = utype
        val cs = ucs
        val gs = ugs
        val year = uy
        val p = linkedMapOf("primary_release_date.gte" to dayAdd(1), "include_adult" to "false", "page" to upage.toString(), "sort_by" to "popularity.desc")
        if (mode == "cin") {
            p["primary_release_date.gte"] = dayAdd(-75)
            p["primary_release_date.lte"] = today()
            p["with_release_type"] = "2|3"
            p["sort_by"] = "popularity.desc"
        } else if (year.isNotEmpty()) {
            p["primary_release_date.lte"] = "$year-12-31"
            if ((year.toIntOrNull() ?: 0) > CY) p["primary_release_date.gte"] = "$year-01-01"
        }
        fun countryParams(m: MutableMap<String, String>) {
            if (cs.isNotEmpty()) {
                m["with_origin_country"] = apiC()
                if (cs.size == 1 && cs[0] == "IN-BN") m["with_original_language"] = "bn"
            }
        }
        countryParams(p)
        if (gs.isNotEmpty()) p["with_genres"] = gs.joinToString("|")
        val pgs = if (reset) listOf(1, 2, 3, 4) else listOf(upage)
        if (reset) upage = 4

        var tvp: LinkedHashMap<String, String>? = null
        if (mode == "cin" && type == "tv") {
            val tg = tvGenres(gs)
            if (gs.isEmpty() || tg.isNotEmpty()) {
                tvp = linkedMapOf("first_air_date.gte" to today(), "sort_by" to "popularity.desc", "include_null_first_air_dates" to "false")
                countryParams(tvp)
                if (tg.isNotEmpty()) tvp["with_genres"] = tg.joinToString("|")
            }
        }
        var xp: LinkedHashMap<String, String>? = null
        if (mode == "cin" && type != "tv") {
            xp = linkedMapOf(
                "include_adult" to "false", "sort_by" to "popularity.desc", "with_release_type" to "4",
                "release_date.gte" to dayAdd(1), "release_date.lte" to dayAdd(365)
            )
            countryParams(xp)
            if (gs.isNotEmpty()) xp["with_genres"] = gs.joinToString("|")
        }

        val tvpF = tvp
        val xpF = xp
        try {
            val triple = coroutineScope {
                val a = pgs.map { pg ->
                    async {
                        if (mode == "cin") EMPTY_PAGE
                        else Net.tmdb.get("/discover/movie", LinkedHashMap(p).also { it["page"] = pg.toString() })
                    }
                }
                val b = pgs.map { pg ->
                    async {
                        if (tvpF == null) null else try {
                            Net.tmdb.get("/discover/tv", LinkedHashMap(tvpF).also { it["page"] = pg.toString() })
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { EMPTY_PAGE }
                    }
                }
                val x = pgs.map { pg ->
                    async {
                        if (xpF == null) null else try {
                            Net.tmdb.get("/discover/movie", LinkedHashMap(xpF).also { it["page"] = pg.toString() })
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { EMPTY_PAGE }
                    }
                }
                Triple(a.awaitAll(), b.awaitAll().filterNotNull(), x.awaitAll().filterNotNull())
            }
            val rs = triple.first
            val trs = triple.second
            val exs = triple.third

            var tvItems = ArrayList<UItem>()
            for (r in trs) {
                val a = r.optJSONArray("results") ?: continue
                for (i in 0 until a.length()) {
                    val x = a.optJSONObject(i) ?: continue
                    if (poster(x) != null && tvItems.none { it.id == -x.optLong("id") }) tvItems.add(tvItem(x))
                }
            }
            if (cs.isNotEmpty()) tvItems = ArrayList(tvItems.filter { x -> x.origin.any { c -> cMatch(c, x.lang) } })
            var totalPages = maxOf(rs[0].optInt("total_pages", 0), if (trs.isNotEmpty()) trs[0].optInt("total_pages", 0) else 0)
            val cur = items
            tvItems = ArrayList(tvItems.filter { x -> cur.none { it.id == x.id } })
            val seen = HashSet<Long>()
            cur.forEach { seen.add(it.id) }
            val results = ArrayList<UItem>()
            for (r in rs) {
                val a = r.optJSONArray("results") ?: continue
                for (i in 0 until a.length()) {
                    val x = a.optJSONObject(i) ?: continue
                    val id = x.optLong("id")
                    if (seen.add(id)) results.add(movieItem(x))
                }
            }
            for (r in exs) {
                val a = r.optJSONArray("results") ?: continue
                for (i in 0 until a.length()) {
                    val x = a.optJSONObject(i) ?: continue
                    val id = x.optLong("id")
                    if (seen.add(id)) results.add(movieItem(x).also { it.ex = true })
                }
            }
            if (exs.isNotEmpty()) totalPages = maxOf(totalPages, exs[0].optInt("total_pages", 0))
            if (gen != ugen.get()) return
            val got0 = results.filter { !it.poster.isNullOrEmpty() }
            uhasMore = upage < totalPages
            val got = if (cs.isNotEmpty() && mode != "cin") ctryLoad(got0) else got0
            if (gen != ugen.get()) return
            if (mode != "cin") {
                addUp(got)
            } else {
                val r0 = cinLoad(got, false)
                if (gen != ugen.get()) return
                val mv = r0.filter { !it.ex || (it.o != null && Enrich.ottState(it.o!!) == "soon") }
                addUp(mv + tvItems)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen != ugen.get()) return
            uerr = true
            if (reset || items.isEmpty()) {
                msg = UMsg.Retry(e.message ?: "")
            } else {
                upage--
                com.trackerxx.movieverse.ui.components.ToastBridge.show(e.message ?: "")
            }
        }
        if (gen == ugen.get()) {
            uload = false
            if (uerr && items.isNotEmpty() && uhasMore) {
                moreText = "Couldn’t load. Tap to retry"
                moreVisible = true
            } else moreVisible = false
        }
    }

    /** next page (scrolled near the end, or the Loading more / retry button) */
    fun loadMore() {
        if (uload) return
        uerr = false
        upage++
        loadUp(false)
    }

    /** checkMore(): called while the list is scrolled near its end */
    fun checkMore() {
        if (!uhasMore || usq.isNotEmpty() || uload || uerr || !visible || AppData.apis.key.isEmpty()) return
        loadMore()
    }

    /** Released Today: every movie (cinema or OTT release) and new TV series whose release date is exactly today */
    private suspend fun loadToday(gen: Int) {
        val td = today()
        val cs = ucs
        val gs = ugs
        suspend fun pages(ep: String, params: Map<String, String>): List<JSONObject> {
            val out = ArrayList<JSONObject>()
            var pg = 1
            while (true) {
                val r = Net.tmdb.get(ep, LinkedHashMap(params).also { it["page"] = pg.toString() })
                r.optJSONArray("results")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(it) } }
                if (gen != ugen.get()) return out
                if (pg < minOf(r.optInt("total_pages", 0), 10)) pg++ else break
            }
            return out
        }
        val base = linkedMapOf("include_adult" to "false", "sort_by" to "popularity.desc", "release_date.gte" to td, "release_date.lte" to td)
        if (cs.isNotEmpty()) {
            base["with_origin_country"] = apiC()
            if (cs.size == 1 && cs[0] == "IN-BN") base["with_original_language"] = "bn"
        }
        if (gs.isNotEmpty()) base["with_genres"] = gs.joinToString("|")
        val tg = tvGenres(gs)
        var tvParams: LinkedHashMap<String, String>? = null
        if (gs.isEmpty() || tg.isNotEmpty()) {
            tvParams = linkedMapOf(
                "include_adult" to "false", "sort_by" to "popularity.desc", "first_air_date.gte" to td,
                "first_air_date.lte" to td, "include_null_first_air_dates" to "false"
            )
            if (cs.isNotEmpty()) {
                tvParams["with_origin_country"] = apiC()
                if (cs.size == 1 && cs[0] == "IN-BN") tvParams["with_original_language"] = "bn"
            }
            if (tg.isNotEmpty()) tvParams["with_genres"] = tg.joinToString("|")
        }
        val tvParamsF = tvParams
        val a = coroutineScope {
            val c1 = async { pages("/discover/movie", LinkedHashMap(base).also { it["with_release_type"] = "2|3" }) }
            val c2 = async {
                try { pages("/discover/movie", LinkedHashMap(base).also { it["with_release_type"] = "4" }) }
                catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList<JSONObject>() }
            }
            val c3 = async {
                if (tvParamsF == null) emptyList<JSONObject>() else try { pages("/discover/tv", tvParamsF) }
                catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList<JSONObject>() }
            }
            Triple(c1.await(), c2.await(), c3.await())
        }
        if (gen != ugen.get()) return
        val map = HashMap<Long, UItem>()
        val list = ArrayList<UItem>()
        fun addM(arr: List<JSONObject>, kind: String) {
            for (x in arr) {
                if (poster(x) == null) continue
                val id = x.optLong("id")
                val e = map[id]
                if (e != null) {
                    if (e.kind != kind) e.kind = "Cinema + OTT"
                    continue
                }
                val n = movieItem(x, td)
                n.kind = kind
                map[id] = n
                list.add(n)
            }
        }
        addM(a.first, "Cinema")
        addM(a.second, "OTT")
        var tvs = ArrayList<UItem>()
        for (x in a.third) {
            if (poster(x) == null || tvs.any { it.id == -x.optLong("id") }) continue
            tvs.add(tvItem(x, td))
        }
        if (cs.isNotEmpty()) tvs = ArrayList(tvs.filter { x -> x.origin.any { c -> cMatch(c, x.lang) } })

        // keep only titles whose own dates (the same In cinemas / OTT release dates shown in the details) are exactly today
        val sem = Semaphore(16)
        val out = ConcurrentHashMap.newKeySet<UItem>()
        val kept = ArrayList<UItem>()
        coroutineScope {
            list.map { x ->
                async {
                    sem.withPermit {
                        if (gen != ugen.get()) return@withPermit
                        val o = details(x.id)
                        if (o != null) {
                            val c = o.rel == td
                            val t = o.str("ottDate") == td
                            if ((c || t) && (cs.isEmpty() || cMatch(ccOf(o), x.lang))) {
                                x.kind = if (c && t) "Cinema + OTT" else if (c) "Cinema" else "OTT"
                                x.o = o
                                out.add(x)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        kept.addAll(out)
        if (gen != ugen.get()) return
        addUp(kept + tvs)
    }

    /** ctryLoad(list): keeps the movies whose real country matches the chosen countries */
    private suspend fun ctryLoad(list: List<UItem>): List<UItem> {
        val r = coroutineScope {
            list.map { x ->
                async {
                    if (ccache.containsKey(x.id)) ccache[x.id]
                    else try {
                        val d = Net.tmdb.get("/movie/" + x.id)
                        val cc = Countries.effC(d.optJSONArray("origin_country"), d.optJSONArray("production_countries"))
                        ccache[x.id] = cc
                        cc
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                }
            }.awaitAll()
        }
        return list.filterIndexed { i, x -> !r[i].isNullOrEmpty() && cMatch(r[i], x.lang) }
    }

    /** cinLoad(list, noC): release details of each movie (16 at a time); movies already streaming are dropped */
    private suspend fun cinLoad(list: List<UItem>, noC: Boolean): List<UItem> {
        list.forEachIndexed { k, x -> x.idx = k }
        val sem = Semaphore(16)
        val out = ConcurrentHashMap.newKeySet<UItem>()
        coroutineScope {
            list.map { x ->
                async {
                    sem.withPermit {
                        val o = details(x.id)
                        x.o = o
                        val okC = noC || ucs.isEmpty() || (o != null && cMatch(ccOf(o), x.lang))
                        if (okC && (o == null || Enrich.ottState(o) != "live")) out.add(x)
                    }
                }
            }.awaitAll()
        }
        return out.sortedBy { it.idx }
    }

    /** sortBatch(b): each loaded batch is sorted and appended below what is shown, so nothing jumps while scrolling */
    private fun sortBatch(b: List<UItem>): List<UItem> {
        val mode = umode
        if (mode == "today") return b.sortedWith { a, c -> c.popularity.compareTo(a.popularity) }
        if (mode == "cin") {
            fun od(m: UItem): String {
                if (m.tv) return m.release
                val o = m.o
                return if (o != null && Enrich.ottState(o) == "soon" && !o.str("ottDate").isNullOrEmpty()) o.str("ottDate")!! else ""
            }
            return b.sortedWith { a, c ->
                val x = od(a)
                val y = od(c)
                if (x.isNotEmpty() && y.isNotEmpty()) {
                    if (x == y) c.popularity.compareTo(a.popularity) else if (x < y) -1 else 1
                } else if (x.isNotEmpty()) -1
                else if (y.isNotEmpty()) 1
                else {
                    val r = a.release
                    val q = c.release
                    if (r == q) c.popularity.compareTo(a.popularity) else if (r < q) 1 else -1
                }
            }
        }
        return b.sortedWith { a, c ->
            val x = a.release.ifEmpty { "9999" }
            val y = c.release.ifEmpty { "9999" }
            if (x == y) c.popularity.compareTo(a.popularity) else if (x < y) -1 else 1
        }
    }

    /** addUp(batch) + drawUp(): add a loaded batch to the list */
    private fun addUp(batch: List<UItem>) {
        val b = sortBatch(batch)
        items = items + b
        if (usq.isNotEmpty()) return
        msg = if (items.isEmpty()) UMsg.Empty(emptyText()) else UMsg.None
    }

    private fun emptyText(): String =
        if (umode == "today") "No releases found for today."
        else if (umode == "cin") (if (utype == "tv") "No upcoming TV series match these filters." else "No upcoming OTT movies match these filters.")
        else "No coming soon movies match these filters."

    /** upTag(m): the small tag on a card */
    fun upTag(m: UItem): String {
        if (umode == "today") return m.kind ?: "Today"
        if (m.tv) return "TV " + LibraryLogic.fmtDate(m.release)
        if (umode != "cin") return LibraryLogic.fmtDate(m.release)
        val o = m.o
        if (o == null || Enrich.ottState(o) == "tba") return "Coming Soon"
        return "OTT " + LibraryLogic.fmtDate(o.str("ottDate"))
    }

    /** showView('up'): load when the list is empty, from another day or older than 30 minutes */
    fun onShow() {
        if (items.isEmpty() || udate != today() || now() - ustamp > UTTL) loadUp(true)
    }

    fun setMode(m: String) {
        if (m == umode) return
        umode = m
        if (usq.isNotEmpty()) {
            search()
            return
        }
        loadUp(true)
    }

    fun resetFilters() {
        ucs = emptyList(); ugs = emptyList(); uy = ""; utype = "movie"
        loadUp(true)
    }

    // ---- search ------------------------------------------------------------------------------------------------------------------

    private var searchJob: kotlinx.coroutines.Job? = null

    /** typing in the Releases search box: results appear 450 ms after the last key */
    fun onQuery(v: String) {
        val t = v.trim()
        searchJob?.cancel()
        if (t.isEmpty()) {
            if (usq.isNotEmpty()) {
                usq = ""
                usgen.incrementAndGet()
                sres = emptyList()
                msg = if (items.isEmpty()) UMsg.Empty(emptyText()) else UMsg.None
            }
            return
        }
        usq = t
        searchJob = AppScope.launch {
            delay(450)
            search()
        }
    }

    private fun search() {
        AppScope.launch { searchImpl() }
    }

    private suspend fun searchImpl() {
        val t = usq
        val g = usgen.incrementAndGet()
        if (AppData.apis.key.isEmpty()) {
            msg = UMsg.Empty("Add your TMDB API key in Settings to search.")
            return
        }
        msg = UMsg.Empty("Searching…")
        val td = today()
        val mode = umode
        val type = utype
        suspend fun sp(ep: String, tv: Boolean): Any {
            return try {
                val d = Net.tmdb.get(ep, linkedMapOf("query" to t, "include_adult" to "false"))
                val a = d.optJSONArray("results")
                val out = ArrayList<UItem>()
                if (a != null) for (i in 0 until a.length()) {
                    val x = a.optJSONObject(i) ?: continue
                    if (poster(x) == null) continue
                    out.add(if (tv) tvItem(x) else movieItem(x))
                }
                out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
        }
        try {
            val a = coroutineScope {
                val m = async { sp("/search/movie", false) }
                val v = async { sp("/search/tv", true) }
                Pair(m.await(), v.await())
            }
            if (g != usgen.get()) return
            val err: Exception? = (a.first as? Exception) ?: (a.second as? Exception)
            @Suppress("UNCHECKED_CAST")
            var mv: List<UItem> = (a.first as? List<UItem>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            var tv: List<UItem> = (a.second as? List<UItem>) ?: emptyList()
            mv = mv.filter { x ->
                val r = x.release
                if (mode == "today") r == td else if (mode == "cin") r.isNotEmpty() else (r.isNotEmpty() && r > td)
            }
            tv = tv.filter { x -> if (mode == "today") x.release == td else (x.release.isNotEmpty() && x.release > td) }
            /* Theaters = movies only; OTT follows the Type filter */
            if (mode == "soon" || (mode == "cin" && type != "tv")) tv = emptyList()
            if (mode == "cin" && type == "tv") mv = emptyList()
            if (mv.isEmpty() && tv.isEmpty() && err != null) {
                msg = UMsg.Retry(err.message ?: "")
                return
            }
            val r0 = if (mode == "cin") cinLoad(mv, true) else mv
            if (g != usgen.get()) return
            val joined = ((if (mode == "cin") r0.filter { it.o != null && Enrich.ottState(it.o!!) == "soon" } else r0) + tv)
                .sortedWith { x, y -> y.popularity.compareTo(x.popularity) }
            sres = joined
            msg = if (joined.isEmpty())
                UMsg.Empty("Nothing found in " + (if (mode == "cin") "On OTT" else if (mode == "today") "Released Today" else "In Cinemas") + " for \"" + t + "\".")
            else UMsg.None
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (g == usgen.get()) msg = UMsg.Retry(e.message ?: "")
        }
    }

    /** Retry button of an error shown in the list */
    fun retry() {
        if (usq.isNotEmpty()) search() else loadUp(true)
    }
}
