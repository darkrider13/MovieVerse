package com.trackerxx.movieverse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
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

@Composable
fun MovieVerseTheme(content: @Composable () -> Unit) {
    val colors = DarkColors
    val selection = TextSelectionColors(
        handleColor = colors.ink,
        backgroundColor = Color(1f, 1f, 1f, .28f)
    )
    SystemBarIcons()
    CompositionLocalProvider(
        LocalMv provides colors,
        LocalMvText provides MvTextDefaults(15.sp, FontWeight.Normal, colors.ink),
        LocalTextSelectionColors provides selection
    ) {
        content()
    }
}

/** paintBar(): light icons on the black bars. */
@Composable
private fun SystemBarIcons() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = false
            ctrl.isAppearanceLightNavigationBars = false
        }
    }
}
