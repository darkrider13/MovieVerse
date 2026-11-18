package com.trackerxx.movieverse.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Splash is shown for exactly this long (ms), counted from the moment the intro animation starts. */
const val SPLASH_MS = 2200L

// same curves as the web version's CSS
private val EaseOutCubicCss = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseOutExpoCss = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val EaseCss = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private val InkColor = Color(0xFFF1F2FC)
private val MutedColor = Color(0xFFA3A9C9)
private val AccentColor = Color(0xFFFF1A1A)

@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.88f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(12f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // wait for the first frames to be painted, then start the intro
        withFrameNanos { }
        withFrameNanos { }
        withFrameNanos { }
        val t0 = SystemClock.uptimeMillis()

        // logo: opacity .6s, scale .8s
        launch { logoAlpha.animateTo(1f, tween(600, easing = EaseOutCubicCss)) }
        launch { logoScale.animateTo(1f, tween(800, easing = EaseOutExpoCss)) }
        // name + tagline: .55s delay, opacity .5s, slide .7s
        launch { textAlpha.animateTo(1f, tween(500, delayMillis = 550, easing = EaseOutCubicCss)) }
        launch { textShift.animateTo(0f, tween(700, delayMillis = 550, easing = EaseOutExpoCss)) }

        delay((SPLASH_MS - (SystemClock.uptimeMillis() - t0)).coerceAtLeast(0L))
        // fade out .4s, then remove
        overlayAlpha.animateTo(0f, tween(400, easing = EaseCss))
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color.Black)
            .pointerInput(Unit) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                }
        )

        val name: AnnotatedString = buildAnnotatedString {
            append("Movie")
            withStyle(SpanStyle(color = AccentColor)) { append("Verse") }
        }
        BasicText(
            text = name,
            modifier = Modifier.graphicsLayer {
                alpha = textAlpha.value
                translationY = textShift.value * density
            },
            style = TextStyle(
                color = InkColor,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.03).em
            )
        )

        BasicText(
            text = "Every story starts with a movie",
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textShift.value * density
                },
            style = TextStyle(
                color = MutedColor,
                fontSize = 16.sp,
                lineHeight = 19.2.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.02.em,
                textAlign = TextAlign.Center
            )
        )
    }
}
