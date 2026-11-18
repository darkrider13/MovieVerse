package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.concurrent.ConcurrentHashMap

/** one amount from an infobox text: currency code + value */
class WkAmt(val c: String, val v: Double)

/** budget and box office in the same currency */
class WkPair(val b: Double, val r: Double, val c: String)

/** a verified Wikipedia page of the film: page title, infobox budget text, box office text, Wikidata item id */
class WkCand(val page: String, val bud: String, val gross: String, val q: String)

private class WkChk(val c: WkCand?, val ok: Boolean, val e: Boolean)

/**
 * Wikipedia box office: budget + box office from the film's infobox (editor-maintained, keeps the real currency).
 * The page must have the exact film title, list the release year, and (when its Wikidata item lists an IMDb or TMDB id)
 * match this movie's id. Same checks and same parallel lookup as the web version (wkFind / wkCheck / wkVerify).
 */
object Wiki {
    private const val WP = "https://en.wikipedia.org/w/api.php"
    private const val WD = "https://www.wikidata.org/w/api.php"

    private val mem = ConcurrentHashMap<String, Deferred<WkCand?>>()
    private val vm = ConcurrentHashMap<String, Deferred<Boolean>>()

    /** wkApi(q, base, ms): API call, throws "wiki <status>" on an HTTP error */
    suspend fun api(q: List<Pair<String, String>>, base: String? = null, ms: Int = 9000): JSONObject {
        val url = (base ?: WP) + "?" + q.joinToString("&") { it.first + "=" + Js.encodeURIComponent(it.second) } +
            "&format=json&formatversion=2&origin=*"
        val r = Http.get(url, mapOf("Accept" to "application/json"), ms, ms)
        if (!r.ok) throw NetException("wiki " + r.status)
        return Http.parseObject(r.body)
    }

    private val NOT_ALNUM = Regex("[^a-z0-9]+")

    /** wkNorm(t) */
    fun norm(t: String?): String = (t ?: "").lowercase().replace("&", "and").replace(NOT_ALNUM, "")

    private val MAIN_SPLIT = Regex("\\s*[:–—]\\s*|\\s+-\\s+")

    /** wkMain(t): the title before ":" / dash (the main title of a film with a subtitle) */
    fun main(t: String?): String = MAIN_SPLIT.split(t ?: "")[0]

    private val TRAILING_PAREN = Regex("\\s*\\([^)]*\\)\\s*$")

    /** wkSame(pt, t): same title ignoring a trailing "(2022 film)"; a main-title-only match is accepted too */
    fun same(pt: String?, t: String?): Boolean {
        val p = (pt ?: "").replace(TRAILING_PAREN, "")
        val a = norm(p)
        val b = norm(t)
        if (a == b) return true
        val am = norm(main(p))
        val bm = norm(main(t))
        return am.length >= 3 && bm.length >= 3 && ((a == bm && bm != b) || (am == b && am != a))
    }

    private fun textOf(n: Node, sb: StringBuilder) {
        if (n is TextNode) sb.append(n.wholeText) else for (c in n.childNodes()) textOf(c, sb)
    }

    private fun textContent(n: Node): String = StringBuilder().also { textOf(n, it) }.toString()

    /** wkInfobox(html): label -> text of the first "infobox" table (null when there is none) */
    fun infobox(html: String): Map<String, String>? {
        val doc = Jsoup.parse(html)
        val tb = doc.selectFirst("table.infobox") ?: return null
        tb.select("sup, .reference, style").remove()
        val o = LinkedHashMap<String, String>()
        for (tr in tb.select("tr")) {
            val th = tr.selectFirst("th") ?: continue
            val td = tr.selectFirst("td") ?: continue
            val k = Js.WS.replace(textContent(th), " ").trim().lowercase()
            if (!o.containsKey(k)) {
                o[k] = Js.WS.replace(textContent(td).replace('\u00A0', ' '), " ").trim()
            }
        }
        return o
    }

    /** wkQid(pr): Wikidata item id from the page properties */
    private fun qid(pr: Any?): String {
        if (!Js.truthy(pr)) return ""
        if (pr is JSONArray) {
            for (i in 0 until pr.length()) {
                val e = pr.optJSONObject(i) ?: continue
                if (e.opt("name") == "wikibase_item") {
                    val v = Js.or(e.opt("value"), e.opt("*"), "")
                    return v?.toString() ?: ""
                }
            }
            return ""
        }
        if (pr is JSONObject) {
            val v = pr.opt("wikibase_item")
            return if (Js.truthy(v)) v.toString() else ""
        }
        return ""
    }

    private val YEAR_RE = Regex("\\b(?:19|20)\\d\\d\\b")

    private fun first(ib: Map<String, String>, vararg keys: String): String {
        for (k in keys) {
            val v = ib[k]
            if (!v.isNullOrEmpty()) return v
        }
        return ""
    }

    /** wkCheck(page, t, y): reads one page; null when it is missing or is not this film (title + release year) */
    private suspend fun checkPage(page: String, t: String, y: Long): WkCand? {
        val p = api(
            listOf(
                "action" to "parse", "page" to page, "prop" to "text|properties", "section" to "0",
                "redirects" to "1", "disableeditsection" to "1", "disablelimitreport" to "1"
            )
        )
        val parse = p.optJSONObject("parse") ?: return null
        val title = if (Js.truthy(parse.opt("title"))) parse.opt("title").toString() else ""
        if (!same(title, t)) return null
        val ib = infobox(if (Js.truthy(parse.opt("text"))) parse.opt("text").toString() else "") ?: return null
        val rd = first(ib, "release date", "release dates", "released")
        val ys = YEAR_RE.findAll(rd).map { it.value.toLong() }.toList()
        if (!ys.contains(y)) return null
        return WkCand(title, ib["budget"] ?: "", ib["box office"] ?: "", qid(parse.opt("properties")))
    }

    /**
     * wkVerify(c, m): Wikidata cross-check. When the page's Wikidata item lists an IMDb id or a TMDB id, one of them must be
     * this movie's. No ids listed = nothing to compare, the title + year check stands.
     */
    private suspend fun verify(c: WkCand, tmdbId: Long, imdbId: String): Boolean {
        val k = c.page + "|" + tmdbId
        val d = vm.computeIfAbsent(k) { Wiki.asyncIo { verifyImpl(c, tmdbId, imdbId) } }
        d.invokeOnCompletion { if (it != null) vm.remove(k, d) }
        return d.await()
    }

    private suspend fun verifyImpl(c: WkCand, tmdbId: Long, imdbId: String): Boolean {
        val q: String = if (c.q.isNotEmpty()) c.q else {
            val j = api(
                listOf(
                    "action" to "query", "prop" to "pageprops", "titles" to c.page,
                    "ppprop" to "wikibase_item", "redirects" to "1"
                )
            )
            val pg = j.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            val wi = pg?.optJSONObject("pageprops")?.opt("wikibase_item")
            if (Js.truthy(wi)) wi.toString() else ""
        }
        if (q.isEmpty()) return true

        suspend fun claims(pp: String): List<String> {
            val w = api(listOf("action" to "wbgetclaims", "entity" to q, "property" to pp), WD)
            val arr = w.optJSONObject("claims")?.optJSONArray(pp) ?: return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val dv = x.optJSONObject("mainsnak")?.optJSONObject("datavalue") ?: continue
                val v = dv.opt("value")
                val s = if (v == null) "undefined" else v.toString()
                if (s.isNotEmpty()) out.add(s)
            }
            return out
        }

        val (a0, a1) = coroutineScope {
            val x = async { if (imdbId.isNotEmpty()) claims("P345") else emptyList() }
            val y = async { if (tmdbId > 0) claims("P4947") else emptyList() }
            Pair(x.await(), y.await())
        }
        var ex = false
        var ok = false
        if (imdbId.isNotEmpty() && a0.isNotEmpty()) {
            ex = true
            if (a0.contains(imdbId)) ok = true
        }
        if (tmdbId > 0 && a1.isNotEmpty()) {
            ex = true
            if (a1.contains(tmdbId.toString())) ok = true
        }
        return if (ex) ok else true
    }

    private fun <T> asyncIo(block: suspend () -> T): Deferred<T> = AppScope.async(Dispatchers.IO) { block() }

    /** wkFind(m) */
    suspend fun find(m: Movie): WkCand? {
        val yn = m.year.trim().toDoubleOrNull() ?: Double.NaN
        if (m.title.isEmpty() || yn.isNaN() || yn == 0.0) return null
        return find(m.title, yn.toLong(), m.tmdbId, m.str("imdbId") ?: "")
    }

    /**
     * Finds the film's Wikipedia page. The usual page names and the search candidates are checked at the same time
     * (title + year, then Wikidata); the first page that passes everything wins. Returns null when there is no such page,
     * throws when Wikipedia could not be reached (so the movie is tried again later).
     */
    suspend fun find(title: String, year: Long, tmdbId: Long, imdbId: String): WkCand? {
        if (title.isEmpty() || year == 0L) return null
        val k = norm(title) + year + "." + tmdbId
        val d = mem.computeIfAbsent(k) { asyncIo { findImpl(title, year, tmdbId, imdbId) } }
        d.invokeOnCompletion { if (it != null) mem.remove(k, d) }
        return d.await()
    }

    private suspend fun chk(page: String, t: String, y: Long, tmdbId: Long, imdbId: String): WkChk = try {
        val c = checkPage(page, t, y)
        if (c != null) WkChk(c, verify(c, tmdbId, imdbId), false) else WkChk(null, false, false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WkChk(null, false, true)
    }

    private suspend fun search(q: String, t: String, y: Long, tmdbId: Long, imdbId: String): List<WkChk> {
        val j = api(
            listOf(
                "action" to "query", "list" to "search", "srsearch" to q, "srlimit" to "5", "srnamespace" to "0"
            )
        )
        val arr = j.optJSONObject("query")?.optJSONArray("search")
        val hits = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                val ti = if (Js.truthy(h.opt("title"))) h.opt("title").toString() else ""
                if (same(ti, t)) hits.add(ti)
            }
        }
        return hits.take(3).map { h -> asyncIo { chk(h, t, y, tmdbId, imdbId) } }.awaitAll()
    }

    private suspend fun findImpl(t: String, y: Long, tmdbId: Long, imdbId: String): WkCand? {
        val mt = main(t)
        val loose = norm(mt) != norm(t) && mt.length >= 3
        val names = arrayListOf("$t ($y film)", t, "$t (film)")
        if (loose) {
            names.add("$mt ($y film)")
            names.add(mt)
        }
        val gs = names.map { n -> asyncIo { chk(n, t, y, tmdbId, imdbId) } }
        val ss = ArrayList<Deferred<List<WkChk>>>()
        ss.add(asyncIo { search("$t $y film", t, y, tmdbId, imdbId) })
        if (loose) ss.add(asyncIo { search("$mt $y film", t, y, tmdbId, imdbId) })

        val a = gs.awaitAll()
        for (x in a) {
            val c = x.c
            if (c != null && x.ok) return c
        }
        val b = ss.map {
            try {
                it.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listOf(WkChk(null, false, true))
            }
        }
        val all = a + b.flatten()
        for (x in all) {
            val c = x.c
            if (c != null && x.ok) return c
        }
        if (all.any { it.e }) throw NetException("wiki")
        return null
    }

    // ---- amounts ("₹3.5–5 crore", "US$12 million", "৳15 crore") ----------------------------------------------

    /** wkCur(sy): currency code of a symbol */
    fun cur(sy: String): String {
        val t = sy.lowercase()
        if (t == "₹" || Regex("^rs|^inr").containsMatchIn(t)) return "INR"
        if (t == "৳" || Regex("^(bdt|tk)").containsMatchIn(t)) return "BDT"
        if (t == "£") return "GBP"
        if (t == "€") return "EUR"
        if (t == "a$") return "AUD"
        if (t == "ca$") return "CAD"
        return "USD"
    }

    private val D = "\\" + '$'
    private val AMT_RE = Regex(
        "(CA" + D + "|A" + D + "|US" + D + "|" + D + "|₹|Rs\\.?|INR|৳|BDT|Tk\\.?|£|€)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)" +
            "(?:\\s*[–—-]\\s*(?:CA" + D + "|A" + D + "|US" + D + "|" + D + "|₹|Rs\\.?|৳|£|€)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?))?" +
            "\\s*(crore|lakh|million|billion|thousand|cr\\b|bn\\b)?",
        RegexOption.IGNORE_CASE
    )
    private val MU = mapOf("crore" to 1e7, "cr" to 1e7, "lakh" to 1e5, "million" to 1e6, "billion" to 1e9, "bn" to 1e9, "thousand" to 1e3)

    /** wkAmts(txt): every amount in the text; a range becomes its midpoint */
    fun amts(txt: String?): List<WkAmt> {
        val out = ArrayList<WkAmt>()
        val s = txt ?: ""
        for (r in AMT_RE.findAll(s)) {
            val g = r.groupValues
            val a = Js.parseFloat(g[2].replace(",", ""))
            val b = if (g[3].isNotEmpty()) Js.parseFloat(g[3].replace(",", "")) else a
            val mu = MU[g[4].lowercase()] ?: 1.0
            val v = (a + b) / 2 * mu
            if (v > 0) out.add(WkAmt(cur(g[1]), v))
        }
        return out
    }

    /** wkPair(bt, gt): budget and box office must be in the same currency (first matching one wins), otherwise nothing */
    fun pair(bt: String?, gt: String?): WkPair? {
        val bl = amts(bt)
        val gl = amts(gt)
        for (b in bl) for (g in gl) if (b.c == g.c) return WkPair(b.v, g.v, b.c)
        return null
    }
}
