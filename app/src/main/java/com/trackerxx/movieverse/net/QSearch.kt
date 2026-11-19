package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** one search result: TV shows have a NEGATIVE id */
class QItem(
    val id: Long,
    val title: String,
    val releaseDate: String,
    val poster: String?,
    val overview: String,
    val popularity: Double,
    val tv: Boolean,
    var s: Double = 0.0,   // _s : how well the title matches
    var k: Double = 0.0    // _k : final rank
) {
    fun copyRanked(s: Double, k: Double) = QItem(id, title, releaseDate, poster, overview, popularity, tv, s, k)
}

/**
 * Smart title search (qFind): finds the film even with typos, missing letters, only the first word, a year, or a subtitle.
 * It asks TMDB several ways at the same time and ranks everything by how close the title is.
 */
class QSearch(private val tmdb: Tmdb) {

    private val cache = ConcurrentHashMap<String, List<QItem>>()
    private val poolMutex = Mutex()
    @Volatile
    private var poolCache: List<QItem>? = null

    // ---- title helpers ------------------------------------------------------------------------------------------------

    private val DIACRITICS = Regex("[\u0300-\u036f]")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    /** qn(s): lower case, no accents, only letters and digits */
    fun qn(s: String?): String =
        Normalizer.normalize((s ?: "").lowercase(), Normalizer.Form.NFKD).replace(DIACRITICS, "").replace(NON_ALNUM, "")

    /** qLev(a, b): edit distance where swapping two neighbouring letters counts as one change */
    fun qLev(a: String, b: String): Int {
        var p = IntArray(b.length + 1) { it }
        var pp: IntArray? = null
        for (i in 1..a.length) {
            val c = IntArray(b.length + 1)
            c[0] = i
            for (j in 1..b.length) {
                var v = minOf(p[j] + 1, c[j - 1] + 1, p[j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1))
                if (i > 1 && j > 1 && pp != null && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, pp[j - 2] + 1)
                c[j] = v
            }
            pp = p
            p = c
        }
        return p[b.length]
    }

    /** qSim(a, b): 0..1, how alike two titles are */
    fun qSim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        return 1.0 - qLev(a, b).toDouble() / maxOf(a.length, b.length)
    }

    // ---- mapping ------------------------------------------------------------------------------------------------------

    private fun str(v: Any?): String = if (Js.truthy(v)) v.toString() else ""

    private fun mapRaw(x: JSONObject): QItem? {
        val mt = x.opt("media_type")
        return if (mt == "tv") {
            QItem(
                -x.optLong("id"), str(x.opt("name")), str(x.opt("first_air_date")),
                if (Js.truthy(x.opt("poster_path"))) x.opt("poster_path").toString() else null,
                str(x.opt("overview")), Js.clean(x.opt("popularity")).let { if (Js.truthy(it)) (it as? Number)?.toDouble() ?: 0.0 else 0.0 }, true
            )
        } else if (mt == "movie") {
            QItem(
                x.optLong("id"), str(x.opt("title")), str(x.opt("release_date")),
                if (Js.truthy(x.opt("poster_path"))) x.opt("poster_path").toString() else null,
                str(x.opt("overview")), Js.clean(x.opt("popularity")).let { if (Js.truthy(it)) (it as? Number)?.toDouble() ?: 0.0 else 0.0 }, false
            )
        } else null
    }

    /** qMap(x): only results that have a poster and a release year */
    private fun map(x: JSONObject): QItem? {
        val r = mapRaw(x) ?: return null
        return if (!r.poster.isNullOrEmpty() && r.releaseDate.take(4).isNotEmpty()) r else null
    }

    private fun mapAll(j: JSONObject, forceType: String? = null, ep: String? = null): List<QItem> {
        val a = j.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<QItem>()
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            val o = JSONObject(x.toString())
            if (forceType != null) o.put("media_type", forceType)
            else if (!Js.truthy(o.opt("media_type")) && ep != null) o.put("media_type", if (ep.startsWith("/tv/")) "tv" else "movie")
            map(o)?.let { out.add(it) }
        }
        return out
    }

    /** qPool(): today's trending / popular / top rated lists, used to find a title when search alone does not (cached) */
    suspend fun pool(): List<QItem> {
        poolCache?.let { return it }
        return poolMutex.withLock {
            poolCache ?: run {
                val eps = listOf(
                    "/trending/movie/week", "/trending/tv/week", "/movie/popular", "/tv/popular", "/movie/top_rated",
                    "/tv/top_rated", "/movie/now_playing", "/movie/upcoming"
                )
                val lists = coroutineScope {
                    eps.map { ep ->
                        async {
                            try {
                                mapAll(tmdb.get(ep), null, ep)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList<QItem>()
                            }
                        }
                    }.awaitAll()
                }
                val seen = HashSet<Long>()
                val out = ArrayList<QItem>()
                for (l in lists) for (x in l) if (seen.add(x.id)) out.add(x)
                if (out.isNotEmpty()) poolCache = out
                out
            }
        }
    }

    /** qRank(list, nt, yr): keep what is close enough to the typed text, best first */
    fun rank(list: List<QItem>, nt: String, yr: String): List<QItem> {
        val seen = HashSet<Long>()
        val o = ArrayList<QItem>()
        val cy = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        for (x in list) {
            if (x.title.isEmpty() || !seen.add(x.id)) continue
            val ti = qn(x.title)
            if (ti.isEmpty()) continue
            var s = maxOf(qSim(nt, ti), 0.92 * qSim(nt, ti.take(nt.length)))
            val ix = ti.indexOf(nt)
            if (ix == 0) s = maxOf(s, 0.95)
            else if (ix > 0) s = maxOf(s, if (nt.length >= 3) 0.8 else 0.5)
            val min = if (nt.length <= 2) 0.9 else if (nt.length == 3) 0.7 else 0.62
            if (s < min) continue
            val y = x.releaseDate.take(4).toDoubleOrNull() ?: 0.0
            var sc = s + minOf(0.12, Math.log10(1 + x.popularity) / 25) + (if (y != 0.0 && cy - y <= 1) 0.05 else 0.0)
            if (yr.isNotEmpty()) sc += if (y == (yr.toDoubleOrNull() ?: Double.NaN)) 0.15 else -0.05
            o.add(x.copyRanked(s, sc))
        }
        return o.sortedByDescending { it.k }
    }

    private val YEAR_RE = Regex("\\b((?:19|20)\\d{2})\\b")
    private val BRACKETS = Regex("[()\\[\\]\\-–]")

    /**
     * qFind(t): the ranked results for the typed text (up to 16). Throws the last network error when nothing was found
     * and a request failed (so the screen can say "Could not search").
     */
    suspend fun find(t: String): List<QItem> {
        val ym = YEAR_RE.find(t)
        var yr = ""
        var tx = t
        if (ym != null) {
            val rest = t.replace(ym.value, " ").replace(BRACKETS, " ").replace(Js.WS, " ").trim()
            if (qn(rest).length >= 2) {
                yr = ym.groupValues[1]
                tx = rest
            }
        }
        val nt = qn(tx)
        val ck = tx.lowercase() + "|" + yr
        if (nt.isEmpty()) return emptyList()
        cache[ck]?.let { return it }
        val err = AtomicReference<Exception?>(null)

        suspend fun run(q: String): List<QItem> = try {
            mapAll(tmdb.get("/search/multi", linkedMapOf("query" to q, "include_adult" to "false")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err.set(e)
            emptyList()
        }

        suspend fun yx(ep: String, k: String, mt: String): List<QItem> = try {
            mapAll(tmdb.get(ep, linkedMapOf("query" to tx, "include_adult" to "false", k to yr)), mt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        fun onlyYr(r: List<QItem>): List<QItem> {
            if (yr.isNotEmpty()) {
                val f = r.filter { it.releaseDate.take(4) == yr }
                if (f.isNotEmpty()) return f
            }
            return r
        }

        fun fin(r: List<QItem>): List<QItem> {
            val x = onlyYr(r).take(16)
            cache[ck] = x
            return x
        }

        val parts = coroutineScope {
            val a0 = async { run(tx) }
            val a1 = async { pool() }
            val a2 = async { if (yr.isNotEmpty()) yx("/search/movie", "year", "movie") else emptyList() }
            val a3 = async { if (yr.isNotEmpty()) yx("/search/tv", "first_air_date_year", "tv") else emptyList() }
            listOf(a0.await(), a1.await(), a2.await(), a3.await())
        }
        val all = parts[0] + parts[2] + parts[3] + parts[1].filter { qn(it.title).startsWith(nt) }
        val rk = rank(all, nt, yr)
        if (rk.isEmpty() && err.get() != null) throw err.get()!!

        // little or nothing found: try letters left out, swapped letters and shorter starts of the text
        if (nt.length >= 4 && (rk.isEmpty() || rk[0].s < 0.85)) {
            val vs = ArrayList<String>()
            val sv = HashSet<String>()
            val L = tx.length
            for (i in 0 until L) {
                if (tx[i] == ' ') continue
                val v = (tx.substring(0, i) + tx.substring(i + 1)).trim()
                if (v.length >= 2 && sv.add(v)) vs.add(v)
            }
            val stp = Math.ceil(vs.size / 12.0).toInt()
            val thinned = if (stp > 0) vs.filterIndexed { k, _ -> k % stp == 0 }.toMutableList() else vs
            for (i in 0 until L - 1) {
                if (tx[i] == ' ' || tx[i + 1] == ' ' || tx[i] == tx[i + 1]) continue
                val sw = tx.substring(0, i) + tx[i + 1] + tx[i] + tx.substring(i + 2)
                if (sv.add(sw)) thinned.add(sw)
            }
            for (n in listOf(maxOf(3, Math.ceil(L * 0.6).toInt()), 4, 5)) {
                val pf = tx.take(n).trim()
                if (pf.length >= 3 && sv.add(pf)) thinned.add(pf)
            }
            val variants = thinned.take(30)
            val b = coroutineScope { variants.map { v -> async { run(v) } }.awaitAll() }
            return fin(rank(all + b.flatten(), nt, yr))
        }
        return fin(rk)
    }
}
