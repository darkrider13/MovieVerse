package com.trackerxx.movieverse.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding

/** Like CSS inheritance: body is 15px / 1.45 system font, buttons and dialogs override it. */
@Immutable
class MvTextDefaults(val size: TextUnit, val weight: FontWeight, val color: Color)

val LocalMvText = compositionLocalOf { MvTextDefaults(15.sp, FontWeight.Normal, Color.White) }

private val CssLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

fun mvTextStyle(
    size: TextUnit,
    weight: FontWeight,
    color: Color,
    lineHeight: Float = 1.45f,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Unspecified,
    fontFamily: FontFamily? = null
) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    fontFamily = fontFamily,
    lineHeight = (size.value * lineHeight).sp,
    lineHeightStyle = CssLineHeight,
    letterSpacing = letterSpacing,
    textAlign = align
)

@Composable
fun MvText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    size: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: Float = 1.45f,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    fontFamily: FontFamily? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val d = LocalMvText.current
    BasicText(
        text = text,
        modifier = modifier,
        style = mvTextStyle(
            size = if (size.isSpecified) size else d.size,
            weight = weight ?: d.weight,
            color = if (color != Color.Unspecified) color else d.color,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            align = align,
            fontFamily = fontFamily
        ),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout
    )
}

@Composable
fun MvText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: Float = 1.45f,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    fontFamily: FontFamily? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    MvText(
        text = AnnotatedString(text), modifier = modifier, size = size, weight = weight, color = color,
        align = align, letterSpacing = letterSpacing, lineHeight = lineHeight, maxLines = maxLines,
        overflow = overflow, softWrap = softWrap, fontFamily = fontFamily, onTextLayout = onTextLayout
    )
}

/** CSS: label{display:block;font-weight:700;margin:12px 0 4px} */
@Composable
fun MvLabel(text: String, modifier: Modifier = Modifier, size: TextUnit = TextUnit.Unspecified, top: Dp = 12.dp, bottom: Dp = 4.dp) {
    MvText(text, modifier.padding(top = top, bottom = bottom), size = size, weight = FontWeight.Bold)
}

/** CSS: h2{font-size:18px;margin:22px 0 10px} and h2 small{...} */
@Composable
fun MvH2(
    text: String,
    modifier: Modifier = Modifier,
    small: String? = null,
    size: TextUnit = 18.sp,
    top: Dp = 22.dp,
    bottom: Dp = 10.dp,
    align: TextAlign = TextAlign.Unspecified
) {
    val c = Mv.c
    val t = buildAnnotatedString {
        append(text)
        if (small != null) {
            withStyle(SpanStyle(color = c.mut, fontWeight = FontWeight.Medium, fontSize = 14.sp)) {
                append("\u00A0\u00A0$small")
            }
        }
    }
    MvText(t, modifier.padding(top = top, bottom = bottom), size = size, weight = FontWeight.Bold, align = align)
}

/** CSS: .note{color:var(--mut);font-size:14px} */
@Composable
fun MvNote(text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, align: TextAlign = TextAlign.Unspecified) {
    MvText(text, modifier, size = size, color = Mv.c.mut, align = align)
}
