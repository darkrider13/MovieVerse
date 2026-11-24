package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

// ======================================================================================================================
// cast & crew
// ======================================================================================================================

/** one card of the cast carousel (.cp). female = avatar picture when there is no photo (pimg's g === 1) */
class CastPerson(val id: Long, val name: String, val photo: String?, val female: Boolean, val sub: String)

/** crew = Director / Screenplay / Story people, cast = first 40 actors */
class CastData(val crew: List<CastPerson>, val cast: List<CastPerson>)

/** one video of the trailer popup */
class TrailerVideo(val key: String, val name: String, val publishedAt: String, val lang: String)

/** trailerInfo(m): t = best Trailer key, s = best Teaser key, list = what the popup shows */
class TrailerInfo(val t: String, val s: String, val list: List<TrailerVideo>, val isTrailer: Boolean)

// ======================================================================================================================
// person dialog
// ======================================================================================================================

/** one credit (mk(x, r) of personOpen) */
class PCredit(
    val id: Long,
    val t: String,
    val d: String,
    val r: String,
    val poster: String?,
    val tv: Boolean,
    val v: Double,
    val n: Int,
    val g: List<Int>
) {
    var act: Boolean = false
    var w: Double = 0.0
}

class PersonData(
    val name: String,
    val photo: String?,
    val female: Boolean,
    val info: String,
    val knownCredits: Int,
    val bio: String,
    val upcoming: List<PCredit>,
    val latest: List<PCredit>,
    val top: List<PCredit>,
    val all: List<PCredit>,
    val wikidataId: String
)

/** the Awards page could not be loaded: message = text of the error line (shown with a Retry button) */
class AwardsFail(message: String) : Exception(message)

/** one row of the Awards page */
class AwRow(val name: String, val category: String, val work: String, val badge: Int)   // badge: 0 none, 1 Won, 2 Nominee

object Credits {

    // ---- cast ------------------------------------------------------------------------------------------------------

    private val castMem = ConcurrentHashMap<Long, JSONObject>()

    /** tmdb(tp(id)+'/credits'), kept in memory like castMem */
    suspend fun credits(id: Long): JSONObject {
        castMem[id]?.let { return it }
        val c = Net.tmdb.get(Tmdb.path(id) + "/credits")
        castMem[id] = c
        return c
    }

    private val CREW_ORDER = listOf("Director", "Screenplay", "Story")

    private fun arr(o: JSONObject, k: String): List<JSONObject> {
        val a = o.optJSONArray(k) ?: return emptyList()
        val out = ArrayList<JSONObject>(a.length())
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    private fun str(o: JSONObject, k: String): String? {
        if (!o.has(k) || o.isNull(k)) return null
        val v = o.get(k)
        return if (v is String) v else v.toString()
    }

    private fun lng(o: JSONObject, k: String): Long = if (o.has(k) && !o.isNull(k)) o.optLong(k) else 0L

    /** the crew / cast lists of castLoad */
    fun buildCast(c: JSONObject): CastData {
        class Cr(val id: Long, val name: String, val p: String?, val g: Int) { val js = ArrayList<String>() }

        val cr = ArrayList<Cr>()
        val seen = HashMap<Long, Cr>()
        for (x in arr(c, "crew")) {
            val job = str(x, "job") ?: ""
            val j = if (job == "Director") "Director"
            else if (job == "Screenplay" || job == "Writer") "Screenplay"
            else if (job == "Story" || job == "Novel") "Story"
            else ""
            if (j.isEmpty()) continue
            val id = lng(x, "id")
            var o = seen[id]
            if (o == null) {
                o = Cr(id, str(x, "name") ?: "", str(x, "profile_path")?.takeIf { it.isNotEmpty() }, x.optInt("gender", 0))
                seen[id] = o
                cr.add(o)
            }
            if (!o.js.contains(j)) o.js.add(j)
        }
        val sorted = cr.sortedBy { o -> o.js.minOf { CREW_ORDER.indexOf(it) } }
        val crew = sorted.map { o ->
            CastPerson(
                o.id, o.name, o.p, o.g == 1,
                CREW_ORDER.filter { o.js.contains(it) }.joinToString(" · ")
            )
        }
        val cast = arr(c, "cast").take(40).map { x ->
            CastPerson(
                lng(x, "id"), str(x, "name") ?: "", str(x, "profile_path")?.takeIf { it.isNotEmpty() },
                x.optInt("gender", 0) == 1, str(x, "character") ?: ""
            )
        }
        return CastData(crew, cast)
    }

    // ---- trailers ----------------------------------------------------------------------------------------------------

    private val tcache = ConcurrentHashMap<Long, Deferred<TrailerInfo>>()

    /** trailerInfo(m): cached; a failed answer is dropped at once, an answer without a trailer after 5 minutes */
    suspend fun trailerInfo(id: Long): TrailerInfo {
        tcache[id]?.let { return it.await() }
        val d = AppScope.async { loadTrailer(id) }
        val prev = tcache.putIfAbsent(id, d)
        if (prev != null) {
            d.cancel()
            return prev.await()
        }
        AppScope.launch {
            try {
                val i = d.await()
                if (i.t.isEmpty()) {
                    delay(300000L)
                    tcache.remove(id, d)
                }
            } catch (e: CancellationException) {
                tcache.remove(id, d)
            } catch (e: Exception) {
                tcache.remove(id, d)
            }
        }
        return d.await()
    }

    private class V(val key: String, val name: String?, val type: String, val iso: String?, val official: Boolean, val pub: String?)

    private val NOT_REAL_TRAILER = Regex("teaser|tv spot|clip|featurette|behind the scenes", RegexOption.IGNORE_CASE)
    private val NOT_MAIN = Regex("teaser|tv spot|clip|featurette|international|red band|final|\\bnew\\b|trailer\\s*#?\\s*([2-9]|ii)\\b")
    private val MAIN_NAME = Regex("^(official\\s+)?(main\\s+)?trailer(\\s*[\\[(].*[\\])])?$")

    private suspend fun loadTrailer(id: Long): TrailerInfo {
        val d = Net.tmdb.get(Tmdb.path(id))
        var lg = Js.or(d.opt("original_language"), "en")?.toString() ?: "en"
        if (lg == "cn") lg = "zh"
        val v = Net.tmdb.get(
            Tmdb.path(id) + "/videos",
            mapOf("include_video_language" to ((if (lg == "en") "en" else "$lg,en") + ",null"))
        )
        val all = ArrayList<V>()
        for (x in arr(v, "results")) {
            if (str(x, "site") != "YouTube") continue
            val iso = str(x, "iso_639_1")?.takeIf { it.isNotEmpty() }
            all.add(
                V(
                    str(x, "key") ?: "", str(x, "name"), str(x, "type") ?: "", iso,
                    Js.truthy(x.opt("official")), str(x, "published_at")
                )
            )
        }
        val lang = lg
        fun pick(type: String): String {
            val l = all.filter { it.type == type }
            fun sc(x: V) = (if (x.iso == lang) 4 else if (x.iso == null) 2 else 0) + (if (x.official) 1 else 0)
            val s = l.sortedByDescending { sc(it) }
            return s.firstOrNull()?.key ?: ""
        }

        fun mk(type: String): List<V> {
            var l = all.filter { it.type == type }
            val of = l.filter { it.official }
            if (of.isNotEmpty()) l = of
            if (type == "Trailer") {
                val nt = l.filter { !NOT_REAL_TRAILER.containsMatchIn(it.name ?: "") }
                if (nt.isNotEmpty()) l = nt
            }
            fun sc(x: V) = if (x.iso == lang) 2 else if (x.iso == null) 1 else 0
            fun dt(x: V) = x.pub ?: ""
            fun ms(x: V): Int {
                val n = (x.name ?: "").lowercase().trim()
                if (NOT_MAIN.containsMatchIn(n)) return 0
                if (MAIN_NAME.matches(n)) return 3
                if (n.contains("official trailer")) return 2
                return if (n.contains("trailer")) 1 else 0
            }
            if (l.isEmpty()) return l
            val best = l.sortedWith(Comparator { a, b ->
                val r1 = ms(b) - ms(a)
                if (r1 != 0) r1 else {
                    val r2 = sc(b) - sc(a)
                    if (r2 != 0) r2 else dt(a).compareTo(dt(b))
                }
            }).first()
            val rest = l.filter { it !== best }.sortedWith(Comparator { a, b ->
                val r1 = dt(a).compareTo(dt(b))
                if (r1 != 0) r1 else ms(b) - ms(a)
            })
            return listOf(best) + rest
        }

        val tl = mk("Trailer")
        val list = if (tl.isNotEmpty()) tl else mk("Teaser")
        return TrailerInfo(
            pick("Trailer"), pick("Teaser"),
            list.map { TrailerVideo(it.key, it.name ?: "", it.pub ?: "", it.iso ?: "") },
            tl.isNotEmpty()
        )
    }

    // ---- person ---------------------------------------------------------------------------------------------------------

    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** fmt(d): "30 Jul 1970" ("TBA" when empty) */
    fun fmt(d: String?): String {
        if (d.isNullOrEmpty()) return "TBA"
        val a = d.split("-")
        val mi = (a.getOrNull(1)?.toIntOrNull() ?: 0) - 1
        return (a.getOrNull(2) ?: "undefined") + " " + (MONTHS.getOrNull(mi) ?: "undefined") + " " + a[0]
    }

    /** ageOf(b, e): " (Age 56)" or "" */
    fun ageOf(b: String?, e: String?): String {
        val a = (b ?: "").split("-").map { it.toIntOrNull() ?: 0 }
        if (a.size < 3 || a[0] == 0 || a[1] == 0 || a[2] == 0) return ""
        val n = Calendar.getInstance()
        val r = (e ?: "").split("-").map { it.toIntOrNull() ?: 0 }
        if (r.size == 3 && r[0] != 0) {
            n.clear()
            n.set(r[0], r[1] - 1, r[2])
        }
        var y = n.get(Calendar.YEAR) - a[0]
        val mo = n.get(Calendar.MONTH) + 1
        val day = n.get(Calendar.DAY_OF_MONTH)
        if (mo < a[1] || (mo == a[1] && day < a[2])) y--
        return if (y in 0..129) " (Age $y)" else ""
    }

    private val DM = mapOf(
        "Directing" to "Director", "Writing" to "Writer", "Production" to "Producer", "Camera" to "Cinematographer",
        "Editing" to "Editor", "Visual Effects" to "VFX Artist", "Costume & Make-Up" to "Costume & Make-Up Artist",
        "Creator" to "Creator", "Lighting" to "Lighting Technician"
    )
    private val CREW_JOBS = listOf("Director", "Writer", "Screenplay", "Story", "Producer")
    private val SELF = Regex("\\bSelf\\b", RegexOption.IGNORE_CASE)
    private val ACT = Regex("^(as |Actor)")

    suspend fun person(pid: Long): PersonData {
        val p = Net.tmdb.get("/person/$pid", mapOf("append_to_response" to "combined_credits,external_ids"))
        val cc = p.optJSONObject("combined_credits") ?: JSONObject()
        val td = Fmt.todayL()

        fun mk(x: JSONObject, r: String): PCredit {
            val gs = ArrayList<Int>()
            x.optJSONArray("genre_ids")?.let { a -> for (i in 0 until a.length()) gs.add(a.optInt(i)) }
            return PCredit(
                lng(x, "id"),
                Js.or(x.opt("title"), x.opt("name"))?.takeIf { Js.truthy(it) }?.toString() ?: "",
                Js.or(x.opt("release_date"), x.opt("first_air_date"), "")?.toString() ?: "",
                r, str(x, "poster_path")?.takeIf { it.isNotEmpty() }, str(x, "media_type") == "tv",
                Js.parseFloat(x.opt("vote_average")).let { if (it.isNaN()) 0.0 else it },
                Js.parseFloat(x.opt("vote_count")).let { if (it.isNaN()) 0 else it.toInt() },
                gs
            )
        }

        val castL = arr(cc, "cast")
        val crewL = arr(cc, "crew")
        val raw = ArrayList<PCredit>()
        for (x in castL) {
            val ch = str(x, "character") ?: ""
            raw.add(mk(x, if (ch.isNotEmpty()) "as $ch" else "Actor"))
        }
        for (x in crewL) {
            val job = str(x, "job") ?: ""
            if (CREW_JOBS.contains(job)) raw.add(mk(x, job))
        }
        val seen = HashSet<String>()
        val uniq = ArrayList<PCredit>()
        for (x in raw) {
            val k = x.id.toString() + "|" + x.r
            if (x.t.isNotEmpty() && x.d.isNotEmpty() && seen.add(k)) uniq.add(x)
        }
        // all.sort(function(a,b){return a.d<b.d?1:-1}) : newest first, equal dates end up in reverse order
        val all = uniq.withIndex()
            .sortedWith(compareByDescending<IndexedValue<PCredit>> { it.value.d }.thenByDescending { it.index })
            .map { it.value }

        val up = all.filter { it.d > td }.reversed()
        val dn = all.filter { it.d <= td }.take(60)

        // Top Rated: TMDB rating weighted by the number of votes
        val by = HashMap<Long, PCredit>()
        for (x in all) {
            if (!(x.d <= td && !x.g.contains(10767) && !x.g.contains(10763) && !SELF.containsMatchIn(x.r))) continue
            val o = by[x.id]
            val act = ACT.containsMatchIn(x.r)
            if (o == null || (act && !o.act)) {
                x.act = act
                by[x.id] = x
            }
        }
        val l = by.keys.sorted().map { by[it]!! }   // Object.keys() lists number keys in ascending order
        val m0 = 1500.0
        val c0 = 6.6
        var q = l.filter { it.n >= 200 }
        if (q.size < 5) q = l.filter { it.n >= 20 }
        if (q.size < 5) q = l
        for (x in q) x.w = (x.n.toDouble() / (x.n + m0)) * x.v + (m0 / (x.n + m0)) * c0
        val top = q.sortedByDescending { it.w }.take(30)

        val ks = HashSet<String>()
        for (x in castL + crewL) ks.add((str(x, "media_type") ?: "") + lng(x, "id"))
        val kc = ks.size

        val kfd = str(p, "known_for_department") ?: ""
        val gender = p.optInt("gender", 0)
        val dep = if (kfd == "Acting") (if (gender == 1) "Actress" else "Actor") else (DM[kfd] ?: kfd)
        val bd = str(p, "birthday") ?: ""
        val info = listOf(
            dep,
            if (bd.isNotEmpty()) "Born " + fmt(bd) + ageOf(bd, str(p, "deathday")) else "",
            str(p, "place_of_birth") ?: ""
        ).filter { it.isNotEmpty() }.joinToString(" · ")

        val bio0 = str(p, "biography") ?: ""
        val bio = if (bio0.length > 420) bio0.substring(0, 420) + "…" else bio0
        val wd = p.optJSONObject("external_ids")?.let { str(it, "wikidata_id") } ?: ""

        return PersonData(
            str(p, "name") ?: "", str(p, "profile_path")?.takeIf { it.isNotEmpty() }, (if (gender == 0) 2 else gender) == 1,
            info, kc, bio, up, dn, top, all, wd
        )
    }

    // ---- awards -------------------------------------------------------------------------------------------------------------

    private const val WDA = "https://www.wikidata.org/w/api.php"

    /** awChar(all, w, y): the character the person played in that work (closest year) */
    private fun awChar(all: List<PCredit>, w: String, y: Int): String {
        if (w.isEmpty()) return ""
        val nw = Wiki.norm(w)
        val c = all.filter { Wiki.norm(it.t) == nw && it.r.startsWith("as ") }
            .sortedBy { Math.abs((it.d.take(4).toIntOrNull() ?: 0) - y) }
            .firstOrNull()
        return c?.r?.substring(3) ?: ""
    }

    private class Aw(val n: String, val c: String, val w: String, val y: Int, val won: Boolean)

    private val CITE = Regex("\\[[^\\]]*\\]")

    private fun cellText(e: Element): String = Js.WS.replace(CITE.replace(e.wholeText(), ""), " ").trim()

    /** wkTbl(tb): a Wikipedia table as a grid, rowspan and colspan filled in (missing cells stay null like holes in a JS array) */
    private fun wkTbl(tb: Element): List<List<String?>> {
        val head = ArrayList<Element>()
        val body = ArrayList<Element>()
        val foot = ArrayList<Element>()
        for (ch in tb.children()) {
            when (ch.tagName()) {
                "thead" -> head.addAll(ch.children().filter { it.tagName() == "tr" })
                "tfoot" -> foot.addAll(ch.children().filter { it.tagName() == "tr" })
                "tbody" -> body.addAll(ch.children().filter { it.tagName() == "tr" })
                "tr" -> body.add(ch)
            }
        }
        val rows = head + body + foot
        val g = ArrayList<ArrayList<String?>>()
        fun row(i: Int): ArrayList<String?> {
            while (g.size <= i) g.add(ArrayList())
            return g[i]
        }

        fun put(r: Int, c: Int, t: String) {
            val rw = row(r)
            while (rw.size <= c) rw.add(null)
            rw[c] = t
        }

        for ((ri, tr) in rows.withIndex()) {
            row(ri)
            var ci = 0
            for (c in tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                while (ci < g[ri].size && g[ri][ci] != null) ci++
                val rs = (c.attr("rowspan").trim().toIntOrNull() ?: 1).coerceIn(1, 200)
                val cs = (c.attr("colspan").trim().toIntOrNull() ?: 1).coerceIn(1, 200)
                val t = cellText(c)
                for (a in 0 until rs) for (b in 0 until cs) put(ri + a, ci + b, t)
                ci += cs
            }
        }
        return g
    }

    private suspend fun wkAwPage(t: String): JSONObject? {
        val p = Wiki.api(
            listOf(
                "action" to "parse", "page" to t, "prop" to "text", "redirects" to "1",
                "disableeditsection" to "1", "disablelimitreport" to "1"
            ), null, 30000, true
        )
        return if (p.optJSONObject("parse") != null) p else null
    }

    private val LIST_TITLE = Regex("^list of .*(awards|accolades)", RegexOption.IGNORE_CASE)
    private val RES_RE = Regex("^(result|outcome)", RegexOption.IGNORE_CASE)
    private val YEAR_RE = Regex("^year", RegexOption.IGNORE_CASE)
    private val CAT_RE = Regex("category", RegexOption.IGNORE_CASE)
    private val AWD_RE = Regex("^(award|association|organi[sz]ation|ceremony|festival)", RegexOption.IGNORE_CASE)
    private val WORK_RE = Regex("work|film|movie|title|project", RegexOption.IGNORE_CASE)
    private val WON_RE = Regex("^(won|win)", RegexOption.IGNORE_CASE)
    private val NOM_RE = Regex("^nominat", RegexOption.IGNORE_CASE)
    private val NOWORK_RE = Regex("^[—–\\-\\s]*(n/?a)?$", RegexOption.IGNORE_CASE)
    private val YR4 = Regex("\\d{4}")

    /** wkAwList(name): the usual page names first, then a search; null when there is no list page */
    private suspend fun wkAwList(name: String): List<Aw>? {
        val key = Wiki.norm(name)
        val titles = listOf(
            "List of roles and awards of $name",
            "List of awards and nominations received by $name",
            "List of awards and nominations of $name"
        )
        // all page names and the search start at the same time (before: one after the other); the first usual name that exists wins,
        // the search result is only used when none of them does. Same result as before, a lot sooner.
        var p: JSONObject? = null
        supervisorScope {
            val tl = titles.map { t -> async { wkAwPage(t) } }
            val sd = async {
                val j = Wiki.api(
                    listOf("action" to "query", "list" to "search", "srsearch" to "$name awards list", "srlimit" to "8", "srnamespace" to "0"),
                    null, 9000, true
                )
                val hits = j.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
                var title: String? = null
                for (i in 0 until hits.length()) {
                    val ti = hits.optJSONObject(i)?.optString("title") ?: continue
                    if (LIST_TITLE.containsMatchIn(ti) && Wiki.norm(ti).contains(key)) {
                        title = ti
                        break
                    }
                }
                if (title != null) wkAwPage(title) else null
            }
            try {
                for (d in tl) {
                    p = d.await()
                    if (p != null) break
                }
                if (p == null) p = sd.await()
            } finally {
                tl.forEach { it.cancel() }
                sd.cancel()
            }
            Unit
        }
        val pg = p ?: return null
        val html = pg.optJSONObject("parse")?.let { str(it, "text") } ?: ""
        val doc = Jsoup.parse(html)
        val out = ArrayList<Aw>()
        var head = ""
        for (n0 in doc.select("h2, h3, h4, table.wikitable")) {
            if (n0.tagName() != "table") {
                head = CITE.replace(n0.wholeText(), "").trim()
                continue
            }
            val g = wkTbl(n0)
            var hi = -1
            for ((i, r) in g.withIndex()) {
                if (hi < 0 && r.any { c -> c != null && RES_RE.containsMatchIn(c) }) hi = i
            }
            if (hi < 0) continue
            val hr = g[hi]
            fun ix(re: Regex): Int {
                for (k in hr.indices) if (hr[k] != null && re.containsMatchIn(hr[k]!!)) return k
                return -1
            }

            val yi = ix(YEAR_RE)
            var ci = ix(CAT_RE)
            val ai = ix(AWD_RE)
            val wi = ix(WORK_RE)
            val ri = ix(RES_RE)
            val named = ai >= 0 && ci >= 0 && ai != ci
            if (ci < 0) ci = ai
            var cur = head
            for (r in g.drop(hi + 1)) {
                // a row where every cell says the same thing is a ceremony label inside the table
                val r0 = r.getOrNull(0)
                if (r.size > 1 && r0 != null && r0.isNotEmpty() && r.all { it == null || (it.isNotEmpty() && it == r0) }) {
                    cur = r0
                    continue
                }
                val res = r.getOrNull(ri)
                val won = res != null && res.isNotEmpty() && WON_RE.containsMatchIn(res)
                val nom = res != null && res.isNotEmpty() && NOM_RE.containsMatchIn(res)
                if (!won && !nom) continue
                val cat = if (ci >= 0) (r.getOrNull(ci) ?: "") else ""
                var wk = if (wi >= 0) (r.getOrNull(wi) ?: "") else ""
                val yr = if (yi >= 0) (r.getOrNull(yi)?.let { YR4.find(it)?.value } ?: "") else ""
                if (NOWORK_RE.matches(wk)) wk = ""
                out.add(Aw(if (named && r.getOrNull(ai) != null) r[ai]!! else cur, cat, wk, yr.toIntOrNull() ?: 0, won))
            }
        }
        if (out.isEmpty()) return null
        return out.sortedWith(Comparator { a, b ->
            val r1 = b.y - a.y
            if (r1 != 0) r1 else (if (b.won) 1 else 0) - (if (a.won) 1 else 0)
        })
    }

    /** the Awards page: Wikipedia's list first, Wikidata as fallback. Throws AwardsFail with the text to show (and a Retry button). */
    suspend fun awards(wikidata: String, all: List<PCredit>, name: String): List<AwRow> {
        if (name.isEmpty()) {
            return try { awardsWD(wikidata, all) } catch (e: CancellationException) { throw e } catch (e: Exception) { throw AwardsFail("Could not load awards.") }
        }
        val l: List<Aw>? = try {
            try { wkAwList(name) } catch (e: CancellationException) { throw e } catch (e: Exception) { wkAwList(name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AwardsFail("Could not load awards from Wikipedia. Check your connection and try again.")
        }
        if (l == null) {
            return try { awardsWD(wikidata, all) } catch (e: CancellationException) { throw e } catch (e: Exception) { throw AwardsFail("Could not load awards.") }
        }
        return l.map { x ->
            AwRow(
                x.n.ifEmpty { "Award" },
                if (x.c.isNotEmpty() && Wiki.norm(x.c) != Wiki.norm(x.n)) x.c else "",
                awWork(x.w, awChar(all, x.w, x.y)),
                if (x.won) 1 else 2
            )
        }
    }

    private fun awWork(w: String, ch: String): String = if (w.isEmpty()) "" else w + (if (ch.isNotEmpty()) " · as $ch" else "")

    private val FOR_RE = Regex("^(.+?)\\s+for\\s+(.+)$", RegexOption.IGNORE_CASE)

    /** awardsWD(q, all): empty list = "No award data found."; throws when Wikidata fails */
    private suspend fun awardsWD(q: String, all: List<PCredit>): List<AwRow> {
        if (q.isEmpty()) return emptyList()
        val w = Wiki.api(listOf("action" to "wbgetclaims", "entity" to q, "property" to "P166"), WDA, 9000, true)
        val claims = w.optJSONObject("claims")?.optJSONArray("P166") ?: JSONArray()
        class It(val a: String, val y: String, val w: String)

        val items = ArrayList<It>()
        for (i in 0 until claims.length()) {
            val x = claims.optJSONObject(i) ?: continue
            val v = x.optJSONObject("mainsnak")?.optJSONObject("datavalue")
            val id = v?.optJSONObject("value")?.let { str(it, "id") }
            if (v == null || id.isNullOrEmpty()) continue
            val qu = x.optJSONObject("qualifiers")
            val tm = qu?.optJSONArray("P585")?.optJSONObject(0)?.optJSONObject("datavalue")
            val wk = qu?.optJSONArray("P1686")?.optJSONObject(0)?.optJSONObject("datavalue")
            val y = if (tm != null) (tm.optJSONObject("value")?.let { str(it, "time") } ?: "undefined").let { if (it.length > 1) it.substring(1, minOf(5, it.length)) else "" } else ""
            val wid = wk?.optJSONObject("value")?.let { str(it, "id") } ?: ""
            items.add(It(id, y, wid))
        }
        if (items.isEmpty()) return emptyList()
        val sorted = items.withIndex()
            .sortedWith(compareByDescending<IndexedValue<It>> { it.value.y }.thenByDescending { it.index })
            .map { it.value }.take(24)
        val ids = ArrayList<String>()
        for (x in sorted) for (i in listOf(x.a, x.w)) if (i.isNotEmpty() && !ids.contains(i)) ids.add(i)
        val r = Wiki.api(
            listOf("action" to "wbgetentities", "ids" to ids.joinToString("|"), "props" to "labels", "languages" to "en"), WDA, 9000, true
        )
        val ents = r.optJSONObject("entities")

        fun lab(i: String): String = ents?.optJSONObject(i)?.optJSONObject("labels")?.optJSONObject("en")?.let { str(it, "value") } ?: ""

        return sorted.map { x ->
            var n = lab(x.a).ifEmpty { "Award" }
            var rr = ""
            val m = FOR_RE.find(n)
            if (m != null) {
                n = m.groupValues[1]
                rr = m.groupValues[2]
            }
            val wl = if (x.w.isNotEmpty() && lab(x.w).isNotEmpty()) lab(x.w) else ""
            var ch = ""
            if (wl.isNotEmpty()) {
                val nw = Wiki.norm(wl)
                val yy = x.y.toIntOrNull() ?: 0
                val c = all.filter { Wiki.norm(it.t) == nw && it.r.startsWith("as ") }
                    .sortedBy { Math.abs((it.d.take(4).toIntOrNull() ?: 0) - yy) }.firstOrNull()
                if (c != null) ch = c.r.substring(3)
            }
            AwRow(n, rr, awWork(wl, ch), 0)
        }
    }
}
