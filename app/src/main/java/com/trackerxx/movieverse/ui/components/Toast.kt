package com.trackerxx.movieverse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** one open window above the library (every dialog / page is its own Android window): popup = a dialog, otherwise a full page */
class WinLayer(val popup: Boolean)

/** toast(text, ms): shows the text for ms (default 2600) and fades out. A new toast replaces the old one. */
@Stable
class ToastState(private val scope: CoroutineScope) {
    var text by mutableStateOf("")
        private set
    var visible by mutableStateOf(false)
        private set
    private var job: Job? = null

    /** number of open dialogs (each dialog is its own window; the toast is drawn in the top one) */
    var dialogs by mutableIntStateOf(0)

    /** the open windows above the library, bottom to top (the library itself is the window below all of them) */
    private val layers = mutableStateListOf<WinLayer>()

    fun addLayer(l: WinLayer) { layers.add(l) }

    fun removeLayer(l: WinLayer) { layers.remove(l) }

    /**
     * Must the window [l] (null = the library) be blurred? Yes while a popup is open above it and nothing opaque is in between.
     * A popup blurs EVERYTHING visible behind it, including a full page (movie details) and a popup under another popup, like the
     * web version's dialog::backdrop. A full page above a window hides it completely, so that window needs no blur.
     * Reads snapshot state: call it inside a graphicsLayer block, then the blur changes without any recomposition.
     */
    fun covered(l: WinLayer?): Boolean {
        val i = if (l == null) -1 else layers.indexOf(l)
        if (l != null && i < 0) return false
        var popup = false
        for (k in i + 1 until layers.size) {
            if (!layers[k].popup) return false
            popup = true
        }
        return popup
    }

    fun show(message: String, ms: Long = 2600L) {
        job?.cancel()
        text = message
        visible = true
        job = scope.launch {
            delay(ms)
            visible = false
        }
    }
}

val LocalToast = staticCompositionLocalOf<ToastState> { error("ToastState not provided") }

@Composable
fun rememberToastState(): ToastState {
    val scope = rememberCoroutineScope()
    return remember { ToastState(scope) }
}

/** #toast : centered above the bottom, --bot background, glass border, radius 12, bold text, fade .2s. */
@Composable
fun ToastHost(state: ToastState, inDialog: Boolean = false, visibleHere: Boolean = true) {
    val c = Mv.c
    // while a dialog is open the toast is drawn by that dialog instead (see MvDialog)
    val hidden = if (inDialog) !visibleHere else state.dialogs > 0
    val a by animateFloatAsState(
        targetValue = if (state.visible) 1f else 0f,
        animationSpec = tween(200, easing = EaseCss),
        label = "toastAlpha"
    )
    if (a > 0f && !hidden) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .widthIn(max = this.maxWidth * 0.9f)
                    .graphicsLayer { alpha = a }
                    .shadow(
                        elevation = 20.dp, shape = shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(shape)
                    .background(c.bot)
                    .border(1.dp, MvFixed.GlassBorder, shape)
                    .padding(horizontal = 17.dp, vertical = 11.dp)
            ) {
                MvText(state.text, weight = FontWeight.SemiBold, align = TextAlign.Center)
            }
        }
    }
}

/** lets code outside the screens (loaders) show a toast */
object ToastBridge {
    @Volatile
    var state: ToastState? = null

    fun show(message: String, ms: Long = 2600L) {
        state?.show(message, ms)
    }
}
