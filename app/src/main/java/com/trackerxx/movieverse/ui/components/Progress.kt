package com.trackerxx.movieverse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvText

/**
 * The CSS "border + border-top-color + rotate" spinner (@keyframes ptrs).
 * .lds   = 18dp, border 2, base --mut, top transparent, .8s
 * .aps   = 46dp, border 4, base --line, top --acc, .8s
 */
@Composable
fun MvSpinner(
    diameter: Dp,
    stroke: Dp,
    base: Color,
    top: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = 800
) {
    val tr = rememberInfiniteTransition(label = "spin")
    val angle = tr.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle"
    )
    Canvas(
        modifier
            .size(diameter)
            .graphicsLayer { rotationZ = angle.value }
    ) {
        val sw = stroke.toPx()
        val tl = Offset(sw / 2f, sw / 2f)
        val d = Size(this.size.width - sw, this.size.height - sw)
        if (base.alpha > 0f) {
            drawArc(base, startAngle = -45f, sweepAngle = 270f, useCenter = false, topLeft = tl, size = d, style = Stroke(sw))
        }
        if (top.alpha > 0f) {
            drawArc(top, startAngle = -135f, sweepAngle = 90f, useCenter = false, topLeft = tl, size = d, style = Stroke(sw))
        }
    }
}

/** .lds small inline spinner */
@Composable
fun MvSmallSpinner(modifier: Modifier = Modifier) {
    MvSpinner(18.dp, 2.dp, Mv.c.mut, Color.Transparent, modifier)
}

// @keyframes apdot{0%,80%,100%{opacity:.2}40%{opacity:1}} with CSS "ease" between the keys
private fun dotAlpha(phase: Float): Float = when {
    phase < 0.4f -> 0.2f + 0.8f * EaseCss.transform(phase / 0.4f)
    phase < 0.8f -> 1f - 0.8f * EaseCss.transform((phase - 0.4f) / 0.4f)
    else -> 0.2f
}

/** Three pulsing dots "...", 1.2s loop, each dot .2s later than the one before. */
@Composable
fun MvLoadingDots(
    modifier: Modifier = Modifier,
    size: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    color: Color = Color.Unspecified,
    lineHeight: Float = 1.45f,
    gap: Dp = 0.dp
) {
    val tr = rememberInfiniteTransition(label = "dots")
    val t = tr.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dotsT"
    )
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            MvText(
                ".",
                size = size, weight = weight, color = color, lineHeight = lineHeight,
                modifier = Modifier.padding(horizontal = gap).graphicsLayer {
                    val p = ((((t.value - i * 200f) % 1200f) + 1200f) % 1200f) / 1200f
                    alpha = dotAlpha(p)
                }
            )
        }
    }
}
