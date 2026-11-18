package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.ApiKeys
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** mdFetch() answer: undefined = no MDBList key, null = failed / limit, object = ratings */
sealed class MdResult {
    /** no MDBList key added in Settings */
    object NoKey : MdResult()
    /** request failed or every key hit its limit */
    object Failed : MdResult()
    /** ratings (the object stored as movie.md; can be empty when MDBList does not know the title) */
    class Data(val md: JSONObject) : MdResult()
}

/**
 * MDBList: main rating source (IMDb, Letterboxd, Rotten Tomatoes, Metacritic, Roger Ebert, MyAnimeList, Trakt)
 * plus budget / revenue / digital release date. parse() builds exactly the object the web version stores in movie.md:
 * {tr: Trakt /10, i: IMDb, v: IMDb votes, l: Letterboxd /5, rt: RT critics %, ra: RT audience %, mc: Metacritic,
 *  mu: Metacritic users /10, eb: Roger Ebert /4, ma: MyAnimeList /10, vt: {votes per source}, bud, rev, bv, vv, d}
 */
class MdbList(private val apis: ApiKeys) {

    /** mdHttp(u): 8 s connect + 8 s read, JSON accepted */
    private suspend fun http(u: String): HttpResult =
        Http.get(u, mapOf("Accept" to "application/json"), 8000, 8000)

    /** mdFetch(m) */
    suspend fun fetch(tmdbId: Long, imdbId: String?): MdResult {
        val ks = apis.apiKeys("mdblist").filter { it.isNotEmpty() }
        if (ks.isEmpty()) return MdResult.NoKey
        val tv = tmdbId < 0
        val kind = if (tv) "show" else "movie"
        val path = if (!imdbId.isNullOrEmpty()) "imdb/$kind/$imdbId" else "tmdb/$kind/" + Math.abs(tmdbId)
        for (i in ks.indices) {
            try {
                val r = http("https://api.mdblist.com/" + path + "?apikey=" + Js.encodeURIComponent(ks[i]))
                if (r.status == 429 || r.status == 401 || r.status == 403) continue
                if (r.status == 404) return MdResult.Data(JSONObject())
                if (r.status != 200) return MdResult.Failed
                val j: Any? = try {
                    Http.parseAny(r.body)
                } catch (e: Exception) {
                    return MdResult.Failed
                }
                return MdResult.Data(parse(j as? JSONObject))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
        }
        return MdResult.Failed
    }

    companion object {
        private val MDS = mapOf(
            "imdb" to "i", "letterboxd" to "l", "tomatoes" to "rt", "rottentomatoes" to "rt", "tomatometer" to "rt",
            "popcorn" to "ra", "tomatoesaudience" to "ra", "audience" to "ra", "rottentomatoesaudience" to "ra",
            "metacritic" to "mc", "metacriticuser" to "mu", "rogerebert" to "eb", "myanimelist" to "ma", "mal" to "ma",
            "trakt" to "tr"
        )
        private val NOT_ALPHA = Regex("[^a-z]")
        private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}")

        /** mdNum(x): positive number or 0 */
        fun mdNum(x: Any?): Double {
            val v = Js.parseFloat(x)
            return if (v > 0) v else 0.0
        }

        /** mdParse(j) */
        fun parse(j: JSONObject?): JSONObject {
            val o = JSONObject()
            val rs = j?.optJSONArray("ratings")
            if (rs != null) {
                for (idx in 0 until rs.length()) {
                    val x = rs.optJSONObject(idx) ?: continue
                    val srcRaw = if (Js.truthy(x.opt("source"))) x.opt("source").toString() else ""
                    val k = MDS[srcRaw.lowercase().replace(NOT_ALPHA, "")] ?: continue
                    if (o.has(k) && Js.truthy(o.get(k))) continue
                    val sc = mdNum(x.opt("score"))
                    val v = mdNum(x.opt("value"))
                    val vt0 = Js.parseInt(x.opt("votes"))
                    if (vt0 > 0) {
                        val vt = o.optJSONObject("vt") ?: JSONObject().also { o.put("vt", it) }
                        vt.put(k, Fmt.fmtV(vt0))
                    }
                    when (k) {
                        "i" -> {
                            val t = if (v > 0 && v <= 10) v else sc / 10
                            if (t > 0) o.put("i", Js.toFixed(t, 1))
                            val vt = Js.parseInt(x.opt("votes"))
                            if (vt > 0) o.put("v", Fmt.fmtV(vt))
                        }
                        "l" -> {
                            val t = if (sc != 0.0) sc / 20 else (if (v > 5) v / 2 else v)
                            if (t > 0 && t <= 5) o.put("l", Js.toFixed(t, 1))
                        }
                        "rt", "ra", "mc" -> {
                            val t = if (sc != 0.0) sc else v
                            if (t > 0 && t <= 100) o.put(k, Js.round(t).toLong().toString())
                        }
                        "mu", "ma", "tr" -> {
                            val t = if (sc != 0.0) sc / 10 else v
                            if (t > 0 && t <= 10) o.put(k, Js.toFixed(t, 1))
                        }
                        "eb" -> {
                            val t = if (sc != 0.0) sc / 25 else v
                            if (t > 0 && t <= 4) o.put("eb", Js.toFixed(Js.round(t * 2) / 2, 1))
                        }
                    }
                }
            }
            val bn = mdNum(j?.opt("budget"))
            val rn = mdNum(j?.opt("revenue"))
            if (bn > 0) o.put("bud", bn)
            if (rn > 0) o.put("rev", rn)
            o.put("bv", 1)
            o.put("vv", 1) // budget / revenue (USD): used only when Wikipedia has none
            val dd0 = j?.opt("released_digital")
            val dd = if (Js.truthy(dd0)) dd0.toString() else ""
            if (DATE_RE.containsMatchIn(dd)) o.put("d", dd.take(10)) // digital (OTT) release date: used only when TMDB has none
            return o
        }
    }
}
