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
}
