package com.trackerxx.movieverse.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.trending.TrItem
import com.trackerxx.movieverse.trending.Trending
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.BtnSize
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.components.riseIn
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import com.trackerxx.movieverse.util.RateClass

/** one Trending row: title + sideways list of cards (three cards wide, snaps) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrendSection(
    id: String,
    list: List<TrItem>?,
    failed: Boolean,
    libIds: Set<Long>,
    onTap: (TrItem) -> Unit,
    onRetry: () -> Unit
) {
    val c = Mv.c
    val title = Trending.TITLES[id] ?: ""
    if (list.isNullOrEmpty()) {
        if (failed) {
            Column(Modifier.fillMaxWidth()) {
                TrendTitle(title)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MvText("Could not load.", size = 14.sp, color = c.mut, align = TextAlign.Center)
                    MvButton("Retry", onRetry, kind = BtnKind.Primary, size = BtnSize.Page)
                }
            }
        }
        return
    }
    val rk = id == "m"
    val shown = remember(list, rk) { list.take(if (rk) 10 else 20) }
    Column(Modifier.fillMaxWidth()) {
        TrendTitle(title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemW = (this.maxWidth - 20.dp) / 3
            val state = rememberLazyListState()
            val measurer = rememberTextMeasurer()   // one per row (it used to be one per card)
            LazyRow(
                state = state,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // free, natural fling (like Netflix / Play Store rows) - no snapping, so the row never "catches" the finger
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = PaddingValues(0.dp)
            ) {
                items(shown.size, key = { shown[it].id }, contentType = { "tr-card" }) { ri ->
                    TrendCard(shown[ri], if (rk) ri + 1 else 0, libIds.contains(shown[ri].id), itemW, onTap, id, ri, measurer)
                }
            }
        }
    }
}

/** .tt : 15px bold title, 16 above / 4 below */
@Composable
private fun TrendTitle(text: String) {
    MvText(text, Modifier.padding(top = 16.dp, bottom = 4.dp), size = 15.sp, weight = FontWeight.Bold)
}

@Composable
private fun TrendCard(o: TrItem, rank: Int, inLib: Boolean, width: androidx.compose.ui.unit.Dp, onTap: (TrItem) -> Unit, sid: String, ri: Int, measurer: androidx.compose.ui.text.TextMeasurer) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    val dark = c.dark
    Column(Modifier.width(width).plainClickable { onTap(o) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(cssGradient(160f, c.s2, c.line))
                .drawWithCache {
                    // everything heavy (gradient, text layout, shadow) is built ONCE here, not on every frame
                    val h = size.height * 0.62f
                    val a = if (dark) Color(34 / 255f, 36 / 255f, 42 / 255f) else Color(226 / 255f, 228 / 255f, 234 / 255f)
                    val fade = if (rank > 0) Brush.verticalGradient(
                        0f to a.copy(alpha = 0f), 0.12f to a.copy(alpha = 0.09f), 0.28f to a.copy(alpha = 0.26f),
                        0.5f to a.copy(alpha = 0.52f), 0.75f to a.copy(alpha = 0.78f), 1f to a.copy(alpha = 0.92f),
                        startY = size.height - h, endY = size.height
                    ) else null
                    val k = size.width / 100f
                    val glyph = if (dark) Color.White else Color(0xFF141846)
                    val layout = if (rank > 0) measurer.measure(
                        rank.toString(),
                        TextStyle(
                            brush = Brush.verticalGradient(
                                0f to glyph.copy(alpha = 0.98f), 0.5f to glyph.copy(alpha = 0.7f), 1f to glyph.copy(alpha = 0.25f)
                            ),
                            fontSize = (70f * k).toSp(),
                            fontWeight = FontWeight.Black,
                            shadow = Shadow(
                                color = if (dark) Color.White.copy(alpha = 0.5f) else Color(20 / 255f, 24 / 255f, 70 / 255f, 0.4f),
                                blurRadius = 4.dp.toPx()
                            )
                        )
                    ) else null
                    onDrawWithContent {
                        drawContent()
                        if (fade != null && layout != null) {
                            // .rkt : dark fade at the bottom + the big rank number
                            drawRect(fade, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            val x = (if (rank > 9) 2f else 1f) * k
                            drawText(layout, topLeft = Offset(x, 140f * k - layout.firstBaseline))
                        }
                    }
                }
        ) {
            if (!o.p.isNullOrEmpty()) {
                AsyncImage(
                    model = Tmdb.imgUrl(o.p, "w185"), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (inLib) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .height(12.4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MvFixed.OkGreen)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MvText("In Library", size = 6.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1f, maxLines = 1, softWrap = false)
                }
            }
            val x = o.x
            if (!x.isNullOrEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(MvFixed.RateBg).padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        MvText("IMDb", size = 8.sp, weight = FontWeight.ExtraBold, color = Color.White, lineHeight = 1.3f)
                        MvText(" ", size = 8.sp, weight = FontWeight.Bold, lineHeight = 1.3f)
                        val cls = Fmt.rcls(Js.parseFloat(x) * 10)
                        MvText(
                            x, size = 8.sp, weight = FontWeight.Bold, lineHeight = 1.3f,
                            color = when (cls) {
                                RateClass.Good -> Color(0xFF00E000)
                                RateClass.Bad -> Color(0xFFFF6B6B)
                                else -> Color(0xFFFFC857)
                            }
                        )
                    }
                }
            }
        }
        MvText(
            o.t, Modifier.padding(top = 5.dp), size = 12.sp, weight = FontWeight.Bold, lineHeight = 1.25f,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        MvText(o.d.take(4) + (if (o.tv) " · TV" else ""), size = 11.sp, color = c.mut)
    }
}
