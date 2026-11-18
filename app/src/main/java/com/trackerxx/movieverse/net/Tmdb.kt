package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.ApiKeys
import com.trackerxx.movieverse.util.Js
import org.json.JSONObject

/** Error with a message the user can read (shown in a toast / dialog) */
class NetException(message: String) : Exception(message)

/** TMDB v3 API. Key rotation, error texts and the Bearer-token rule are the same as the web version's tmdb(). */
class Tmdb(private val apis: ApiKeys) {

    /**
     * tmdb(path, params): GET https://api.themoviedb.org/3{path}
     * - key longer than 40 characters = v4 token (Authorization: Bearer), otherwise ?api_key=
     * - 401 / 429 with several keys: moves to the next key and tries again
     * - other errors throw NetException with the same texts as before
     */
    suspend fun get(path: String, params: Map<String, String> = emptyMap(), n: Int = 0): JSONObject {
        val key = apis.key
        val q = ArrayList<String>()
        val headers = HashMap<String, String>()
        headers["Accept"] = "application/json"
        if (key.length > 40) headers["Authorization"] = "Bearer $key"
        else q.add("api_key=" + Js.encodeURIComponent(key))
        for ((k, v) in params) q.add(Js.encodeURIComponent(k) + "=" + Js.encodeURIComponent(v))
        val url = "https://api.themoviedb.org/3" + path + (if (q.isEmpty()) "" else "?" + q.joinToString("&"))

        val r = Http.get(url, headers)
        if ((r.status == 401 || r.status == 429) && apis.keys.size > 1 && n < apis.keys.size - 1) {
            apis.ki = (apis.ki + 1) % apis.keys.size
            apis.pickKeys()
            return get(path, params, n + 1)
        }
        if (!r.ok) {
            if (r.status == 401) {
                val k = apis.key
                throw NetException(
                    "That API key was not accepted." +
                        (if (k.length <= 40 && k.length != 32)
                            " A TMDB API key has 32 characters, yours has " + k.length + ". Copy it again from themoviedb.org > Settings > API."
                        else " Check it on themoviedb.org > Settings > API.")
                )
            }
            throw NetException("TMDB error " + r.status)
        }
        return Http.parseObject(r.body)
    }

    companion object {
        const val IMG = "https://image.tmdb.org/t/p/"

        /** tp(id): TV shows are stored with a negative id */
        fun path(id: Long): String = if (id < 0) "/tv/" + (-id) else "/movie/$id"

        /** img(): full poster url, null when there is no poster */
        fun imgUrl(p: String?, size: String = "w342"): String? = if (p.isNullOrEmpty()) null else IMG + size + p
    }
}
