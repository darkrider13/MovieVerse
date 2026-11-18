package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.ApiKeys
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import org.json.JSONObject

/** {imdb, rt, vc} from OMDb (fields may be empty); id is set when the movie was found through a search */
class OmRating(val imdb: String, val rt: String, val vc: String, val id: String? = null)

/** OMDb: IMDb rating, Rotten Tomatoes score and IMDb votes. Same lookup steps and key rotation as the web version. */
class Omdb(private val apis: ApiKeys) {

    /** omReq(q): one request; on a limit / invalid-key answer moves to the next OMDb key and retries */
    suspend fun req(q: String, n: Int = 0): JSONObject {
        val r = Http.get("https://www.omdbapi.com/?" + q + "&apikey=" + Js.encodeURIComponent(apis.omdb))
        val j = Http.parseObject(r.body)
        if (limitHit(j) && apis.omdbs.size > 1 && n < apis.omdbs.size - 1) {
            apis.oi = (apis.oi + 1) % apis.omdbs.size
            apis.pickKeys()
            return req(q, n + 1)
        }
        return j
    }

    suspend fun byId(id: String): JSONObject = req("i=$id")

    suspend fun byTitle(t: String?, y: String?): JSONObject =
        req("t=" + Js.encodeURIComponent(t ?: "") + (if (!y.isNullOrEmpty()) "&y=" + Js.encodeURIComponent(y) else "") + "&type=movie")

    suspend fun search(t: String?, y: String?): JSONObject =
        req("s=" + Js.encodeURIComponent(t ?: "") + (if (!y.isNullOrEmpty()) "&y=" + Js.encodeURIComponent(y) else "") + "&type=movie")

    /**
     * lookupO(id, title, year): IMDb id first, then title + year, then a title search.
     * Returns null when OMDb could not be reached for the first lookup / the limit was hit.
     * Network errors of the first two lookups and of the last one are thrown, like rejected promises.
     */
    suspend fun lookup(id: String?, title: String?, year: String?): OmRating? {
        val j: JSONObject? = if (!id.isNullOrEmpty()) byId(id) else null
        if (limitHit(j)) return null
        val o = parseO(j)
        if (o != null && o.imdb.isNotEmpty() && o.rt.isNotEmpty() && o.vc.isNotEmpty()) return o

        val j2 = byTitle(title, year)
        if (limitHit(j2)) return o
        var o2 = parseO(j2)
        if (o2 != null && !id.isNullOrEmpty() && Js.truthy(j2.opt("imdbID")) && j2.opt("imdbID").toString() != id) o2 = null
        val m1 = OmRating(
            imdb = (o?.imdb ?: "").ifEmpty { o2?.imdb ?: "" },
            rt = (o?.rt ?: "").ifEmpty { o2?.rt ?: "" },
            vc = (o?.vc ?: "").ifEmpty { o2?.vc ?: "" }
        )
        if (m1.imdb.isNotEmpty()) return m1

        // title search; if the search itself fails the result so far (m1) is returned
        val j3: JSONObject = try {
            search(title, year)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return m1
        }
        val list = j3.optJSONArray("Search")
        val tl = (title ?: "").lowercase().trim()
        var hit: JSONObject? = null
        if (list != null) {
            for (i in 0 until list.length()) {
                val x = list.optJSONObject(i) ?: continue
                if ((if (Js.truthy(x.opt("Title"))) x.opt("Title").toString() else "").lowercase().trim() == tl) {
                    hit = x
                    break
                }
            }
            if (hit == null && list.length() == 1) hit = list.optJSONObject(0)
        }
        if (hit == null || !Js.truthy(hit.opt("imdbID"))) return m1
        val hid = hit.opt("imdbID").toString()
        val o4 = parseO(byId(hid)) ?: return m1
        return OmRating(
            imdb = o4.imdb.ifEmpty { m1.imdb },
            rt = m1.rt.ifEmpty { o4.rt },
            vc = m1.vc.ifEmpty { o4.vc },
            id = hid
        )
    }

    companion object {
        private val LIMIT_RE = Regex("limit|invalid api", RegexOption.IGNORE_CASE)

        /** limitHit(j): daily limit reached or key not valid */
        fun limitHit(j: JSONObject?): Boolean =
            j != null && j.opt("Response") == "False" && LIMIT_RE.containsMatchIn(
                if (Js.truthy(j.opt("Error"))) j.opt("Error").toString() else ""
            )

        /** parseO(j): reads the useful fields of an OMDb answer (null when OMDb says Response False) */
        fun parseO(j: JSONObject?): OmRating? {
            if (j == null || j.opt("Response") == "False") return null
            var imdb = ""
            var rt = ""
            var vc = ""
            val ir = j.opt("imdbRating")
            if (Js.truthy(ir) && ir != "N/A") imdb = ir.toString()
            val ratings = j.optJSONArray("Ratings")
            if (ratings != null) {
                for (i in 0 until ratings.length()) {
                    val x = ratings.optJSONObject(i) ?: continue
                    if (x.opt("Source") == "Rotten Tomatoes") rt = if (Js.truthy(x.opt("Value"))) x.opt("Value").toString() else ""
                }
            }
            val iv = j.opt("imdbVotes")
            if (Js.truthy(iv) && iv != "N/A") {
                vc = Fmt.fmtV(Js.parseInt(iv.toString().replace(",", "")))
            }
            return OmRating(imdb, rt, vc)
        }
    }
}
