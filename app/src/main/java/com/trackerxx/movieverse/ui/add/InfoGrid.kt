package com.trackerxx.movieverse.ui.add

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvText
import kotlin.math.abs

// ---- rating icons (the RI svg drawings) ---------------------------------------------------------------------------------

private sealed class IcOp {
    class Rect(val rx: Float, val color: Color) : IcOp()
    class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Color?, val stroke: Color? = null, val sw: Float = 0f) : IcOp()
    class P(val d: String, val fill: Color?, val stroke: Color? = null, val sw: Float = 0f, val round: Boolean = false) : IcOp()
    class T(val t: String, val x: Float, val y: Float, val size: Float, val color: Color) : IcOp()
}

private fun hex(v: Long) = Color(0xFF000000L or v)

private val ICONS: Map<String, List<IcOp>> = mapOf(
    "IMDb" to listOf(IcOp.Rect(5f, hex(0xF5C518)), IcOp.T("IMDb", 12f, 15.2f, 8.6f, Color.Black)),
    "Letterboxd" to listOf(
        IcOp.Circle(12f, 12f, 12f, hex(0x14181c)), IcOp.Circle(7f, 12f, 3.1f, hex(0xff8000)),
        IcOp.Circle(17f, 12f, 3.1f, hex(0x40bcf4)), IcOp.Circle(12f, 12f, 3.1f, hex(0x00e054))
    ),
    "Tomatoes" to listOf(
        IcOp.Circle(12f, 13.5f, 9.5f, hex(0xfa320a)),
        IcOp.P("M12 6c-.8-2.6-3.2-3.3-5-2.6 1.3.2 2.2.9 2.6 2-1.7-.6-3.4.2-3.9 1.2 1.7-.1 3 .3 3.9 1.1z", hex(0x00912d)),
        IcOp.P("M12 6c.8-2.6 3.2-3.3 5-2.6-1.3.2-2.2.9-2.6 2 1.7-.6 3.4.2 3.9 1.2-1.7-.1-3 .3-3.9 1.1z", hex(0x00912d))
    ),
    "Popcorn" to listOf(
        IcOp.Circle(7.5f, 8.5f, 3.4f, hex(0xffe27a)), IcOp.Circle(12f, 6.8f, 3.6f, hex(0xfff0a8)), IcOp.Circle(16.5f, 8.5f, 3.4f, hex(0xffe27a)),
        IcOp.P("M5.5 10.5h13l-1.6 12h-9.8z", hex(0xfa320a)),
        IcOp.P("M9.6 10.5l.5 12M14.4 10.5l-.5 12", null, Color.White, 1.6f)
    ),
    "Metacritic" to listOf(
        IcOp.Circle(12f, 12f, 11f, hex(0x1c1c1c), hex(0xffbd3f), 2f), IcOp.T("M", 12f, 16.4f, 12f, hex(0xffbd3f))
    ),
    "Metacritic Users" to listOf(
        IcOp.Circle(12f, 12f, 11f, hex(0x1c1c1c), hex(0xffbd3f), 2f), IcOp.T("M", 12f, 16.4f, 12f, hex(0xffbd3f))
    ),
    "TMDb" to listOf(IcOp.Rect(5f, hex(0x0d253f)), IcOp.T("TMDb", 12f, 14.6f, 7.4f, hex(0x01b4e4))),
    "Trakt" to listOf(
        IcOp.Rect(6f, hex(0xb01fd0)), IcOp.Circle(12f, 12f, 6.6f, null, Color.White, 1.8f),
        IcOp.P("M8.6 12.2l2.5 2.5 4.6-4.9", null, Color.White, 2f, true)
    ),
    "RogerEbert" to listOf(
        IcOp.P("M3.5 10.5h4v10h-4zM9.5 20.5h7.6a2 2 0 0 0 1.95-1.55l1.2-5.6A2 2 0 0 0 18.3 10.9h-3.9l.8-3.6a1.8 1.8 0 0 0-3.2-1.4L9.5 10.5z", Color.White)
    ),
    "MyAnimeList" to listOf(IcOp.Rect(5f, hex(0x2e51a2)), IcOp.T("MAL", 12f, 15.2f, 8.4f, Color.White))
)

/** the small coloured logo in front of a rating name (12dp, drawn in a 24 unit box) */
@Composable
fun RatingIcon(label: String, size: Dp = 12.dp) {
    val ops = ICONS[label] ?: return
    val paths = remember(label) { ops.map { if (it is IcOp.P) PathParser().parsePathString(it.d).toPath() else null } }
    val tm = rememberTextMeasurer()
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            ops.forEachIndexed { i, op ->
                when (op) {
                    is IcOp.Rect -> drawRoundRect(op.color, size = Size(24f, 24f), cornerRadius = CornerRadius(op.rx))
                    is IcOp.Circle -> {
                        if (op.fill != null) drawCircle(op.fill, op.r, Offset(op.cx, op.cy))
                        if (op.stroke != null) drawCircle(op.stroke, op.r, Offset(op.cx, op.cy), style = Stroke(op.sw))
                    }
                    is IcOp.P -> {
                        val p = paths[i]!!
                        if (op.fill != null) drawPath(p, op.fill)
                        if (op.stroke != null) drawPath(
                            p, op.stroke,
                            style = Stroke(op.sw, cap = if (op.round) StrokeCap.Round else StrokeCap.Butt, join = if (op.round) StrokeJoin.Round else StrokeJoin.Miter)
                        )
                    }
                    is IcOp.T -> {
                        val r = tm.measure(
                            op.t,
                            TextStyle(color = op.color, fontSize = op.size.toSp(), fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
                        )
                        drawText(r, topLeft = Offset(op.x - r.size.width / 2f, op.y - r.firstBaseline))
                    }
                }
            }
        }
    }
}

// ---- the grid -----------------------------------------------------------------------------------------------------------------

@Composable
private fun toneColor(t: Tone): Color {
    val c = Mv.c
    return when (t) { Tone.Good -> c.cg; Tone.Mid -> c.cy; Tone.Bad -> c.cr; Tone.None -> c.ink }
}

/** .sh : the loading shimmer (54 x 14) */
@Composable
private fun Shimmer() {
    val c = Mv.c
    val tr = rememberInfiniteTransition(label = "shm")
    val p = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "shmP")
    Box(Modifier.height(18.85.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .size(54.dp, 14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.s2)
                .graphicsLayer { }
        ) {
            Canvas(Modifier.size(54.dp, 14.dp)) {
                val w = size.width
                val x = -w + p.value * w * 2
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                        startX = x, endX = x + w
                    )
                )
            }
        }
    }
}

@Composable
private fun RatePageRow(pg: RatePage, modifier: Modifier) {
    val c = Mv.c
    Row(
        modifier.height(20.dp).padding(horizontal = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            RatingIcon(pg.label, 12.dp)
            MvText(pg.label, size = 11.sp, weight = FontWeight.Bold, lineHeight = 16f / 11f, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MvText(pg.value, size = 11.sp, weight = FontWeight.Bold, color = toneColor(pg.tone), lineHeight = 16f / 11f, maxLines = 1, softWrap = false)
            if (pg.suffix.isNotEmpty()) {
                MvText(" " + pg.suffix, size = 9.sp, color = c.mut, lineHeight = 16f / 9f, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** the Rating tile body: pages slide sideways; swipe = previous / next, tap = next (wraps) */
@Composable
private fun RatingBody(pages: List<RatePage>, flow: AddFlow) {
    val page = minOf(flow.rsPage, pages.size - 1)
    val anim = animateFloatAsState(page.toFloat(), tween(280, easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)), label = "rsAnim")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clipToBounds()
            .pointerInput(pages.size, page) {
                detectTapGestures { flow.rsGo(if (page + 1 >= pages.size) 0 else page + 1, pages.size) }
            }
            .pointerInput(pages.size, page) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { _, d -> dx += d },
                    onDragEnd = { if (abs(dx) >= 28.dp.toPx()) flow.rsGo(page + (if (dx < 0) 1 else -1), pages.size) }
                )
            }
    ) {
        val w = this.maxWidth
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -anim.value * w.toPx() }
        ) {
            pages.forEach { pg -> RatePageRow(pg, Modifier.width(w)) }
        }
    }
}

@Composable
private fun Tile(t: TileSpec, flow: AddFlow, modifier: Modifier) {
    val c = Mv.c
    val shape = RoundedCornerShape(10.dp)
    val labelColor: Color = if (t.chg) {
        val tr = rememberInfiniteTransition(label = "tileg")
        val p = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "tilegP")
        lerp(c.mut, Color(0xFF6EE7A0), p.value)
    } else c.mut
    Box(
        modifier
            .clip(shape)
            .background(c.s2)
            .border(1.dp, c.line, shape)
            .then(if (t.toggleSeasons) Modifier.plainClickable { flow.toggleSeasons() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            MvText(t.label, size = 11.sp, color = labelColor, maxLines = 1)
            when (val b = t.body) {
                is TileBody.Text -> MvText(b.text, size = 13.sp, weight = FontWeight.Bold, color = if (b.muted) c.mut else c.ink)
                is TileBody.Shimmer -> Shimmer()
                is TileBody.Rating -> RatingBody(b.pages, flow)
            }
        }
        val b = t.body
        if (b is TileBody.Rating && b.pages.size > 1) {
            val page = minOf(flow.rsPage, b.pages.size - 1)
            Row(Modifier.align(Alignment.TopEnd).padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                b.pages.indices.forEach { i ->
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == page) c.ink else c.mut.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

/** #info : the tiles (language, rating, votes, dates, genre, budget ...) in a dense two column grid, 6dp apart */
@Composable
fun InfoGrid(flow: AddFlow, m: Movie) {
    @Suppress("UNUSED_VARIABLE") val rev = flow.infoRev
    val items = InfoModel.build(m, flow.rsPage, flow.soOpen)
    val rows = ArrayList<MutableList<InfoItem>>()
    for (it in items) {
        when (it) {
            is InfoItem.Wide, is InfoItem.Pair -> rows.add(mutableListOf(it))
            is InfoItem.Half -> {
                val r = rows.firstOrNull { row -> row.size == 1 && row[0] is InfoItem.Half }
                if (r != null) r.add(it) else rows.add(mutableListOf(it))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            // a row with the Rating tile is 47.95dp high (its pages are 20dp); the other tile stretches to the same height
            val hasRating = row.any { x ->
                (x is InfoItem.Wide && x.t.body is TileBody.Rating) || (x is InfoItem.Half && x.t.body is TileBody.Rating)
            }
            val rowMod = Modifier.fillMaxWidth().then(if (hasRating) Modifier.height(47.95.dp) else Modifier)
            val tileMod = Modifier.weight(1f).then(if (hasRating) Modifier.fillMaxHeight() else Modifier)
            Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (x in row) {
                    when (x) {
                        is InfoItem.Wide -> Tile(x.t, flow, tileMod)
                        is InfoItem.Pair -> {
                            Tile(x.a, flow, Modifier.weight(1f))
                            Tile(x.b, flow, Modifier.weight(1f))
                        }
                        is InfoItem.Half -> Tile(x.t, flow, tileMod)
                    }
                }
            }
        }
    }
}
