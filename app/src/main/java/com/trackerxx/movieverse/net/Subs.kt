package com.trackerxx.movieverse.net

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

/** one subtitle of the list: Bangla or English only */
class SubItem(
    val url: String,
    val release: String,
    val code: String,      // BN | EN
    val author: String,
    val season: Int,
    val episode: Int,
    val hi: Boolean
) {
    val langName: String get() = if (code == "BN") "Bangla" else "English"
    /** stable id for the row (download state) */
    val id: String get() = url
}

/** answer of one search page */
class SubPage(val items: List<SubItem>, val more: Boolean)

/**
 * Subtitles from SubDL (api.subdl.com). The key lives in Settings > "Subtitles" (api id `subs`, several keys are tried in turn).
 * Only Bangla (BN) and English (EN) are asked for. Download = the zip from dl.subdl.com, the subtitle files inside are
 * saved straight into the phone's Download folder.
 */
object Subs {
    private const val BASE = "https://api.subdl.com/api/v1/subtitles"
    private const val DL = "https://dl.subdl.com"
    const val PER_PAGE = 30

    fun keys(): List<String> = AppData.apis.apiKeys("subs").map { it.trim() }.filter { it.isNotEmpty() }

    fun hasKey(): Boolean = keys().isNotEmpty()

    /** one language, one page. Throws an Exception with a readable message. */
    suspend fun search(m: Movie, code: String, page: Int): SubPage {
        val ks = keys()
        if (ks.isEmpty()) throw IOException("NOKEY")
        var last: Exception? = null
        for (k in ks) {
            try {
                return one(m, code, page, k)
            } catch (e: IOException) {
                last = e
                if (e.message == "NOKEY") throw e
            }
        }
        throw last ?: IOException("Could not load subtitles")
    }

    private suspend fun one(m: Movie, code: String, page: Int, key: String): SubPage {
        val tv = m.tmdbId < 0
        val q = StringBuilder(BASE)
        q.append("?api_key=").append(URLEncoder.encode(key, "UTF-8"))
        q.append("&languages=").append(code)
        q.append("&subs_per_page=").append(PER_PAGE)
        q.append("&page=").append(page)
        q.append("&type=").append(if (tv) "tv" else "movie")
        if (m.tmdbId != 0L) {
            q.append("&tmdb_id=").append(Math.abs(m.tmdbId))
        } else {
            q.append("&film_name=").append(URLEncoder.encode(m.title, "UTF-8"))
            if (m.year.isNotEmpty()) q.append("&year=").append(URLEncoder.encode(m.year, "UTF-8"))
        }
        val r = Http.get(q.toString(), connectMs = 20000, readMs = 30000)
        if (r.status == 401 || r.status == 403) throw IOException("The subtitle API key was not accepted")
        if (r.status == 429) throw IOException("Subtitle limit reached, try again later")
        val o = try { Http.parseObject(r.body) } catch (e: Exception) { throw IOException("Could not load subtitles") }
        if (o.has("status") && !o.optBoolean("status", true)) {
            val msg = o.optString("error", "")
            throw IOException(if (msg.isNotEmpty()) msg else "Could not load subtitles")
        }
        val arr = o.optJSONArray("subtitles")
        val out = ArrayList<SubItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val url = s.optString("url", "")
                if (url.isEmpty()) continue
                val lc = s.optString("language", "").uppercase()
                val ln = s.optString("lang", "").lowercase()
                val c = when {
                    lc == "BN" || ln.startsWith("bengali") || ln.startsWith("bangla") -> "BN"
                    lc == "EN" || ln.startsWith("english") -> "EN"
                    else -> continue
                }
                var rel = s.optString("release_name", "")
                if (rel.isEmpty()) rel = s.optString("name", "")
                if (rel.isEmpty()) rel = m.title
                out.add(
                    SubItem(
                        url = url, release = rel, code = c, author = s.optString("author", ""),
                        season = s.optInt("season", 0), episode = s.optInt("episode", 0), hi = s.optBoolean("hi", false)
                    )
                )
            }
        }
        return SubPage(out, (arr?.length() ?: 0) >= PER_PAGE)
    }

    private fun safe(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(110)

    private fun mime(ext: String): String = when (ext) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    /**
     * Downloads the zip and saves every subtitle file in it to the Download folder.
     * Returns the names of the saved files (usually one).
     */
    suspend fun download(ctx: Context, m: Movie, sub: SubItem): List<String> = withContext(Dispatchers.IO) {
        val c = URL(DL + (if (sub.url.startsWith("/")) sub.url else "/" + sub.url)).openConnection() as HttpURLConnection
        val bytes: ByteArray = try {
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "MovieVerse/1.0 (Android)")
            if (c.responseCode !in 200..299) throw IOException("Download failed (" + c.responseCode + ")")
            c.inputStream.use { st -> st.readBytes() }
        } finally {
            c.disconnect()
        }
        val base = safe(m.title + (if (m.year.isNotEmpty()) " (" + m.year + ")" else "")) + " - " + sub.langName
        val exts = setOf("srt", "ass", "ssa", "sub", "vtt")
        val files = ArrayList<Pair<String, ByteArray>>()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val nm = e.name.substringAfterLast('/')
                    val ext = nm.substringAfterLast('.', "").lowercase()
                    if (ext !in exts) continue
                    files.add(nm to z.readBytes())
                }
            }
        } else {
            files.add("subtitle.srt" to bytes)
        }
        if (files.isEmpty()) throw IOException("No subtitle file found in the download")
        val saved = ArrayList<String>()
        for ((n, data) in files) {
            val ext = n.substringAfterLast('.', "srt").lowercase()
            val stem = safe(n.substringBeforeLast('.', n))
            val name = (if (files.size == 1) base + (if (stem.isNotEmpty() && stem != "subtitle") " - $stem" else "") else "$base - $stem") + "." + ext
            saved.add(save(ctx, name, ext, data))
        }
        saved
    }

    /** writes into the public Download folder (MediaStore on Android 10+, the folder itself on older phones) */
    private fun save(ctx: Context, name: String, ext: String, data: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime(ext))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val res = ctx.contentResolver
            val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IOException("Could not create the file")
            try {
                (res.openOutputStream(uri) ?: throw IOException("Could not open the file")).use { o -> o.write(data) }
            } catch (e: Exception) {
                try { res.delete(uri, null, null) } catch (x: Exception) { }
                throw e
            }
            return name
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not open the Download folder")
        var f = File(dir, name)
        var n = 1
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + " (" + n + ")." + ext)
            n++
        }
        f.outputStream().use { o -> o.write(data) }
        return f.name
    }
}
