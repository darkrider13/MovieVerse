package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.ApiKeys
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** IMDb rating and vote text from XMDb */
class XmOut(val imdb: String, val vc: String)

/** xmLookup() answer: Ok(null) = movie not found / no rating, Failed = every key failed (JS `false`) */
sealed class XmRes {
    object Failed : XmRes()
    class Ok(val v: XmOut?) : XmRes()
}

/** XMDb: optional second source of the IMDb rating (used when OMDb has none). Remembers the working key (xi). */
class Xmdb(private val apis: ApiKeys) {

    /** xmGet(id, ks, n): any failure except 404 or a limit message moves to the next key */
    private suspend fun get(id: String, ks: List<String>, n: Int = 0): XmRes {
        if (apis.xi >= ks.size) apis.xi = 0
        val i = (apis.xi + n) % ks.size
        try {
            val r = Http.get("https://xmdbapi.com/api/v1/movies/" + Js.encodeURIComponent(id) + "?apiKey=" + Js.encodeURIComponent(ks[i]))
            if (r.status == 404) return XmRes.Ok(null)
            if (!r.ok) return next(id, ks, n)
            val j: Any? = try {
                Http.parseAny(r.body)
            } catch (e: Exception) {
                return next(id, ks, n)
            }
            if (limit(j)) return next(id, ks, n)
            apis.xi = i
            return XmRes.Ok(parse(j))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return next(id, ks, n)
        }
    }

    private suspend fun next(id: String, ks: List<String>, n: Int): XmRes =
        if (n < ks.size - 1) get(id, ks, n + 1) else XmRes.Failed

    /** xmLookup(id) */
    suspend fun lookup(id: String?): XmRes {
        val ks = apis.apiKeys("xmdb").filter { it.isNotEmpty() }
        if (id.isNullOrEmpty() || ks.isEmpty()) return XmRes.Ok(null)
        return get(id, ks, 0)
    }

    companion object {
        private val LIMIT_RE = Regex("limit|quota|exceed|too many|rate|credit|subscription|unauthor|forbidden|invalid", RegexOption.IGNORE_CASE)
        private val NON_DIGIT = Regex("[^0-9]")

        private fun prop(o: Any?, k: String): Any? = (o as? JSONObject)?.let { Js.clean(it.opt(k)) }

        /** xmLimit(j) */
        fun limit(j: Any?): Boolean {
            val o = j as? JSONObject ?: return false
            val s = Js.or(Js.clean(o.opt("error")), Js.clean(o.opt("message")), Js.clean(o.opt("detail")), Js.clean(o.opt("msg")), "")
            return LIMIT_RE.containsMatchIn(s?.toString() ?: "") && parse(j) == null
        }

        /** xmParse(j): the answer can be wrapped in data / result and the rating can sit in several places */
        fun parse(j: Any?): XmOut? {
            val top = j as? JSONObject ?: return null
            val d: JSONObject = top.optJSONObject("data") ?: top.optJSONObject("result") ?: top
            var r: Any? = Js.clean(d.opt("rating"))
            var v: Any? = Js.clean(d.opt("vote_count"))
            if (Js.truthy(r) && (r is JSONObject || r is JSONArray)) {
                v = Js.or(v, prop(r, "vote_count"), prop(r, "votes"), prop(r, "voteCount"), prop(r, "votes_count"))
                r = Js.or(prop(r, "aggregate_rating"), prop(r, "aggregateRating"), prop(r, "rating"), prop(r, "value"), prop(r, "average"))
            }
            val ratings = Js.clean(d.opt("ratings"))
            if (r == null && Js.truthy(ratings) && (ratings is JSONObject || ratings is JSONArray)) {
                r = Js.or(prop(ratings, "aggregate_rating"), prop(ratings, "rating"), prop(ratings, "imdb"), prop(ratings, "value"))
                v = Js.or(v, prop(ratings, "vote_count"), prop(ratings, "votes"))
            }
            if (r == null) {
                r = Js.or(Js.clean(d.opt("imdb_rating")), Js.clean(d.opt("imdbRating")), Js.clean(d.opt("aggregate_rating")))
            }
            val rn = Js.parseFloat(r)
            if (!(rn > 0)) return null
            val vs = if (Js.truthy(v)) v.toString() else ""
            val vn = Js.parseInt(vs.replace(NON_DIGIT, ""))
            val vc = if (!vn.isNaN() && vn != 0.0) Fmt.fmtV(vn) else ""
            return XmOut(Js.toFixed(rn, 1), vc)
        }
    }
}
