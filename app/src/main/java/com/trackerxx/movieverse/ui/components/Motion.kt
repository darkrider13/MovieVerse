package com.trackerxx.movieverse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** keys that already played their entrance (so scrolling back never replays it) */
private val seenKeys = HashSet<Any>()

/**
 * Light entrance while scrolling: a card fades in and rises 12dp (240 ms). It plays ONCE per card (the first time
 * the card is ever shown), so scrolling up and down again stays smooth and nothing re-animates.
 * Only graphicsLayer is animated (draw phase), so there is no recomposition while it runs.
 */
@Composable
fun Modifier.riseIn(key: Any, delayMs: Int = 0): Modifier {
    val fresh = remember(key) { seenKeys.add(key) }
    val p = remember(key) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(key) {
        if (fresh) {
            if (delayMs > 0) delay(delayMs.toLong())
            p.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    return this.graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * rise
    }
}

/**
 * The ONE "light switch animation" of the page headers: fades in and rises 12dp over 280 ms (FastOutSlowIn). Instant hide when [shown] turns false.
 * Your Library (tabs / search / filters) and Releases (search / mode buttons / filter pills) both use it, so they can never drift apart.
 * Draw phase only (graphicsLayer): it never changes the layout size.
 */
@Composable
fun Modifier.switchEnter(shown: Boolean): Modifier {
    val dens = LocalDensity.current.density
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) tween(280, easing = FastOutSlowInEasing) else snap(),
        label = "switchEnter"
    )
    return this.graphicsLayer { alpha = a; translationY = (1f - a) * 12f * dens }
}

/** true one frame after the composable first appears: gives [switchEnter] a real 0 -> 1 run when a header is composed fresh (Releases) */
@Composable
fun rememberEntered(): Boolean {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    return entered
}
