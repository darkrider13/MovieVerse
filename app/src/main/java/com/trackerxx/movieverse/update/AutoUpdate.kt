package com.trackerxx.movieverse.update

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * The automatic "Update available" popup (same idea as the HTML app): MainActivity calls check() a few seconds after the
 * app starts, every time the app comes back to the front, and every 10 minutes while it stays open. When the newest
 * GitHub build is newer than the installed one, [offer] gets the build and MovieVerseApp shows the update popup.
 * "Later" hides that build until the next app start (it is not offered again and again).
 */
object AutoUpdate {
    /** the build the popup is offering right now (null = no popup) */
    var offer by mutableStateOf<Updater.Info?>(null)
        private set

    private var skip = 0L      // build number the person already answered "Later" to
    private var busy = false
    private var last = 0L

    suspend fun check(ctx: Context, force: Boolean) {
        if (busy || offer != null) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - last < 60_000L) return
        busy = true
        last = now
        try {
            val info = Updater.check(ctx, auto = true)
            if (info != null && info.versionCode != skip) offer = info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // silent: no internet / GitHub busy -> try again at the next check
        } finally {
            busy = false
        }
    }

    /** "Later" / Close / Cancel on the popup */
    fun later(info: Updater.Info) {
        skip = info.versionCode
        offer = null
    }
}
