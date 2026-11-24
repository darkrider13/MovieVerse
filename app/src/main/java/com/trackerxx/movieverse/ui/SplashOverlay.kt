package com.trackerxx.movieverse.ui

import android.os.SystemClock
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Splash is shown for exactly this long (ms), counted from the moment the intro animation starts. */
const val SPLASH_MS = 2200L

private val InkColor = Color(0xFFF1F2FC)
private val MutedColor = Color(0xFFA3A9C9)
private val AccentColor = Color(0xFFFF1A1A)

@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    // static splash: logo + name + tagline are simply there (no fade, no scale, no slide), then it is removed
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        withFrameNanos { }
        val t0 = SystemClock.uptimeMillis()
        delay((SPLASH_MS - (SystemClock.uptimeMillis() - t0)).coerceAtLeast(0L))
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )

        val name: AnnotatedString = buildAnnotatedString {
            append("Movie")
            withStyle(SpanStyle(color = AccentColor)) { append("Verse") }
        }
        BasicText(
            text = name,
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
            modifier = Modifier.padding(horizontal = 24.dp),
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
