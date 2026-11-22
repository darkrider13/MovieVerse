package com.trackerxx.movieverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.ui.theme.LocalMvText
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.MvTextDefaults

/** .btn / .btn.pri / .btn.red */
enum class BtnKind { Normal, Primary, Red }

/**
 * Button sizes of the web version:
 * Page = .btn (15px, 8/13), Nav = .nav-in .btn (14px, 7/11), Dialog = #dlg .btn (13px, 7/14), Settings = #sdlg .btn (13px, 8/14).
 * icon = true is the .btn.ic variant (narrower side padding).
 */
enum class BtnSize(val font: TextUnit, val padH: Dp, val padV: Dp, val icPadH: Dp) {
    Page(15.sp, 13.dp, 8.dp, 10.dp),
    Nav(14.sp, 11.dp, 7.dp, 9.dp),
    Dialog(13.sp, 14.dp, 7.dp, 9.dp),
    Settings(13.sp, 14.dp, 8.dp, 14.dp),
    SettingsSmall(12.sp, 10.dp, 6.dp, 10.dp)   // #sdlg .srow.ex .btn (Export / Import)
}

@Composable
fun MvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Normal,
    size: BtnSize = BtnSize.Page,
    icon: Boolean = false,
    danger: Boolean = false,           // .btn.del
    enabled: Boolean = true,
    spacing: Dp = 6.dp,
    borderColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = Mv.c
    val shape = RoundedCornerShape(10.dp)
    val weight = if (kind == BtnKind.Normal) FontWeight.Normal else FontWeight.Bold
    val colors: Triple<Color, Color, Color> = when (kind) {
        BtnKind.Primary -> Triple(c.acc, c.acc, c.onAcc)
        BtnKind.Red -> Triple(MvFixed.RedBtn, MvFixed.RedBtn, Color.White)
        BtnKind.Normal -> Triple(c.s, c.line, if (danger) MvFixed.Del else c.ink)
    }
    val bg = colors.first
    val bd = borderColor ?: colors.second
    val fg = colors.third
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bg)
            .border(1.dp, bd, shape)
            .plainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = (if (icon) size.icPadH else size.padH) + 1.dp, vertical = size.padV + 1.dp), // +1dp = the 1px border (CSS adds it to the box)
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalMvText provides MvTextDefaults(size.font, weight, fg)) {
            content()
        }
    }
}

@Composable
fun MvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Normal,
    size: BtnSize = BtnSize.Page,
    danger: Boolean = false,
    enabled: Boolean = true,
    borderColor: Color? = null
) {
    MvButton(onClick, modifier, kind, size, false, danger, enabled, borderColor = borderColor) { MvText(text) }
}

/** .pill (filter chips, tabs). Selected: ink background, bg-coloured bold text. */
@Composable
fun MvPill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    padH: Dp = 13.dp,
    padV: Dp = 5.dp,
    unselectedColor: Color = Color.Unspecified,   // default --mut
    selectedColor: Color = Color.Unspecified,     // default --bg
    weight: FontWeight? = null,                   // default: bold when selected, normal otherwise
    content: @Composable RowScope.() -> Unit
) {
    val c = Mv.c
    val shape = RoundedCornerShape(99.dp)
    val fg = if (selected) (if (selectedColor != Color.Unspecified) selectedColor else c.bg)
    else (if (unselectedColor != Color.Unspecified) unselectedColor else c.mut)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) c.ink else c.s)
            .border(1.dp, if (selected) c.ink else c.line, shape)
            .plainClickable(onClick = onClick)
            .padding(horizontal = padH + 1.dp, vertical = padV + 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalMvText provides MvTextDefaults(fontSize, weight ?: (if (selected) FontWeight.Bold else FontWeight.Normal), fg)
        ) { content() }
    }
}

@Composable
fun MvPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    padH: Dp = 13.dp,
    padV: Dp = 5.dp,
    weight: FontWeight? = null
) {
    MvPill(selected, onClick, modifier, fontSize, padH, padV, weight = weight) { MvText(text) }
}

/** .seg : segmented switch in the top bar (content-sized buttons, accent when selected). */
@Composable
fun MvSeg(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Mv.c
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.s)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            val shape = RoundedCornerShape(9.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (on) c.acc else Color.Transparent)
                    .plainClickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MvText(label, weight = FontWeight.Bold, color = if (on) c.onAcc else c.mut)
            }
        }
    }
}

/** .segc : equal-width choice control inside dialogs and forms. compact = "#dlg .segc button" (12px, min-height 32). */
@Composable
fun MvSegc(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val c = Mv.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.s2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            val shape = RoundedCornerShape(9.dp)
            Row(
                Modifier
                    .weight(1f)
                    .then(if (compact) Modifier.height(32.dp) else Modifier)
                    .clip(shape)
                    .background(if (on) c.acc else Color.Transparent)
                    .plainClickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = if (compact) 0.dp else 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MvText(
                    label,
                    size = if (compact) 12.sp else 15.sp,
                    weight = FontWeight.Bold,
                    color = if (on) c.onAcc else c.mut
                )
            }
        }
    }
}
