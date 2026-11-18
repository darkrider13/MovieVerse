package com.trackerxx.movieverse.net

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** status + body of a finished request (like fetch's Response after .text()) */
class HttpResult(val status: Int, val body: String) {
    /** r.ok */
    val ok: Boolean get() = status in 200..299
}

/**
 * One thread that owns all changes to movies. The web version runs on a single JS thread, so enrichment code can change a
 * movie at any time without locks. Here every enrichment function runs on this dispatcher; only the network waits run in parallel.
 */
val StateDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { r -> Thread(r, "mv-state").also { it.isDaemon = true } }.asCoroutineDispatcher()

/** Background scope for work that must outlive a screen (cache refreshes, Wikipedia lookups). */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

object Http {
    private const val UA = "MovieVerse/1.0 (Android)"

    /**
     * GET request. Network problems throw an IOException (= a rejected fetch promise in the web version).
     * HTTP error codes do NOT throw: check status / ok.
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectMs: Int = 30000,
        readMs: Int = 30000
    ): HttpResult = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            val code = c.responseCode
            val stream = if (code in 200..399) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpResult(code, text)
        } finally {
            c.disconnect()
        }
    }

    /** r.json() for an object answer; throws when the body is not JSON (like a rejected r.json()) */
    fun parseObject(text: String): JSONObject = JSONObject(text)

    /**
     * r.json() for an answer of any shape: JSONObject, JSONArray, a plain value or JSON null.
     * Throws when the body is not valid JSON.
     */
    fun parseAny(text: String): Any? {
        val v = JSONTokener(text).nextValue()
        return if (v == JSONObject.NULL) null else v
    }

    fun isObject(v: Any?) = v is JSONObject
    fun isArray(v: Any?) = v is JSONArray
}
