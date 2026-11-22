package com.trackerxx.movieverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.ui.MovieVerseApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // system splash is plain black (see themes.xml); our own logo splash is drawn by Compose
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // black from the very first frame: no grey flash
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        // bars are transparent; the app paints them itself (top = --nav, bottom = --bot) and sets icon colours per theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { MovieVerseApp() }
        useHighestRefreshRate()

        // backfill(): when the app comes to the front (after 0.4 s) and then every 30 minutes while it is visible
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(400)
                Enrich.backfill()
                while (true) {
                    delay(1_800_000L)
                    Enrich.backfill()
                }
            }
        }
    }

    /** Many phones keep apps at 60 Hz. Pick the highest refresh rate the screen offers (same resolution) = "buttery" scrolling. */
    @Suppress("DEPRECATION")
    private fun useHighestRefreshRate() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return
            val d = windowManager.defaultDisplay
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            window.attributes = lp
        } catch (e: Exception) { }
    }
}
