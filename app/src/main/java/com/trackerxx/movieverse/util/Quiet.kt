package com.trackerxx.movieverse.util

import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * "Keep the screen smooth first": background work (rating look-ups, poster / rating refresh of the library, Trending refresh)
 * waits here while the page is being scrolled, and for a short time after the app starts or the scrolling stops.
 * Without this all of it ran together with the first scroll after the app opened and fought with the UI for the CPU.
 */
object Quiet {
    /** the list is being scrolled right now (set by the home screen) */
    @Volatile var scrolling = false

    /** no background work before this moment (SystemClock.uptimeMillis) */
    @Volatile var untilMs = 0L

    /** call when the finger scrolls / stops: after a scroll the background work waits a little longer */
    fun scroll(on: Boolean) {
        scrolling = on
        if (!on) untilMs = maxOf(untilMs, SystemClock.uptimeMillis() + 1500L)
    }

    /** suspends until the UI is quiet */
    suspend fun await() {
        while (scrolling || SystemClock.uptimeMillis() < untilMs) delay(250)
    }
}
