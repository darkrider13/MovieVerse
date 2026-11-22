package com.trackerxx.movieverse.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.trackerxx.movieverse.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Settings > Update. The GitHub workflow publishes every build as the release "latest" (MovieVerse.apk) and writes the
 * build's versionCode into the release notes. The app reads that release, compares the number with its own versionCode,
 * downloads the APK (with progress) and opens the Android installer. Same package + same signing key = installs over the app.
 */
object Updater {
    private const val UA = "MovieVerse/1.0 (Android)"

    /** GitHub project the updates come from: https://github.com/darkrider13/MovieVerse-V2 */
    private const val REPO = "darkrider13/MovieVerse-V2"

    class Info(val versionCode: Long, val url: String, val size: Long)

    /** versionCode of the installed app */
    fun installedCode(ctx: Context): Long {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
    }

    /** the newest published build, or null when the installed app is already that build */
    suspend fun check(ctx: Context): Info? {
        val r = Http.get(
            "https://api.github.com/repos/$REPO/releases/tags/latest",
            mapOf("Accept" to "application/vnd.github+json"), 20000, 20000
        )
        if (r.status == 404) throw IOException("No update has been published yet.")
        if (!r.ok) throw IOException("GitHub answered " + r.status + ".")
        val j = JSONObject(r.body)
        val assets = j.optJSONArray("assets") ?: throw IOException("The release has no APK.")
        var url: String? = null
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", true)) {
                url = a.optString("browser_download_url")
                size = a.optLong("size", 0L)
                break
            }
        }
        if (url.isNullOrEmpty()) throw IOException("The release has no APK.")
        val vc = Regex("versionCode:\\s*(\\d+)").find(j.optString("body"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        // vc == 0: an older release without the number -> offer it anyway
        if (vc != 0L && vc <= installedCode(ctx)) return null
        return Info(vc, url, size)
    }

    /** downloads the APK into the app cache; onProgress(done, total) (total = 0 when unknown). Cancel = cancel the coroutine. */
    suspend fun download(ctx: Context, info: Info, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "MovieVerse.apk.part")
        val out = File(dir, "MovieVerse.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        var ok = false
        try {
            c.connectTimeout = 30000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode !in 200..299) throw IOException("Server answered " + c.responseCode + ".")
            val total = c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: info.size
            c.inputStream.use { inp ->
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    onProgress(0L, total)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        ensureActive()
                        val now = System.nanoTime()
                        if (now - last > 80_000_000L) {
                            last = now
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                    if (total > 0 && done != total) throw IOException("The download was cut off. Try again.")
                }
            }
            if (!part.renameTo(out)) throw IOException("Could not save the file.")
            ok = true
            out
        } finally {
            c.disconnect()
            if (!ok) part.delete()
        }
    }

    enum class InstallResult { Started, NeedPermission }

    /** opens the Android installer; if "install unknown apps" is off for MovieVerse, opens that setting instead */
    fun install(ctx: Context, apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return InstallResult.NeedPermission
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return InstallResult.Started
    }
}
