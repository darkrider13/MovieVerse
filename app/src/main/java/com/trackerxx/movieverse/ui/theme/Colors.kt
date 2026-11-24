package com.trackerxx.movieverse.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

/** Same names as the CSS variables of the web version (:root; the app is dark only). */
@Immutable
class MvColors(
    val bg: Color,        // --bg
    val bot: Color,       // --bot  (bottom bar / toast)
    val nav: Color,       // --nav  (top bar)
    val s: Color,         // --s    (surface)
    val s2: Color,        // --s2   (stronger surface)
    val ink: Color,       // --ink  (text)
    val mut: Color,       // --mut  (muted text)
    val line: Color,      // --line (borders)
    val acc: Color,       // --acc  (accent red)
    val onAcc: Color,     // --onacc
    val w: Color,         // --w    (watched / green)
    val p: Color,         // --p    (plan / amber)
    val n: Color,         // --n    (watching / parrot green)
    val dialogBg: Color,  // final dialog background
    val cg: Color,        // .cg
    val cy: Color,        // .cy
    val cr: Color         // .cr
)

val DarkColors = MvColors(
    bg = Color(0xFF000000), bot = Color(0xFF000000), nav = Color(0xFF000000),
    s = rgba(255, 255, 255, .07f), s2 = rgba(255, 255, 255, .10f),
    ink = Color(0xFFF1F2FC), mut = Color(0xFFA3A9C9), line = rgba(255, 255, 255, .11f),
    acc = Color(0xFFFF1A1A), onAcc = Color(0xFFFFFFFF),
    w = Color(0xFF00B800), p = Color(0xFFE8A33D), n = Color(0xFF6CC644),
    dialogBg = Color(0xFF000000),
    cg = Color(0xFF00B800), cy = Color(0xFFF2C230), cr = Color(0xFFFF6B6B)
)

/** Fixed colors (not part of the palette). */
object MvFixed {
    val Del = Color(0xFFE24B4B)                 // .btn.del
    val RedBtn = Color(0xFFFF0000)              // .btn.red
    val OkGreen = Color(0xFF16A34A)             // #okdlg .okc
    val GlassBorder = Color(1f, 1f, 1f, .22f)   // dialog + toast border
    val Placeholder = Color(0xFF757575)         // browser default placeholder
    val RateBg = rgba(8, 10, 20, .78f)          // .rate / .s-up
    val RateText = Color(0xFFFFC857)
}

// CSS "ease" and the other curves used by the web version
val EaseCss = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
