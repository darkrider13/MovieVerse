package com.trackerxx.movieverse.enrich

import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.net.MdResult
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.net.StateDispatcher
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.net.Wiki
import com.trackerxx.movieverse.net.XmRes
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** hotMode(m): how often a not-yet-streaming movie is refreshed */
enum class HotMode { None, All, Rating }

/** OTT (streaming) release date + country + the first few platform names */
class OttInfo(val date: String?, val country: String?, val names: List<String>)

/**
 * Enrichment: everything the app looks up about a movie after it is added (details, OTT date, platforms, certificate,
 * IMDb / OMDb / XMDb / MDBList ratings, Wikipedia box office, poster). Ported function by function from the web version:
 * needsX(m) says whether a lookup is due, enrichX(m, force) does it and returns true when the movie changed.
 *
 * All functions run on StateDispatcher (one thread that owns movie changes, like the single JS thread); only the network
 * waits run in parallel.
 */
object Enrich {

    private const val DAY = 864e5
    private const val WEEK = 6048e5

    private val key: String get() = AppData.apis.key
    private val omdb: String get() = AppData.apis.omdb
    private fun xmKeys(): Boolean = AppData.apis.apiKeys("xmdb").any { it.isNotEmpty() }
    private fun mdKeys(): Boolean = AppData.apis.apiKeys("mdblist").any { it.isNotEmpty() }
    private fun now(): Double = System.currentTimeMillis().toDouble()
    private fun today(): String = Fmt.todayL()
    private fun nowMs(): Long = System.currentTimeMillis()

    /** movies whose MDBList lookup failed recently: not retried for 10 minutes */
    val mdBad = ConcurrentHashMap<String, Long>()

    /** called after a background refresh changed movies (the UI redraws) */
    @Volatile
    var onChanged: (() -> Unit)? = null

    /** snapshot of the visible fields of a movie (the UI sets it) to find which fields a refresh changed */
    @Volatile
    var snapF: ((Movie) -> Map<String, Any?>)? = null

    /** movie id -> fields changed by a refresh (they glow in the detail view) */
    val chgF = ConcurrentHashMap<String, MutableMap<String, Boolean>>()
    val chgSet: MutableSet<String> = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * QUIET: Rating / Votes / Rotten Tomatoes update silently: no poster pulse, no glowing tile, no "updated" count.
     * Only the rating badge is rewritten in place. Every other change (cinema date, OTT, budget, collection, seasons, episodes) still flags the card.
     */
    val QUIET: Set<String> = setOf("Rating", "Votes", "Rotten Tomatoes")

    /**
     * noteChg(m, bf, noSet): compares the movie with its earlier snapshot bf, marks the changed fields that are not quiet
     * (chgF = glowing tiles) and, unless noSet, flags the card (chgSet = poster pulse). True when something non-quiet changed.
     */
    fun noteChg(m: Movie, bf: Map<String, Any?>, noSet: Boolean = false): Boolean {
        val sf = snapF ?: return false
        val af = sf(m)
        val cf = chgF[m.id] ?: ConcurrentHashMap<String, Boolean>()
        var nr = false
        for ((k, v) in af) {
            if (v.toString() != bf[k].toString() && !QUIET.contains(k)) {
                cf[k] = true
                nr = true
            }
        }
        if (nr) {
            chgF[m.id] = cf
            if (!noSet) chgSet.add(m.id)
        }
        return nr
    }

    // ---- small helpers ------------------------------------------------------------------------------------------

    /** +m.year || 0 */
    private fun yearNum(m: Movie): Double {
        val v = m.year.trim().toDoubleOrNull() ?: return 0.0
        return if (v.isNaN()) 0.0 else v
    }

    private fun thisYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /** unrel(m): release date is still in the future */
    fun unrel(m: Movie): Boolean {
        val r = m.rel
        return r.isNotEmpty() && r > today()
    }

    private fun str(v: Any?): String = if (Js.truthy(v)) v.toString() else ""

    private fun numOr0(v: Any?): Any = if (Js.truthy(v) && v != null) v else 0

    // ---- OTT info from a TMDB answer ------------------------------------------------------------------------------

    private fun originCountry(d: JSONObject): String? {
        val oc = d.optJSONArray("origin_country") ?: return null
        if (oc.length() == 0 || oc.isNull(0)) return null
        return oc.get(0).toString()
    }

    /** the "results" object of an appended TMDB part, e.g. d.release_dates.results */
    private fun resultsArr(d: JSONObject, name: String): JSONArray = d.optJSONObject(name)?.optJSONArray("results") ?: JSONArray()

    /** provider names: own country first, otherwise the first country that has a flatrate list; at most 4 */
    private fun tvProv(d: JSONObject): List<String> {
        val wp = d.optJSONObject("watch/providers")?.optJSONObject("results") ?: JSONObject()
        val oc = originCountry(d)
        var pk: JSONArray? = if (oc != null) wp.optJSONObject(oc)?.optJSONArray("flatrate") else null
        if (pk == null) {
            val it = wp.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (pk == null) {
                    val fl = wp.optJSONObject(k)?.optJSONArray("flatrate")
                    if (fl != null) pk = fl
                }
            }
        }
        val names = ArrayList<String>()
        if (pk != null) {
            for (i in 0 until pk.length()) {
                if (names.size < 4) {
                    val x = pk.optJSONObject(i)
                    names.add(if (x != null && !x.isNull("provider_name")) x.get("provider_name").toString() else "undefined")
                }
            }
        }
        return names
    }

    /** ottInfo(d): earliest digital (type 4) release date (own country preferred) + platforms */
    fun ottInfo(d: JSONObject): OttInfo {
        val rd = resultsArr(d, "release_dates")
        var best: Pair<String, String?>? = null
        var bo: Pair<String, String?>? = null
        val oc0 = originCountry(d)
        for (i in 0 until rd.length()) {
            val c = rd.optJSONObject(i) ?: continue
            val iso = if (c.isNull("iso_3166_1")) null else c.opt("iso_3166_1")?.toString()
            val rds = c.optJSONArray("release_dates") ?: continue
            for (n in 0 until rds.length()) {
                val r = rds.optJSONObject(n) ?: continue
                val ty = r.opt("type")
                if (ty is Number && ty.toDouble() == 4.0 && Js.truthy(r.opt("release_date"))) {
                    val t = r.opt("release_date").toString().take(10)
                    if (best == null || t < best.first) best = Pair(t, iso)
                    if (iso == oc0 && oc0 != null && (bo == null || t < bo.first)) bo = Pair(t, iso)
                }
            }
        }
        val pick = bo ?: best
        return OttInfo(pick?.first, pick?.second, tvProv(d))
    }

    /** certOf(d): movie age rating, US first, then the origin country */
    fun certOf(d: JSONObject): String {
        val rd = resultsArr(d, "release_dates")
        val oc = originCountry(d)
        fun g(cc: String?): String {
            if (cc == null) return ""
            var c: JSONObject? = null
            for (i in 0 until rd.length()) {
                val x = rd.optJSONObject(i) ?: continue
                if (x.opt("iso_3166_1") == cc) { c = x; break }
            }
            if (c == null) return ""
            val rds = c.optJSONArray("release_dates") ?: JSONArray()
            val a = ArrayList<JSONObject>()
            for (i in 0 until rds.length()) {
                val r = rds.optJSONObject(i) ?: continue
                if (Js.truthy(r.opt("certification")) && r.opt("certification").toString().trim().isNotEmpty()) a.add(r)
            }
            var t: JSONObject? = null
            for (r in a) {
                val ty = r.opt("type")
                if (ty is Number && ty.toDouble() == 3.0) { t = r; break }
            }
            if (t == null) t = a.firstOrNull()
            return if (t != null) t.opt("certification").toString().trim() else ""
        }
        val us = g("US")
        if (us.isNotEmpty()) return us
        return g(oc)
    }

    /** tvCert(d): TV age rating, US first, then the origin country */
    fun tvCert(d: JSONObject): String {
        val r = resultsArr(d, "content_ratings")
        val oc = originCountry(d)
        fun g(cc: String?): String {
            if (cc == null) return ""
            for (i in 0 until r.length()) {
                val x = r.optJSONObject(i) ?: continue
                if (x.opt("iso_3166_1") == cc && Js.truthy(x.opt("rating")) && x.opt("rating").toString().trim().isNotEmpty()) {
                    return x.opt("rating").toString().trim()
                }
            }
            return ""
        }
        val us = g("US")
        if (us.isNotEmpty()) return us
        return g(oc)
    }

    /** tvRt(d): episode run time in minutes (0 when unknown) */
    fun tvRt(d: JSONObject): Any {
        val ert = d.optJSONArray("episode_run_time")
        val first: Any? = if (ert != null && ert.length() > 0 && !ert.isNull(0)) ert.get(0) else null
        val r = Js.or(
            first,
            d.optJSONObject("last_episode_to_air")?.opt("runtime"),
            d.optJSONObject("next_episode_to_air")?.opt("runtime"),
            0
        )
        return numOr0(r)
    }

    /** slOf(d): [[season, episodes], ...] without specials (season 0) */
    fun slOf(d: JSONObject): JSONArray {
        val out = JSONArray()
        val ss = d.optJSONArray("seasons") ?: return out
        for (i in 0 until ss.length()) {
            val x = ss.optJSONObject(i) ?: continue
            val sn = x.optDouble("season_number", Double.NaN)
            if (sn > 0) {
                out.put(JSONArray().put(x.opt("season_number")).put(numOr0(x.opt("episode_count"))))
            }
        }
        return out
    }

    /** applyRel(m, d): copies release / OTT / certificate / overview / runtime from the TMDB movie answer (dates mirror TMDB) */
    fun applyRel(m: Movie, d: JSONObject): Movie {
        val r = ottInfo(d)
        m.set("runtime", numOr0(d.opt("runtime")))
        m.set("rel", str(d.opt("release_date")))
        m.set("ottDate", r.date ?: "")
        m.set("ottC", if (r.date != null) (r.country ?: "") else "")
        m.set("ott", r.names)
        m.set("cert", certOf(d))
        m.set("ottChk", nowMs())
        m.set("ov", str(d.opt("overview")))
        // direct-to-OTT movie: has a digital release but never a cinema (type 2 / 3) release
        var hc = false
        var hd = false
        val rd = resultsArr(d, "release_dates")
        for (i in 0 until rd.length()) {
            val c = rd.optJSONObject(i) ?: continue
            val rds = c.optJSONArray("release_dates") ?: continue
            for (n in 0 until rds.length()) {
                val x = rds.optJSONObject(n) ?: continue
                val ty = x.opt("type")
                if (ty is Number && (ty.toDouble() == 2.0 || ty.toDouble() == 3.0)) hc = true
                else if (ty is Number && ty.toDouble() == 4.0) hd = true
            }
        }
        m.set("direct", !hc && hd)
        m.set("ottT", m.str("ottDate") ?: "")
        m.set("ottCT", m.str("ottC") ?: "")
        mdOtt(m)
        return m
    }

    /** live = streaming now, soon = OTT date announced, tba = nothing announced yet */
    fun ottState(m: Movie): String {
        val ott = m.arr("ott")
        if (ott != null && ott.length() > 0) return "live"
        val od = m.str("ottDate") ?: ""
        if (od.isNotEmpty() && od <= today()) return "live"
        return if (od.isNotEmpty()) "soon" else "tba"
    }

    /** confirmed streaming = the OTT date has really arrived (platform names alone do not count) */
    fun ottConfirmed(m: Movie): Boolean {
        val od = m.str("ottDate") ?: ""
        return od.isNotEmpty() && od <= today()
    }

    /** mdOtt(m): MDBList's digital date is used only when TMDB has no (or a later) OTT date */
    fun mdOtt(m: Movie) {
        if (m.tmdbId <= 0) return
        val t: String = if (m.has("ottT")) (m.str("ottT") ?: "")
        else (if (m.str("ottS") == "md") "" else (m.str("ottDate") ?: ""))
        val d = m.obj("md")?.let { if (Js.truthy(it.opt("d"))) it.opt("d").toString() else "" } ?: ""
        m.set("ottT", t)
        if (d.isNotEmpty() && (t.isEmpty() || d <= t)) {
            m.set("ottDate", d)
            m.set("ottC", "")
            m.set("ottS", "md")
        } else {
            m.set("ottDate", t)
            m.set("ottC", if (t.isNotEmpty()) (m.str("ottCT") ?: "") else "")
            m.set("ottS", "")
        }
    }

    // ---- OTT / details ------------------------------------------------------------------------------------------

    fun needsOtt(m: Movie): Boolean {
        if (key.isEmpty() || m.tmdbId <= 0) return false
        if (!m.truthy("ottChk") || !m.has("ov") || !m.has("runtime") || !m.has("direct") || !m.has("cert")) return true
        val up = m.status == "upcoming"
        val od = m.str("ottDate") ?: ""
        val due = ottState(m) != "live" || (up && !ottConfirmed(m))
        val age = now() - (m.num("ottChk") ?: 0.0)
        val every = if (up && od.isEmpty()) 108e5 else DAY
        val old = m.rel.isNotEmpty() && now() - Fmt.dayStartMs(m.rel) > 365 * DAY
        return due && age > every && !old
    }

    suspend fun enrichOtt(m: Movie, force: Boolean = false): Boolean = withContext(StateDispatcher) {
        if (m.tmdbId < 0) {
            if (key.isEmpty() || (!force && !m.isNull("seasons") && m.truthy("sl") && m.truthy("kind") && m.truthy("ottChk") && m.has("runtime"))) {
                return@withContext false
            }
            try {
                val d = Net.tmdb.get(Tmdb.path(m.tmdbId), mapOf("append_to_response" to "watch/providers,content_ratings"))
                m.set("cert", tvCert(d))
                m.set("rel", str(Js.or(d.opt("first_air_date"), m.j.opt("rel"), "")))
                m.set("ott", tvProv(d))
                m.set("ottChk", nowMs())
                m.set("runtime", tvRt(d))
                m.set("kind", "TV")
                m.set("seasons", numOr0(d.opt("number_of_seasons")))
                m.set("eps", numOr0(d.opt("number_of_episodes")))
                m.set("sl", slOf(d))
                return@withContext true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext false
            }
        }
        if (!force && !needsOtt(m)) return@withContext false
        try {
            val d = Net.tmdb.get("/movie/" + m.tmdbId, mapOf("append_to_response" to "release_dates,watch/providers"))
            applyRel(m, d)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // ---- IMDb ratings (OMDb + XMDb) --------------------------------------------------------------------------------

    fun needsE(m: Movie): Boolean {
        if (!((omdb.isNotEmpty() || xmKeys()) && key.isNotEmpty() && m.tmdbId != 0L)) return false
        if (!m.has("rt")) return true
        if (omdb.isNotEmpty() && !m.has("om")) return true
        if (xmKeys() && !m.truthy("xmc")) return true
        if (!m.truthy("xm")) {
            val lim = if (yearNum(m) >= thisYear() - 1) 216e5 else 2592e5
            return now() - (m.num("chk") ?: 0.0) > lim
        }
        return false
    }

    suspend fun enrich(m: Movie, force: Boolean = false): Boolean = withContext(StateDispatcher) {
        if (!force && !needsE(m)) return@withContext false
        if (force && !((omdb.isNotEmpty() || xmKeys()) && key.isNotEmpty() && m.tmdbId != 0L)) return@withContext false
        try {
            val id: String? =
                if (m.truthy("imdbId")) m.str("imdbId")
                else {
                    val d = Net.tmdb.get(Tmdb.path(m.tmdbId), mapOf("append_to_response" to "external_ids"))
                    val v = d.optJSONObject("external_ids")?.opt("imdb_id")
                    if (Js.truthy(v)) v.toString() else null
                }
            if (!id.isNullOrEmpty()) m.set("imdbId", id)
            val hasX = xmKeys()
            val xr = Net.xmdb.lookup(id)
            val xf = xr is XmRes.Failed // every XMDb key failed / hit its limit: do not mark XMDb as checked
            val x = if (xr is XmRes.Ok) xr.v else null
            val o = if (omdb.isNotEmpty()) {
                try {
                    Net.omdb.lookup(id ?: "", m.title, m.year)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else null
            if (o == null && x == null) return@withContext false
            val oReal = o != null
            val oImdb = o?.imdb ?: ""
            val oRt = o?.rt ?: ""
            val oVc = o?.vc ?: ""
            if (oReal) {
                m.set("om", oImdb)
                m.set("omvc", oVc)
            }
            if (x != null && x.imdb.isNotEmpty()) {
                m.set("xm", x.imdb)
                m.set("xvc", x.vc)
                m.set("vc", x.vc)
            } else if (!m.truthy("xm") && oImdb.isNotEmpty() && oVc.isNotEmpty()) {
                m.set("vc", oVc)
            }
            if (!o?.id.isNullOrEmpty() && !m.truthy("imdbId")) m.set("imdbId", o!!.id)
            m.set("rt", oRt.ifEmpty { m.str("rt") ?: "" })
            m.set("chk", nowMs())
            if (hasX && !xf) m.set("xmc", nowMs())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // ---- MDBList + TMDB score ----------------------------------------------------------------------------------------

    fun mdSkip(m: Movie): Boolean {
        if (m.tmdbId == 0L) return true
        val bad = mdBad[m.id]
        return bad != null && nowMs() - bad < 6e5
    }

    fun needsMd(m: Movie): Boolean {
        var ttl: Double = if (m.status == "upcoming" || unrel(m)) 108e5
        else (if (yearNum(m) >= thisYear() - 1) 216e5 else WEEK)
        val md = m.obj("md")
        if (md != null && !Js.truthy(md.opt("l")) && !unrel(m)) ttl = minOf(ttl, DAY)
        if (md != null && (!Js.truthy(md.opt("vv")) ||
                (!Js.truthy(md.opt("bv")) && !((m.num("wbud") ?: 0.0) > 0 || (m.num("wrev") ?: 0.0) > 0)))
        ) ttl = 0.0
        return !mdSkip(m) && (key.isNotEmpty() || mdKeys()) && (!m.has("mdc") || now() - (m.num("mdc") ?: 0.0) > ttl)
    }

    private class Tm(val undefinedNoKey: Boolean, val text: String?, val votes: Long)

    /** TMDB vote_average (one decimal) + vote_count; text == null means the request failed */
    private suspend fun tmScore(m: Movie): Tm {
        if (key.isEmpty()) return Tm(true, null, 0)
        return try {
            val d = Net.tmdb.get(Tmdb.path(m.tmdbId))
            val vc = d.optDouble("vote_count", Double.NaN)
            val va = d.optDouble("vote_average", Double.NaN)
            val votes = if (vc > 0) vc.toLong() else 0L
            Tm(false, if (vc > 0 && va > 0) Js.toFixed(va, 1) else "", votes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Tm(false, null, 0)
        }
    }

    suspend fun enrichMd(m: Movie, force: Boolean = false): Boolean = withContext(StateDispatcher) {
        if (mdSkip(m) || (!force && !needsMd(m))) return@withContext false
        try {
            val imdb = m.str("imdbId")
            val tmdbId = m.tmdbId
            val both = coroutineScope {
                val a = async { Net.mdblist.fetch(tmdbId, imdb) }
                val b = async { tmScore(m) }
                Pair(a.await(), b.await())
            }
            val o = both.first
            val t = both.second
            val okM = o is MdResult.Data
            val okT = t.text != null
            if (!okM && !okT) {
                if (o is MdResult.Failed || (!t.undefinedNoKey && t.text == null)) mdBad[m.id] = nowMs()
                return@withContext false
            }
            if (o is MdResult.Data) {
                m.set("md", o.md)
                mdOtt(m)
            }
            if (okT) {
                m.set("tm", t.text)
                m.set("tmv", t.votes)
            }
            m.set("mdc", nowMs())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mdBad[m.id] = nowMs()
            false
        }
    }

    // ---- Wikipedia box office -----------------------------------------------------------------------------------------

    fun wikiOK(m: Movie): Boolean =
        m.tmdbId > 0 && m.truthy("rel") && m.truthy("ottChk") && !unrel(m) && m.title.isNotEmpty() && yearNum(m) != 0.0

    fun needsWiki(m: Movie): Boolean {
        if (!wikiOK(m)) return false
        if (now() - (m.num("wkt") ?: 0.0) < 36e5) return false
        val v = m.j.opt("wkv")
        if (!m.has("wkc") || !(v is Number && v.toDouble() == 3.0)) return true
        val age = (now() - Fmt.dayStartMs(m.rel)) / DAY
        return age < 150 && now() - (m.num("wkc") ?: 0.0) > WEEK
    }

    suspend fun enrichWiki(m: Movie, force: Boolean = false): Boolean = withContext(StateDispatcher) {
        if (if (force) !wikiOK(m) else !needsWiki(m)) return@withContext false
        m.set("wkt", nowMs())
        try {
            val r = Wiki.find(m)
            m.set("wkc", nowMs())
            m.set("wkv", 3)
            val pr = if (r != null) Wiki.pair(r.bud, r.gross) else null
            val bo = if (pr == null && r != null) Wiki.amts(r.bud).firstOrNull() else null
            val go = if (pr == null && bo == null && r != null) Wiki.amts(r.gross).firstOrNull() else null
            if (pr != null && r != null) {
                m.set("wbud", pr.b); m.set("wrev", pr.r); m.set("wcur", pr.c); m.set("wpg", r.page)
            } else if (bo != null && r != null) {
                m.set("wbud", bo.v); m.set("wcur", bo.c); m.set("wpg", r.page); m.remove("wrev")
            } else if (go != null && r != null) {
                m.set("wrev", go.v); m.set("wcur", go.c); m.set("wpg", r.page); m.remove("wbud")
            } else {
                m.remove("wbud"); m.remove("wrev"); m.remove("wcur"); m.remove("wpg")
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** wkChain(m): OTT details first (they decide whether the movie is old enough for Wikipedia), then Wikipedia */
    suspend fun wkChain(m: Movie): Boolean = withContext(StateDispatcher) {
        if (needsWiki(m)) {
            val x = coroutineScope {
                val a = async { enrichOtt(m) }
                val b = async { enrichWiki(m) }
                Pair(a.await(), b.await())
            }
            x.first || x.second
        } else {
            val r = enrichOtt(m)
            val w = enrichWiki(m)
            r || w
        }
    }

    // ---- poster ------------------------------------------------------------------------------------------------------

    fun needsPoster(m: Movie): Boolean = key.isNotEmpty() && m.tmdbId != 0L && now() - (m.num("pchk") ?: 0.0) > WEEK

    suspend fun enrichPoster(m: Movie, force: Boolean = false): Boolean = withContext(StateDispatcher) {
        if (!force && !needsPoster(m)) return@withContext false
        try {
            val d = Net.tmdb.get(Tmdb.path(m.tmdbId))
            m.set("pchk", nowMs())
            if (Js.truthy(d.opt("poster_path"))) m.set("poster", d.opt("poster_path").toString())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun needsAny(m: Movie): Boolean = needsE(m) || needsOtt(m) || needsWiki(m) || needsPoster(m) || needsMd(m)

    // ---- refresh modes ---------------------------------------------------------------------------------------------------

    /** hotMode(m, anySt): upcoming movies are refreshed more often (All = everything, Rating = only ratings after streaming starts) */
    fun hotMode(m: Movie, anySt: Boolean = false): HotMode {
        if (m.tmdbId == 0L || (!anySt && m.status != "upcoming") || !m.truthy("rel") || unrel(m)) return HotMode.None
        if (!ottConfirmed(m)) {
            if (now() - Fmt.dayStartMs(m.rel) > 365 * DAY) return HotMode.None
            return HotMode.All
        }
        val st = Fmt.dayStartMs(m.str("ottDate"))
        return if (now() - st <= 7 * DAY) HotMode.Rating else HotMode.None
    }

    fun hot(m: Movie): Boolean = hotMode(m) != HotMode.None

    /**
     * openRefresh(m): refresh when a movie is opened. Opened again within a minute = only what is due;
     * otherwise everything is forced. Returns [something changed in the details, Wikipedia chain changed].
     */
    suspend fun openRefresh(m: Movie): List<Boolean> = withContext(StateDispatcher) {
        if (nowMs() - (m.long("fopen") ?: 0L) < 6e4) {
            coroutineScope {
                val a = async {
                    val x = enrich(m)
                    val l = enrichMd(m)
                    x || l
                }
                val b = async { wkChain(m) }
                listOf(a.await(), b.await())
            }
        } else {
            m.set("fopen", nowMs())
            val a = enrichOtt(m, true)
            val x = enrich(m, true)
            val c = enrichWiki(m, true)
            val pp = enrichPoster(m, true)
            val l = enrichMd(m, true)
            listOf(a || x || c || pp || l, false)
        }
    }

    @Volatile
    private var bfBusy = false

    /**
     * Settings > Save, `after()` (the part that touches movies): forget the failed MDBList lookups, let movies without MDBList data
     * look again (mdc removed = undefined) and, when an XMDb key exists, let movies without any IMDb rating be checked again (chk = 0).
     */
    suspend fun resetAfterKeys() = withContext(StateDispatcher) {
        mdBad.clear()
        val all = AppData.movies.toTypedArray()
        for (m in all) {
            if (!m.truthy("md")) m.remove("mdc")
        }
        if (xmKeys()) {
            for (m in all) {
                if (!m.truthy("imdb") && !m.truthy("xm") && !m.truthy("om") && m.tmdbId != 0L && m.has("rt")) m.set("chk", 0)
            }
        }
    }

    /**
     * backfill(): looks up what is due for up to 25 movies, 6 at a time, then saves the changed ones and tells the UI.
     * Called on start, when the app comes back to the front and every 30 minutes.
     */
    suspend fun backfill() = withContext(StateDispatcher) {
        com.trackerxx.movieverse.util.Quiet.await()   // not while the app starts / the list is scrolled
        if (bfBusy || key.isEmpty()) return@withContext
        val todo = AppData.movies.toTypedArray().filter { needsAny(it) }.take(25)
        if (todo.isEmpty()) return@withContext
        bfBusy = true
        val changed = ArrayList<Movie>()
        var i = 0
        try {
            coroutineScope {
                repeat(minOf(6, todo.size)) {
                    launch {
                        while (true) {
                            com.trackerxx.movieverse.util.Quiet.await()
                            if (i >= todo.size) break
                            val m = todo[i++]
                            val bf = snapF?.invoke(m)
                            val x: List<Boolean> = try {
                                coroutineScope {
                                    val a = async { enrich(m) }
                                    val b = async { wkChain(m) }
                                    val c = async { enrichPoster(m) }
                                    val d = async { enrichMd(m) }
                                    listOf(a.await(), b.await(), c.await(), d.await())
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                listOf(false, false, false, false)
                            }
                            if (x.any { it }) {
                                changed.add(m)
                                if (bf != null) noteChg(m, bf)   // rating / votes / Rotten Tomatoes changes stay quiet
                            }
                            delay(20)
                        }
                    }
                }
            }
        } finally {
            bfBusy = false
        }
        if (changed.isNotEmpty()) {
            AppData.db.putMany(changed)
            onChanged?.invoke()
        }
    }

    // ---- rating display priority ----------------------------------------------------------------------------------------

    /** rr(m): IMDb rating text. MDBList first, then XMDb (optional), then OMDb; null = N/A */
    fun imdbRating(m: Movie): String? {
        val md = m.obj("md")
        if (md != null && Js.truthy(md.opt("i"))) return md.opt("i").toString()
        if (m.truthy("xm")) return m.str("xm")
        if (m.truthy("om")) return m.str("om")
        return null
    }
}
