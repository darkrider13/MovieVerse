package com.trackerxx.movieverse.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** `.card.chg:not(.upd) .poster{animation:pulsep 1.6s ease-in-out infinite}` : the card breathes between 100% and 92% */
fun Modifier.pulse(active: Boolean): Modifier = if (!active) this else composed {
    val tr = rememberInfiniteTransition(label = "pulsep")
    val s = tr.animateFloat(
        initialValue = 1f, targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulsep-s"
    )
    graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

/**
 * The pull-down gesture of the web version (touchstart / touchmove / touchend after `paintDirty()`):
 *  - starts only with one finger, when the page is at the very top, nothing is being dragged, and the touch is not on the tabs / search row
 *  - pull = min(finger travel * 0.5, 90); a move that goes up or mostly sideways resets it
 *  - at 70 the phone ticks once (onBuzz); letting go at 70 or more calls onPull
 * The events are only watched (never consumed), so taps, scrolling and drag-reorder keep working.
 */
fun Modifier.pullRefresh(
    grid: LazyGridState,
    enabled: () -> Boolean,
    dragging: () -> Boolean,
    onBuzz: () -> Unit,
    onPull: () -> Unit
): Modifier = pointerInput(Unit) {
    val th = 70f
    fun atTop() = grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled() || !atTop()) return@awaitEachGesture
        // '#tabs,input,textarea,select' : the tabs / search row of Your Library
        val bar = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "bar" }
        if (bar != null && down.position.y >= bar.offset.y && down.position.y <= bar.offset.y + bar.size.height) return@awaitEachGesture
        val density = this.density
        val sx = down.position.x
        val sy = down.position.y
        var pull = 0f
        var buzz = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size > 1 && ev.changes.count { it.pressed } > 1) return@awaitEachGesture
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!ch.pressed) {
                if (pull >= th) onPull()
                return@awaitEachGesture
            }
            if (!atTop() || dragging()) return@awaitEachGesture
            val dy = (ch.position.y - sy) / density
            val dx = (ch.position.x - sx) / density
            if (dy <= 0f || Math.abs(dx) > Math.abs(dy)) {
                pull = 0f
                continue
            }
            pull = Math.min(dy * 0.5f, 90f)
            if (pull >= th && !buzz) {
                buzz = true
                onBuzz()
            }
        }
    }
}
