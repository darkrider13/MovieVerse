package com.trackerxx.movieverse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.ui.library.HomeScreen
import com.trackerxx.movieverse.ui.components.LocalModal
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.ModalHost
import com.trackerxx.movieverse.ui.components.ModalState
import com.trackerxx.movieverse.ui.components.ToastBridge
import com.trackerxx.movieverse.ui.components.ToastHost
import com.trackerxx.movieverse.ui.components.rememberToastState
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MovieVerseTheme

/** Root of the app. Later steps put the Library / Trending / Releases screens here. */
@Composable
fun MovieVerseApp() {
    // theme comes from storage ("library.theme", dark by default) and is saved when it changes
    val themeState = AppData.theme
    var showSplash by remember { mutableStateOf(true) }

    MovieVerseTheme(themeState) {
        val c = Mv.c
        val toast = rememberToastState()
        val modal = remember { ModalState() }
        SideEffect { ToastBridge.state = toast }
        CompositionLocalProvider(LocalToast provides toast, LocalModal provides modal) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                // page area = the screen minus status bar / navigation bar (like the old non-overlay WebView)
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                ) {
                    HomeScreen(splashGone = !showSplash)
                }

                // StatusBar.setBackgroundColor(--bg) and the body::after bottom strip (--bot)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(c.nav)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(c.bot)
                )

                ModalHost(modal)
                ToastHost(toast)

                if (showSplash) {
                    SplashOverlay(onFinished = { showSplash = false })
                }
            }
        }
    }
}
