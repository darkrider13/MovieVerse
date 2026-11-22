package com.trackerxx.movieverse.ui.settings

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Backup
import com.trackerxx.movieverse.data.BackupScan
import com.trackerxx.movieverse.data.Folder
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.data.Status
import com.trackerxx.movieverse.data.XlsxReader
import com.trackerxx.movieverse.data.jsNumToString
import com.trackerxx.movieverse.net.StateDispatcher
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.MvApiLoadingDialog
import com.trackerxx.movieverse.ui.components.MvOkDialog
import com.trackerxx.movieverse.ui.components.ToastState
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/** the green dialog after an import ("Import successful", "Import stopped", "Import did not finish") */
class ImportOk(val title: String, val message: String)

/**
 * Settings > Import (`#imp.onclick`, `importFile`, `mkScan`, `normM`, `normStatus`, `safetyCopy` of the web version).
 * The backup can be an Excel file (.xlsx, XlsxReader) or the old .json file (BackupScan); the kind is found by looking at the first bytes
 * of the file (an .xlsx is a zip: "PK"), not by its name. Both go through the same flush / finish below (same merge rules).
 * The file is read in pieces and the movies are saved batch by batch, so memory stays small even for a very big backup.
 * Movies that are already in the library (same id) are skipped; nothing is ever removed.
 */
@Stable
class ImportFlow(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val toast: ToastState
) {
    /** "Importing backup" progress dialog: null = hidden, otherwise the caption */
    var progress by mutableStateOf<String?>(null)
        private set

    /** the green OK dialog */
    var ok by mutableStateOf<ImportOk?>(null)
        private set

    fun dismissOk() {
        ok = null
    }

    /** what HomeScreen gives to start(): the pieces of the web flow that live outside this class */
    private class Pending(
        val closeSettings: () -> Unit,
        val selTrend: () -> Unit,
        val keyBusy: suspend () -> String?
    )

    private var pending: Pending? = null

    // set by rememberImportFlow (activity result launchers)
    internal var launchPick: (() -> Unit)? = null
    internal var launchPerm: ((String) -> Unit)? = null
    internal var permDone: CompletableDeferred<Boolean>? = null

    /**
     * The Import button. closeSettings = `$('#sdlg').close()`, selTrend = `selTrend()`,
     * keyBusy = `apiBusy(keyLoad(), 900, 45000)` (returns the error text or null).
     */
    fun start(closeSettings: () -> Unit, selTrend: () -> Unit, keyBusy: suspend () -> String?) {
        pending = Pending(closeSettings, selTrend, keyBusy)
        try { Ls.setItem(PICKING, System.currentTimeMillis().toString()) } catch (e: Exception) { }
        launchPick?.invoke()
    }

    /** `#file.onchange` / `cancel`: the picker came back (uri == null = nothing was chosen) */
    internal fun onPicked(uri: Uri?) {
        try { Ls.removeItem(PICKING) } catch (e: Exception) { }
        val p = pending ?: return   // the app was restarted while the picker was open: nothing to continue
        pending = null
        if (uri == null) return
        scope.launch { importFile(uri, p) }
    }

    /**
     * The "library.picking" marker: set when the picker opens, removed when it comes back. If it is still there (and fresh) when the app
     * starts, the phone closed the app while the picker was open. Checked once per app start, 3.8 s after the start.
     */
    internal suspend fun checkPicking() {
        if (checked) return
        checked = true
        val t = Ls.getItem(PICKING)?.trim()?.toDoubleOrNull() ?: 0.0
        if (t == 0.0 || t.isNaN()) return
        try { Ls.removeItem(PICKING) } catch (e: Exception) { }
        if (System.currentTimeMillis() - t < 180000) {
            delay(3800)
            ok = ImportOk(
                "Import did not finish",
                "The phone ran out of memory while the file picker was open, so the app was closed and restarted before the file could be read. Close other apps and try Import again."
            )
        }
    }

    /** the file is a zip (or something else) that is not a MovieVerse backup */
    private class NotBackup : Exception()

    private class Run(var total: Long) {
        val batch = ArrayList<JSONObject>()
        val extra = HashMap<String, String>()
        var add = 0
        var skip = 0
        var bad = 0
        var ix = 0L
        var first = true
        val ids = HashSet<String>()
        val base = System.currentTimeMillis()
        var read = 0L
    }

    /** counts the bytes that were read (the web version's `off`) */
    private class Counting(i: InputStream, val run: Run) : FilterInputStream(i) {
        override fun read(): Int {
            val r = super.read()
            if (r >= 0) run.read++
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = super.read(b, off, len)
            if (r > 0) run.read += r.toLong()
            return r
        }
    }

    /** importFile(f) */
    private suspend fun importFile(uri: Uri, p: Pending) {
        val r = Run(fileSize(uri).coerceAtLeast(1L))
        try {
            withContext(StateDispatcher) { for (m in AppData.movies.toTypedArray()) r.ids.add(m.id) }
            progress = ""                       // progOn('Importing backup')
            safetyCopy()
            progress = "0%"
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file")
            val found = input.use { raw ->
                val bin = BufferedInputStream(raw, 1 shl 16)
                if (isZip(bin)) importXlsx(bin, r) else importJson(bin, r)
            }
            finish(r, found, p)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotBackup) {
            progress = null
            p.closeSettings()
            toast.show("That is not a valid MovieVerse backup.")
        } catch (e: Exception) {
            try { AppData.render() } catch (x: Exception) { }
            progress = null
            p.closeSettings()
            ok = ImportOk(
                "Import stopped",
                r.add.toString() + " movies were imported before a problem happened. Your existing movies are safe, and you can run Import again — movies already added are skipped."
            )
        }
    }

    /** an .xlsx (and any zip) starts with the bytes "PK" 3 4 */
    private fun isZip(b: BufferedInputStream): Boolean {
        b.mark(16)
        val h = ByteArray(4)
        var n = 0
        while (n < 4) {
            val k = b.read(h, n, 4 - n)
            if (k < 0) break
            n += k
        }
        b.reset()
        return n == 4 && h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() && h[2] == 3.toByte() && h[3] == 4.toByte()
    }

    /** the old .json backup: read in pieces of 1M characters (mkScan). true = a movies array was found */
    private suspend fun importJson(raw: InputStream, r: Run): Boolean {
        val sc = BackupScan(
            onMovie = { t ->
                try { r.batch.add(JSONObject(t)) } catch (e: Exception) { r.bad++ }
            },
            onExtra = { k, t -> r.extra[k] = t }
        )
        val reader = InputStreamReader(Counting(raw, r), Charsets.UTF_8)
        val buf = CharArray(1 shl 20)
        withContext(Dispatchers.IO) {
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                var t = String(buf, 0, n)
                if (r.first) {
                    r.first = false
                    if (t.startsWith("﻿")) t = t.substring(1)
                }
                sc.feed(t)
                flush(r)
                val pct = Math.min(99L, Math.round(r.read.toDouble() / r.total * 100.0))
                progress = pct.toString() + "%  ·  " + (r.add + r.skip) + " movies"
                yield()
            }
        }
        return sc.found()
    }

    /**
     * The Excel backup: the picked file is copied to the cache folder (an .xlsx needs random access), the Movies sheet is read row by
     * row (200 rows per piece, same flush as the json), then Folders / Statuses / Settings. true = the Movies sheet was found.
     */
    private suspend fun importXlsx(bin: InputStream, r: Run): Boolean {
        val tmp = File.createTempFile("mv-import", ".xlsx", ctx.cacheDir)
        try {
            progress = "Reading file…"
            withContext(Dispatchers.IO) { tmp.outputStream().use { o -> bin.copyTo(o, 1 shl 16) } }
            progress = "0%"
            val xr = try {
                XlsxReader(tmp, ctx.cacheDir)
            } catch (e: java.util.zip.ZipException) {
                throw NotBackup()
            }
            try {
                if (!xr.hasMovies()) return false
                val rows = xr.movieRows()
                r.total = rows.total
                withContext(Dispatchers.IO) {
                    while (true) {
                        val o = rows.next() ?: break
                        r.batch.add(o)
                        if (r.batch.size >= 200) {
                            flush(r)
                            xlsxProgress(r, rows.bytes, rows.total)
                            yield()
                        }
                    }
                    flush(r)
                    xlsxProgress(r, rows.bytes, rows.total)
                }
                withContext(Dispatchers.IO) {
                    xr.recordsJson("folders")?.let { r.extra["folders"] = it }
                    xr.recordsJson("statuses")?.let { r.extra["statuses"] = it }
                    xr.settingsJson()?.let { r.extra["settings"] = it }
                }
                return true
            } finally {
                xr.close()
            }
        } finally {
            tmp.delete()
        }
    }

    private fun xlsxProgress(r: Run, bytes: Long, total: Long) {
        val pct = Math.min(99L, Math.round(bytes.toDouble() / total * 100.0))
        progress = pct.toString() + "%  ·  " + (r.add + r.skip) + " movies"
    }

    /** flush(): the movies of this piece are cleaned (normM), new ones are added to the library and saved */
    private suspend fun flush(r: Run) {
        val list = withContext(StateDispatcher) {
            val out = ArrayList<Movie>()
            for (o in r.batch) {
                var n: Movie? = null
                try { n = normM(o, r.base - (r.ix++)) } catch (e: Exception) { }
                if (n == null) { r.bad++; continue }
                if (r.ids.contains(n.id)) { r.skip++; continue }
                r.ids.add(n.id)
                out.add(n)
            }
            out
        }
        r.batch.clear()
        r.add += list.size
        if (list.isEmpty()) return
        // the list of movies is only ever changed on the main thread (the screens read it there)
        withContext(Dispatchers.Main) { AppData.movies.addAll(list) }
        withContext(StateDispatcher) { AppData.db.putMany(list) }
    }

    /** finish() */
    private suspend fun finish(r: Run, found: Boolean, p: Pending) {
        if (!found) {
            progress = null
            p.closeSettings()
            toast.show("That is not a valid MovieVerse backup.")
            return
        }
        fun parse(k: String): Any? = r.extra[k]?.let { try { JSONTokener(it).nextValue() } catch (e: Exception) { null } }
        val folders = parse("folders")
        val statuses = parse("statuses")
        val settings = parse("settings") as? JSONObject
        withContext(Dispatchers.Main) {
            try { if (settings != null) AppData.restoreSettings(settings) } catch (e: Exception) { }
            try {
                if (folders is JSONArray) {
                    for (i in 0 until folders.length()) {
                        val f = folders.opt(i)
                        if (f is JSONObject && Js.truthy(f.opt("id")) && AppData.folders.none { it.id == f.optString("id", "") }) {
                            AppData.folders.add(Folder(f))
                        }
                    }
                }
                if (statuses is JSONArray) {
                    for (i in 0 until statuses.length()) {
                        val x = statuses.opt(i)
                        if (x is JSONObject && Js.truthy(x.opt("id")) && AppData.statuses.none { it.id == x.optString("id", "") }) {
                            AppData.statuses.add(Status(x))
                        }
                    }
                }
                AppData.saveStatuses()
                AppData.rebuildSL()
                AppData.saveFolders()
            } catch (e: Exception) { }
        }
        val msg = r.add.toString() + " movies imported" +
            (if (r.skip != 0) ", " + r.skip + " already in your library" else "") +
            (if (r.bad != 0) ", " + r.bad + " could not be read" else "")
        var hasK = false
        val apis = settings?.opt("apis")
        if (apis is JSONArray) {
            for (i in 0 until apis.length()) {
                val a = apis.opt(i)
                if (a is JSONObject) {
                    val ks = a.opt("keys")
                    if (ks is JSONArray && (0 until ks.length()).any { Js.truthy(ks.opt(it)) }) hasK = true
                }
            }
        }
        AppData.render()
        progress = null
        p.closeSettings()
        if (!hasK) {
            ok = ImportOk("Import successful", msg)
            return
        }
        p.selTrend()
        val err = p.keyBusy()
        ok = ImportOk("Import successful", msg)
        if (err != null) toast.show(err, 4000L)
    }

    // ---- normM / normStatus ------------------------------------------------------------------------------------------------

    /** String(x) of a JSON value */
    private fun jsStr(v: Any?): String = when (v) {
        null -> "null"
        JSONObject.NULL -> "null"
        is String -> v
        is Number -> jsNumToString(v.toDouble())
        is Boolean -> v.toString()
        is JSONArray -> (0 until v.length()).joinToString(",") { if (v.isNull(it)) "" else jsStr(v.get(it)) }
        else -> "[object Object]"
    }

    /** normM(m, ts): a movie of the file -> a clean movie, or null when it has no title */
    private fun normM(m: JSONObject, ts: Long): Movie? {
        val t = Js.or(m.opt("title"), m.opt("name"))
        if (!Js.truthy(t)) return null
        m.put("title", jsStr(t))
        val idv = m.opt("id")
        val id = if (idv != null && idv != JSONObject.NULL && !(idv is String && idv.isEmpty())) jsStr(idv) else AppData.uid()
        m.put("id", id)
        val yv = m.opt("year")
        m.put("year", if (yv == null || yv == JSONObject.NULL) "" else jsStr(yv))
        val ad = m.opt("added")
        if (!(ad is Number && !ad.toDouble().isNaN())) m.put("added", ts)
        m.put("status", normStatus(m.opt("status")))
        val g = m.opt("genres")
        if (Js.truthy(g) && g !is JSONArray) m.remove("genres")
        AppData.db.undelete(id)
        return Movie(m)
    }

    private val ALIAS = mapOf(
        "watch" to "watching", "current" to "watching", "currentlywatching" to "watching",
        "planned" to "plan", "plantowatch" to "plan", "towatch" to "plan", "watchlist" to "plan", "want" to "plan", "wishlist" to "plan",
        "completed" to "watched", "complete" to "watched", "done" to "watched", "seen" to "watched", "finished" to "watched",
        "favorite" to "favourite", "favorites" to "favourite", "favourites" to "favourite", "fav" to "favourite",
        "soon" to "upcoming", "comingsoon" to "upcoming"
    )
    private val NOT_AZ = Regex("[^a-z]")

    /** normStatus(v) */
    private fun normStatus(v: Any?): String {
        val k = if (v == null || v == JSONObject.NULL) "" else jsStr(v)
        val sts = AppData.statuses
        if (sts.any { it.id == k }) return k
        val n = k.lowercase().replace(NOT_AZ, "")
        val byName = sts.firstOrNull { it.name.lowercase().replace(NOT_AZ, "") == n }
        if (byName != null) return byName.id
        val al = ALIAS[n]
        if (al != null && sts.any { it.id == al }) return al
        return if (sts.any { it.id == "plan" }) "plan" else sts[0].id
    }

    // ---- safetyCopy ----------------------------------------------------------------------------------------------------------

    /** safetyCopy(): before an import the current library is saved to Documents/MovieVerse/before-last-import.json (a failure is ignored) */
    private suspend fun safetyCopy() {
        if (AppData.movies.isEmpty()) return
        progress = "Saving a safety copy…"
        try {
            askStorage()
            val total = AppData.movies.size
            withContext(Dispatchers.IO) {
                openSafety().bufferedWriter(Charsets.UTF_8).use { w ->
                    Backup.write(w) { i -> progress = "Safety copy $i / $total" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // .catch(function(){}) : the import goes on without a safety copy
        }
    }

    /** Android 9 and older need the storage permission for Documents (like the export) */
    private suspend fun askStorage() {
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            val d = CompletableDeferred<Boolean>()
            permDone = d
            launchPerm?.invoke(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            d.await()
        }
    }

    /** the safety file is replaced every time (writeFile of Capacitor overwrites) */
    private fun openSafety(): OutputStream {
        val name = "before-last-import.json"
        if (Build.VERSION.SDK_INT >= 29) {
            val res = ctx.contentResolver
            val coll = MediaStore.Files.getContentUri("external")
            val old: Uri? = try {
                res.query(
                    coll, arrayOf(MediaStore.MediaColumns._ID),
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    arrayOf(name, "Documents/MovieVerse/"), null
                )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(coll, c.getLong(0)) else null }
            } catch (e: Exception) {
                null
            }
            if (old != null) {
                try {
                    val o = res.openOutputStream(old, "wt")
                    if (o != null) return o
                } catch (e: Exception) { }
            }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/MovieVerse")
            }
            val nu = res.insert(coll, v) ?: throw IOException("Could not create the file")
            return res.openOutputStream(nu) ?: throw IOException("Could not open the file")
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MovieVerse")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create the folder")
        return File(dir, name).outputStream()
    }

    private fun fileSize(uri: Uri): Long {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        } catch (e: Exception) { }
        return 0L
    }

    companion object {
        private const val PICKING = "library.picking"
        private var checked = false
    }
}

@Composable
fun rememberImportFlow(): ImportFlow {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val flow = remember { ImportFlow(ctx.applicationContext, scope, toast) }
    val pickL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        flow.onPicked(uri)
    }
    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flow.permDone?.complete(granted)
    }
    SideEffect {
        flow.launchPick = { pickL.launch(arrayOf("*/*")) }
        flow.launchPerm = { permL.launch(it) }
    }
    LaunchedEffect(Unit) { flow.checkPicking() }
    return flow
}

/** the progress dialog and the green OK dialog of the import (own composable, so the caption updates do not redraw the whole page) */
@Composable
fun ImportDialogs(flow: ImportFlow) {
    flow.progress?.let { MvApiLoadingDialog(caption = it, title = "Importing backup") }
    flow.ok?.let { MvOkDialog(title = it.title, message = it.message, onOk = { flow.dismissOk() }) }
}
