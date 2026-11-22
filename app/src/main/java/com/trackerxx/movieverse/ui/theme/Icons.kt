package com.trackerxx.movieverse.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** One SVG shape of an icon (circles / rects are written as path data). */
@Immutable
class IconShape(val d: String, val fill: Boolean = false, val color: Color? = null, val strokeWidth: Float? = null)

/** Same drawing model as the web icons: viewBox 24, stroke-width, round caps, optional round joins. */
@Immutable
class MvIconData(
    val shapes: List<IconShape>,
    val strokeWidth: Float = 2f,
    val joinRound: Boolean = true,
    val viewBox: Float = 24f
)

private fun circ(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val iw = w - 2 * r
    val ih = h - 2 * r
    return "M${x + r} ${y}h${iw}a$r $r 0 0 1 $r ${r}v${ih}a$r $r 0 0 1 ${-r} ${r}h${-iw}a$r $r 0 0 1 ${-r} ${-r}v${-ih}a$r $r 0 0 1 $r ${-r}z"
}

private fun s(d: String) = IconShape(d)

/** Icons copied 1:1 from the web version's SVG markup. */
object MvIcons {
    val Gear = MvIconData(
        listOf(
            s(circ(12f, 12f, 3f)),
            s("M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z")
        ), strokeWidth = 2f, joinRound = true
    )
    val Close = MvIconData(listOf(s("M6 6l12 12M18 6L6 18")), strokeWidth = 2.4f, joinRound = false)
    val Folder = MvIconData(listOf(IconShape("M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", fill = true)))
    val Trash = MvIconData(listOf(s("M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6")), 2f, true)
    val Trash2 = MvIconData(listOf(s("M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 11v6M14 11v6")), 2f, true)
    val Filter = MvIconData(listOf(s("M3 4h18l-7 8v6l-4 2v-8z")), 2f, true)
    val Sun = MvIconData(
        listOf(s(circ(12f, 12f, 4f)), s("M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")),
        2f, false
    )
    val Moon = MvIconData(listOf(s("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")), 2f, true)
    val Copy = MvIconData(
        listOf(s(rrect(9f, 9f, 12f, 12f, 2f)), s("M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")), 2f, true
    )
    val Search = MvIconData(listOf(s(circ(11f, 11f, 7f)), s("M20 20l-3.5-3.5")), 2.2f, false)
    val Eye = MvIconData(listOf(s("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"), s(circ(12f, 12f, 3f))), 2f, true)
    val Plus = MvIconData(listOf(s("M12 5v14M5 12h14")), 2.6f, false)
    val CheckGreen = MvIconData(listOf(IconShape("M5 12l5 5L20 7", color = Color(0xFF00B800))), 2.5f, true)
    val CrossRed = MvIconData(listOf(IconShape("M6 6l12 12M18 6L6 18", color = Color(0xFFE24B4B))), 2.5f, false)
    val PlayWhite = MvIconData(listOf(IconShape("M8 5.5v13l11-6.5z", fill = true, color = Color.White)))

    /** #trl : red outlined YouTube button in the title box (28px, 1px red outline) */
    val YtOutline = MvIconData(
        listOf(
            IconShape(rrect(1.6f, 4.8f, 20.8f, 14.4f, 4.6f), color = Color(0xFFFF0000), strokeWidth = 1f),
            IconShape("M10.2 9.3l4.6 2.7-4.6 2.7z", color = Color(0xFFFF0000), strokeWidth = 1f)
        ), 1f, true
    )

    /** AVM / AVF : picture shown instead of a missing profile photo (black shapes on a white circle, viewBox 64) */
    val AvatarM = MvIconData(
        listOf(
            IconShape(circ(32f, 23f, 11f), fill = true, color = Color.Black),
            IconShape("M9 64c0-15 10-24 23-24s23 9 23 24z", fill = true, color = Color.Black)
        ), 2f, true, 64f
    )
    val AvatarF = MvIconData(
        listOf(
            IconShape("M32 6c-11 0-17 7-17 18 0 7 2 12 4 15-7 3-11 9-11 25h48c0-16-4-22-11-25 2-3 4-8 4-15C49 13 43 6 32 6z", fill = true, color = Color.Black)
        ), 2f, true, 64f
    )

    /** Path of the big green tick in the OK dialog (drawn animated there). */
    const val OkTickPath = "M5 12.5l4.5 4.5L19 7.5"
}

@Composable
fun MvIcon(icon: MvIconData, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val defaultTint = LocalMvText.current.color
    val paths = remember(icon) { icon.shapes.map { PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / icon.viewBox
        scale(k, k, pivot = Offset.Zero) {
            icon.shapes.forEachIndexed { i, sh ->
                val col = sh.color ?: (if (tint != Color.Unspecified) tint else defaultTint)
                if (sh.fill) {
                    drawPath(paths[i], col)
                } else {
                    drawPath(
                        paths[i], col,
                        style = Stroke(
                            width = sh.strokeWidth ?: icon.strokeWidth,
                            cap = StrokeCap.Round,
                            join = if (icon.joinRound) StrokeJoin.Round else StrokeJoin.Miter
                        )
                    )
                }
            }
        }
    }
}
