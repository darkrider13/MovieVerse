package com.trackerxx.movieverse.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Backup
import com.trackerxx.movieverse.data.XlsxBackup
import com.trackerxx.movieverse.data.XlsxCodec
import com.trackerxx.movieverse.ui.components.LocalModal
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.ModalResult
import com.trackerxx.movieverse.ui.components.ModalSpec
import com.trackerxx.movieverse.ui.components.ModalState
import com.trackerxx.movieverse.ui.components.ToastState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** the green "Export successful" dialog: message + optional path line */
class ExportOk(val message: String, val code: String?)

/**
 * Settings > Export (`exportBackup`, `writeBackup`, `shareBk`, `bkPg` of the web version, native branch).
 * Question "Export backup" -> Save File (Documents/MovieVerse/<name>) or Share (Android share sheet).
 * If saving to Documents fails, the share sheet is used instead (like the web version's .catch).
 */
@Stable
class ExportFlow(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val modal: ModalState,
    private val toast: ToastState
) {
    /** "Exporting backup" dialog: null = hidden, otherwise the caption ("i / N movies") */
    var progress by mutableStateOf<String?>(null)
        private set

    /** "Export successful" dialog */
    var ok by mutableStateOf<ExportOk?>(null)
        private set

    fun dismissOk() {
        ok = null
    }

    // set by rememberExportFlow (activity result launchers)
    internal var launchShare: ((Intent) -> Unit)? = null
    internal var launchPerm: ((String) -> Unit)? = null
    internal var shareDone: CompletableDeferred<Unit>? = null
    internal var permDone: CompletableDeferred<Boolean>? = null

    /** the Export button. closeSettings = `$('#sdlg').close()` (done only after a choice was made). */
    fun start(closeSettings: () -> Unit) {
        AppData.beginExport()
        val name = Backup.fileName()
        scope.launch {
            val r = modal.ask(
                ModalSpec(
                    title = "Export backup",
                    text = "Share your backup or save to Documents / MovieVerse",
                    ok = "Save File", alt = "Share", plain = true, red = true
                )
            )
            if (r is ModalResult.Cancel) return@launch
            closeSettings()
            if (r is ModalResult.Delete) {
                share(name)
                return@launch
            }
            progOn()
            val t0 = System.currentTimeMillis()
            try {
                askStorage()
                saveToDocuments(name)
                // the loading stays for at least 4 seconds, then the popup comes
                val left = 4000L - (System.currentTimeMillis() - t0)
                if (left > 0) delay(left)
                progress = null
                AppData.markExported()
                ok = ExportOk("Saved to", "Documents/MovieVerse/$name")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress = null
                share(name)
            }
        }
    }

    private fun progOn() {
        progress = ""
        bkPg(0)
    }

    /** bkPg(i) */
    private fun bkPg(i: Int) {
        progress = i.toString() + " / " + AppData.movies.size + " movies"
    }

    /** the Excel file is streamed (a movie at a time, 500 movies per caption update), so a big library never sits in memory */
    private suspend fun write(out: OutputStream) = withContext(Dispatchers.IO) {
        out.use { o -> XlsxBackup.write(o) { i -> bkPg(i) } }
    }

    /** Android 9 and older need the storage permission for Documents (Capacitor asked for it too); a "no" just ends in the share fallback */
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

    private suspend fun saveToDocuments(name: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, XlsxCodec.MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/MovieVerse")
            }
            val res = ctx.contentResolver
            val uri = res.insert(MediaStore.Files.getContentUri("external"), v) ?: throw IOException("Could not create the file")
            try {
                val out = res.openOutputStream(uri) ?: throw IOException("Could not open the file")
                write(out)
            } catch (e: Exception) {
                try { res.delete(uri, null, null) } catch (x: Exception) { }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MovieVerse")
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create the folder")
            write(File(dir, name).outputStream())
        }
    }

    /** shareBk(): file in the cache folder -> Android share sheet */
    private suspend fun share(name: String) {
        progOn()
        try {
            val f = File(ctx.cacheDir, name)
            withContext(Dispatchers.IO) { write(f.outputStream()) }
            progress = null
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            if (chooser(uri, name)) {
                AppData.markExported()
                ok = ExportOk("Backup shared", null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progress = null
            toast.show("Could not share backup")
        }
    }

    /** opens the share sheet; true = a target was chosen, false = the sheet was closed without choosing */
    private suspend fun chooser(uri: android.net.Uri, name: String): Boolean {
        val action = ctx.packageName + ".SHARE_CHOSEN"
        val chosen = AtomicBoolean(false)
        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                chosen.set(true)
            }
        }
        ContextCompat.registerReceiver(ctx, rcv, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(action).setPackage(ctx.packageName), flags)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = XlsxCodec.MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val ch = Intent.createChooser(send, "Save backup", pi.intentSender)
            val d = CompletableDeferred<Unit>()
            shareDone = d
            launchShare?.invoke(ch)
            d.await()
            delay(500)   // the "chosen" broadcast arrives right around the sheet closing
            return chosen.get()
        } finally {
            try { ctx.unregisterReceiver(rcv) } catch (e: Exception) { }
        }
    }
}

@Composable
fun rememberExportFlow(): ExportFlow {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val modal = LocalModal.current
    val toast = LocalToast.current
    val flow = remember { ExportFlow(ctx.applicationContext, scope, modal, toast) }
    val shareL = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.shareDone?.complete(Unit)
    }
    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flow.permDone?.complete(granted)
    }
    SideEffect {
        flow.launchShare = { shareL.launch(it) }
        flow.launchPerm = { permL.launch(it) }
    }
    return flow
}
