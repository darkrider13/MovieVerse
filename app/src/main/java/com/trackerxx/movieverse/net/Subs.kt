package com.trackerxx.movieverse.net

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/** one subtitle of the list: Bangla or English only */
class SubItem(
    /** SubSource's subtitle id (used for the download) */
    val id: String,
    val release: String,
    val code: String,      // BN | EN
    val author: String,
    val season: Int,
    val episode: Int,
    val hi: Boolean
) {
    val langName: String get() = if (code == "BN") "Bangla" else "English"
}

/** answer of one search page (SubSource sends the whole list at once, so `more` is always false) */
class SubPage(val items: List<SubItem>, val more: Boolean)

/**
 * Subtitles from SubSource (api.subsource.net), same steps as the web version:
 *  1. find the film's SubSource entries (IMDb id first, then a title search; a series has one entry per season),
 *  2. list the Bangla / English subtitles of those entries,
 *  3. download = the zip of that subtitle, the subtitle files inside are saved straight into the phone's Download folder.
 * The key lives in Settings > "Subtitles" (api id `subs`) and is sent as the X-API-Key header.
 */
object Subs {
    private const val BASE = "https://api.subsource.net/api/v1"
    private val LANGS = mapOf("BN" to "bengali", "EN" to "english")

    /** a SubSource entry of the film (a series has one per season; season 0 = a movie or a season-less entry) */
    private class Entry(val id: String, val season: Int)

    /** tmdbId -> entries found (so the two languages and the probe search only once) */
    private val cache = ConcurrentHashMap<Long, List<Entry>>()
    /** tmdbId -> entries with the same name and year but another IMDb id, tried only if the main ones have no subtitle */
    private val altCache = ConcurrentHashMap<Long, List<Entry>>()

    fun keys(): List<String> = AppData.apis.apiKeys("subs").map { it.trim() }.filter { it.isNotEmpty() }

    fun hasKey(): Boolean = keys().isNotEmpty()

    // ---- low level -----------------------------------------------------------------------------------------------------

    private fun check(status: Int) {
        if (status == 401 || status == 403) throw IOException("The SubSource API key was not accepted")
        if (status == 429) throw IOException("Too many requests. Try again in a minute.")
        if (status == 404) throw IOException("NOTFOUND")
        if (status >= 400) throw IOException("Could not load subtitles")
    }

    /** GET a JSON answer of SubSource; a 404 answers null */
    private suspend fun getJson(path: String): Any? {
        val k = keys().firstOrNull() ?: throw IOException("NOKEY")
        val r = Http.get(
            BASE + path, mapOf("X-API-Key" to k, "Accept" to "application/json"),
            connectMs = 20000, readMs = 40000
        )
        try {
            check(r.status)
        } catch (e: IOException) {
            if (e.message == "NOTFOUND") return null
            throw e
        }
        return try { Http.parseAny(r.body) } catch (e: Exception) { throw IOException("Could not load subtitles") }
    }

    /** the first list inside an answer (the answer may be a list, or hold it under data / results / subtitles / movies / items) */
    private fun rows(j: Any?): List<JSONObject> {
        val arr: JSONArray? = when (j) {
            is JSONArray -> j
            is JSONObject -> {
                var f: JSONArray? = null
                for (k in arrayOf("data", "results", "subtitles", "movies", "items")) {
                    val v = j.opt(k)
                    if (v is JSONArray) { f = v; break }
                    if (v is JSONObject) {
                        val r = rows(v)
                        if (r.isNotEmpty()) return r
                    }
                }
                f
            }
            else -> null
        }
        val out = ArrayList<JSONObject>()
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    private fun isHard(e: Exception): Boolean {
        val m = e.message ?: return false
        return m == "NOKEY" || m.contains("not accepted") || m.contains("Too many")
    }

    private fun txt(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            if (v == null || v == JSONObject.NULL) continue
            if (v is JSONArray) { if (v.length() > 0 && v.opt(0) is String) return v.getString(0) else continue }
            if (v is String) { if (v.isNotEmpty()) return v } else if (v is Number) return v.toString()
        }
        return ""
    }

    private fun idOf(o: JSONObject): String =
        txt(o, "movieId", "id", "movie_id")

    private fun yearOf(o: JSONObject): Int =
        txt(o, "releaseYear", "year", "release_year").toIntOrNull() ?: 0

    private fun titleOf(o: JSONObject): String =
        Net.qsearch.qn(txt(o, "title", "name").replace(Regex("\\(\\s*\\d{4}\\s*\\)\\s*$"), ""))

    /** first value of a key (matched by regex) anywhere in the object, up to 3 levels deep */
    private fun find(o: JSONObject?, re: Regex, depth: Int = 0): String? {
        if (o == null || depth > 2) return null
        val iter = o.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val v = o.opt(k)
            if (re.matches(k) && v != null && v != JSONObject.NULL && v !is JSONObject && v !is JSONArray) return v.toString()
        }
        val iter2 = o.keys()
        while (iter2.hasNext()) {
            val v = o.opt(iter2.next())
            if (v is JSONObject) find(v, re, depth + 1)?.let { return it }
        }
        return null
    }

    private fun imdbDigits(o: JSONObject): String {
        val v = find(o, Regex("^imdb_?(id)?$", RegexOption.IGNORE_CASE)) ?: return ""
        return Regex("(?:tt)?(\\d{5,10})").find(v)?.groupValues?.get(1) ?: ""
    }

    private suspend fun imdbOf(m: Movie): String {
        val have = m.str("imdbId")
        if (!have.isNullOrEmpty()) return have
        return try {
            val d = Net.tmdb.get(Tmdb.path(m.tmdbId), mapOf("append_to_response" to "external_ids"))
            d.optJSONObject("external_ids")?.optString("imdb_id", "") ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    // ---- step 1: the film's SubSource entries ---------------------------------------------------------------------------

    private fun seasonOf(o: JSONObject): Int {
        val n = (txt(o, "season", "seasonNumber", "season_number")).toIntOrNull() ?: 0
        if (n > 0) return n
        val t = txt(o, "title", "name")
        val r = Regex("season\\s*(\\d+)", RegexOption.IGNORE_CASE).find(t) ?: Regex("\\bS(\\d{1,2})\\b", RegexOption.IGNORE_CASE).find(t)
        return r?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** series: one entry per season (the season-less "All" entry is dropped when real seasons exist) */
    private suspend fun tvEntries(m: Movie): List<Entry> {
        val key = m.tmdbId
        cache[key]?.let { return it }
        val nt = Net.qsearch.qn(m.title)
        val im = imdbOf(m)
        var l: List<JSONObject> = emptyList()
        if (im.isNotEmpty()) {
            l = try { rows(getJson("/movies/search?searchType=imdb&imdb=" + URLEncoder.encode(im, "UTF-8"))) }
            catch (e: IOException) { if (isHard(e)) throw e else emptyList() }
        }
        if (l.isEmpty()) {
            l = try {
                rows(getJson("/movies/search?searchType=text&q=" + URLEncoder.encode(m.title, "UTF-8")))
                    .filter { Net.qsearch.qn(txt(it, "title", "name")).contains(nt) }
            } catch (e: IOException) { if (isHard(e)) throw e else emptyList() }
        }
        val seen = HashSet<Int>()
        var out = ArrayList<Entry>()
        for (x in l) {
            val id = idOf(x)
            val sn = seasonOf(x)
            if (id.isEmpty() || !seen.add(sn)) continue
            out.add(Entry(id, sn))
        }
        val withSeason = out.filter { it.season > 0 }
        val res = (if (withSeason.isNotEmpty()) withSeason else out).sortedBy { it.season }
        cache[key] = res
        return res
    }

    /** movie: every entry with the same title (+ year +-1); an entry with another IMDb id is kept aside as a fallback */
    private suspend fun movieEntries(m: Movie): List<Entry> {
        val key = m.tmdbId
        cache[key]?.let { return it }
        val yr = m.year.toIntOrNull() ?: m.rel.take(4).toIntOrNull() ?: 0
        val nt = Net.qsearch.qn(m.title)
        val ids = LinkedHashSet<String>()
        val extra = LinkedHashSet<String>()
        val im = imdbOf(m)
        val myIm = im.filter { it.isDigit() }
        fun okIm(x: JSONObject): Boolean {
            val a = imdbDigits(x)
            return myIm.isEmpty() || a.isEmpty() || a == myIm
        }
        fun same(l: List<JSONObject>): List<JSONObject> {
            val o = ArrayList<JSONObject>()
            for (x in l) {
                if (titleOf(x) != nt) continue
                val y = yearOf(x)
                if (yr != 0 && y != 0 && Math.abs(y - yr) > 1) continue
                if (okIm(x)) o.add(x) else idOf(x).takeIf { it.isNotEmpty() }?.let { extra.add(it) }
            }
            return o
        }
        fun byText(l: List<JSONObject>): JSONObject? {
            for (x in l) if (okIm(x) && yr != 0 && yearOf(x) == yr && (titleOf(x).contains(nt) || nt.contains(titleOf(x)))) return x
            if (yr == 0) for (x in l) if (okIm(x) && titleOf(x).contains(nt)) return x
            return null
        }
        if (im.isNotEmpty()) {
            try {
                for (x in rows(getJson("/movies/search?searchType=imdb&imdb=" + URLEncoder.encode(im, "UTF-8")))) {
                    val id = idOf(x)
                    if (id.isEmpty()) continue
                    if (okIm(x)) ids.add(id) else extra.add(id)
                }
            } catch (e: IOException) { if (isHard(e)) throw e }
        }
        suspend fun tq(q: String): List<JSONObject> = try {
            val l = rows(getJson("/movies/search?searchType=text&q=" + URLEncoder.encode(q, "UTF-8")))
            for (x in same(l)) idOf(x).takeIf { it.isNotEmpty() }?.let { ids.add(it) }
            l
        } catch (e: IOException) { if (isHard(e)) throw e else emptyList() }
        val l0 = tq(m.title)
        val l1 = if (yr != 0) tq(m.title + " " + yr) else emptyList()
        if (ids.isEmpty()) byText(l0 + l1)?.let { x -> idOf(x).takeIf { it.isNotEmpty() }?.let { ids.add(it) } }
        var main = ids.toList()
        var alt = extra.filter { it !in ids }
        if (main.isEmpty() && alt.isNotEmpty()) { main = alt; alt = emptyList() }
        val res = main.map { Entry(it, 0) }
        cache[key] = res
        altCache[key] = alt.map { Entry(it, 0) }
        return res
    }

    /** one look-up at a time: Bangla and English ask together, the second one then finds the answer in the cache */
    private val lookLock = Mutex()

    private suspend fun entries(m: Movie): List<Entry> =
        lookLock.withLock { if (m.tmdbId < 0) tvEntries(m) else movieEntries(m) }

    // ---- step 2: subtitles of the entries ------------------------------------------------------------------------------

    private fun clean(t: String): String {
        if (t.indexOf('&') < 0) return t
        return if (Build.VERSION.SDK_INT >= 24) android.text.Html.fromHtml(t, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        else @Suppress("DEPRECATION") android.text.Html.fromHtml(t).toString()
    }

    private fun nameOf(v: Any?): String {
        var x = v
        if (x is JSONArray) x = if (x.length() > 0) x.opt(0) else null
        if (x is JSONObject) {
            for (k in arrayOf("displayName", "display_name", "username", "userName", "name", "nickname", "fullName", "full_name")) {
                val s = x.optString(k, "").trim()
                if (s.isNotEmpty()) return s
            }
            return ""
        }
        return (x as? String)?.trim() ?: ""
    }

    private val OWNER = Regex(
        "^(contributors?|uploaders?|uploader_?(name|id)?|uploaded_?by|authors?|author_?name|owners?|owner_?name|users?|user_?name|creators?|created_?by|translators?|submitted_?by|commentary_?by)$",
        RegexOption.IGNORE_CASE
    )
    private val SKIP = Regex("rating|vote|file|link|release", RegexOption.IGNORE_CASE)

    /** the uploader: any key that says contributor / uploader / author / owner ... (up to 3 levels deep) */
    private fun owner(o: JSONObject?, d: Int = 0): String {
        if (o == null || d > 3) return ""
        val iter = o.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (OWNER.matches(k)) {
                val v = nameOf(o.opt(k))
                if (v.isNotEmpty() && !v.all { c -> c.isDigit() }) return v
            }
        }
        val iter2 = o.keys()
        while (iter2.hasNext()) {
            val k = iter2.next()
            val v = o.opt(k)
            if (v is JSONObject && !SKIP.containsMatchIn(k)) {
                val z = owner(v, d + 1)
                if (z.isNotEmpty()) return z
            }
        }
        return ""
    }

    /** the Bangla / English subtitles of one SubSource entry */
    private suspend fun forEntry(e: Entry, code: String): List<SubItem> {
        val lang = LANGS[code] ?: return emptyList()
        val j = getJson("/subtitles?movieId=" + URLEncoder.encode(e.id, "UTF-8") + "&language=" + lang) ?: return emptyList()
        val out = ArrayList<SubItem>()
        for (x in rows(j)) {
            val id = txt(x, "subtitleId", "id", "subtitle_id")
            if (id.isEmpty()) continue
            val l = x.optString("language", "").lowercase()
            if (l.isNotEmpty() && !l.contains(lang) && !(code == "BN" && (l.contains("bangla") || l == "bn")) && !(code == "EN" && l == "en")) continue
            var nm = clean(txt(x, "releaseInfo", "release_info", "release", "name", "title"))
            if (nm.isEmpty()) nm = "Subtitle"
            val hi = x.optBoolean("hearingImpaired", x.optBoolean("hi", false))
            out.add(SubItem(id, nm, code, clean(owner(x)), e.season, 0, hi))
        }
        return out
    }

    private suspend fun forEntries(list: List<Entry>, code: String): List<SubItem> = coroutineScope {
        val parts = list.map { e ->
            async {
                try { forEntry(e, code) } catch (x: CancellationException) { throw x } catch (x: IOException) { if (isHard(x)) throw x else emptyList() }
            }
        }
        val seen = HashSet<String>()
        val out = ArrayList<SubItem>()
        for (p in parts) for (s in p.await()) if (seen.add(s.id)) out.add(s)
        out
    }

    /** one language. SubSource sends everything at once: page 1 holds the whole list, later pages are empty. Throws an Exception with a readable message. */
    suspend fun search(m: Movie, code: String, page: Int): SubPage {
        if (keys().isEmpty()) throw IOException("NOKEY")
        if (page > 1) return SubPage(emptyList(), false)
        val es = entries(m)
        if (es.isEmpty()) return SubPage(emptyList(), false)
        var items = forEntries(es, code)
        if (items.isEmpty() && m.tmdbId >= 0) {
            val alt = altCache[m.tmdbId].orEmpty()
            if (alt.isNotEmpty()) items = forEntries(alt, code)
        }
        return SubPage(items, false)
    }

    /** is there any Bangla / English subtitle? (a series: only the first 6 seasons are looked at, then "yes" so SubSource is not hammered) */
    suspend fun probe(m: Movie): Boolean {
        if (keys().isEmpty()) throw IOException("NOKEY")
        val es = entries(m)
        if (es.isEmpty()) return false
        if (m.tmdbId < 0) {
            for ((i, e) in es.withIndex()) {
                if (i >= 6) return true
                for (c in arrayOf("BN", "EN")) if (forEntry(e, c).isNotEmpty()) return true
            }
            return false
        }
        for (c in arrayOf("BN", "EN")) if (forEntries(es, c).isNotEmpty()) return true
        val alt = altCache[m.tmdbId].orEmpty()
        if (alt.isNotEmpty()) for (c in arrayOf("BN", "EN")) if (forEntries(alt, c).isNotEmpty()) return true
        return false
    }

    private fun safe(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(110)

    private fun mime(ext: String): String = when (ext) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    /**
     * Downloads the zip of a subtitle and saves every subtitle file in it to the Download folder.
     * Returns the names of the saved files (usually one).
     */
    suspend fun download(ctx: Context, m: Movie, sub: SubItem): List<String> = withContext(Dispatchers.IO) {
        val k = keys().firstOrNull() ?: throw IOException("NOKEY")
        val c = URL(BASE + "/subtitles/" + URLEncoder.encode(sub.id, "UTF-8") + "/download").openConnection() as HttpURLConnection
        val bytes: ByteArray = try {
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "MovieVerse/1.0 (Android)")
            c.setRequestProperty("X-API-Key", k)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code == 401 || code == 403) throw IOException("The SubSource API key was not accepted")
            if (code == 429) throw IOException("Too many requests. Try again in a minute.")
            if (code == 404) throw IOException("This subtitle is no longer available.")
            if (code !in 200..299) throw IOException("Download failed ($code)")
            c.inputStream.use { st -> st.readBytes() }
        } finally {
            c.disconnect()
        }
        // the file keeps the subtitle's own name (only characters Android does not allow are replaced)
        val own = safe(sub.release.replace(Regex("\\.(srt|ass|ssa|vtt|sub|zip)$", RegexOption.IGNORE_CASE), "")).trimEnd('.', ' ')
        val base = if (own.isNotEmpty()) own else safe(m.title + (if (m.year.isNotEmpty()) " (" + m.year + ")" else "")) + " - " + sub.langName
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
            val name = (if (files.size == 1) base else "$base - $stem") + "." + ext
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
