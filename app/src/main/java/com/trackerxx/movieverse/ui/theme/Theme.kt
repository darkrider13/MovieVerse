package com.trackerxx.movieverse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalMv = staticCompositionLocalOf<MvColors> { DarkColors }

/** Use as `Mv.c.ink`, `Mv.c.acc` ... inside composables. */
object Mv {
    val c: MvColors
        @Composable @ReadOnlyComposable get() = LocalMv.current
}

/** Current theme (dark by default). onChange saves it to localStorage "library.theme" (see AppData). */
@Stable
class ThemeState(initialDark: Boolean = true, private val onChange: ((Boolean) -> Unit)? = null) {
    var dark by mutableStateOf(initialDark)
        private set

    /** setTheme(t) */
    fun set(value: Boolean) {
        dark = value
        onChange?.invoke(value)
    }

    fun toggle() = set(!dark)
}

val LocalThemeState = staticCompositionLocalOf { ThemeState() }

@Composable
fun MovieVerseTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val colors = if (themeState.dark) DarkColors else LightColors
    val selection = TextSelectionColors(
        handleColor = colors.ink,
        backgroundColor = if (themeState.dark) Color(1f, 1f, 1f, .28f) else Color(0f, 0f, 0f, .18f)
    )
    SystemBarIcons(dark = themeState.dark)
    CompositionLocalProvider(
        LocalMv provides colors,
        LocalThemeState provides themeState,
        LocalMvText provides MvTextDefaults(15.sp, FontWeight.Normal, colors.ink),
        LocalTextSelectionColors provides selection
    ) {
        content()
    }
}

/** paintBar(): dark theme = light icons on black bars, light theme = dark icons on light bars. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
        }
    }
}
