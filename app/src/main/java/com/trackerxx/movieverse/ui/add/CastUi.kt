package com.trackerxx.movieverse.ui.add

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.net.CastData
import com.trackerxx.movieverse.net.CastPerson
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.mvTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest

/** copyName(t): copies a name / title to the clipboard with a short tap of vibration (no message) */
@Composable
fun rememberCopyName(): (String) -> Unit {
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    return remember(clip, haptic) {
        { t: String ->
            if (t.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clip.setText(AnnotatedString(t))
            }
        }
    }
}

/** pimg(p, 'nop', g): round profile photo; without a photo a person picture on white (when the gender is known) or a plain disc */
@Composable
fun PersonPhoto(path: String?, female: Boolean?, size: androidx.compose.ui.unit.Dp, imgSize: String = "w185") {
    val c = Mv.c
    val avatar = path == null && female != null
    Box(
        Modifier.size(size).clip(CircleShape).background(if (avatar) Color.White else c.s2)
    ) {
        if (path != null) {
            AsyncImage(
                model = Tmdb.imgUrl(path, imgSize), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (female != null) {
            MvIcon(if (female) MvIcons.AvatarF else MvIcons.AvatarM, size)
        }
    }
}

/** #cast : label + vertical two-page carousel (crew page, cast page), each page a row of round cards */
@Composable
fun CastBlock(data: CastData, onPerson: (Long) -> Unit) {
    val pages = remember(data) { listOf(data.crew, data.cast).filter { it.isNotEmpty() } }
    if (pages.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val dens = LocalDensity.current
    Column(Modifier.fillMaxWidth()) {
        MvText("Cast", Modifier.padding(top = 10.dp, bottom = 4.dp), weight = FontWeight.Bold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cw = (this.maxWidth - 18.dp) / 4
            // fitCast: the block is as tall as the tallest card + 12, at least 76
            val h = remember(data, cw, dens.density, dens.fontScale) {
                val wpx = with(dens) { cw.toPx() }.toInt().coerceAtLeast(1)
                val fixed = with(dens) { (44 + 3 + 1).dp.toPx() }
                var mx = 0f
                for (p in pages.flatten()) {
                    val a = measurer.measure(
                        AnnotatedString(p.name), mvTextStyle(9.sp, FontWeight.Bold, Color.Black, 1.2f, align = TextAlign.Center),
                        constraints = Constraints(maxWidth = wpx)
                    )
                    val b = if (p.sub.isEmpty()) 0 else measurer.measure(
                        AnnotatedString(p.sub), mvTextStyle(8.sp, FontWeight.Normal, Color.Black, 1.2f, align = TextAlign.Center),
                        constraints = Constraints(maxWidth = wpx)
                    ).size.height
                    val v = a.size.height + b + fixed
                    if (v > mx) mx = v
                }
                val dp = with(dens) { mx.toDp().value }
                maxOf(76.0, Math.ceil(dp + 12.0)).toFloat().dp
            }
            // ONE row: Director / Screenplay / Story people first, a small dot, then the actors
            Box(Modifier.fillMaxWidth().height(h)) { CastRow(data.crew, data.cast, cw, onPerson) }
        }
    }
}

/**
 * One row, exactly four people visible, page by page.
 * A HorizontalPager is used on purpose: it always comes to rest on a whole page, so the carousel can never stop between two cards
 * (the old LazyRow + hand written fling / "safety net" could be left half way, which cut a card in half at both edges).
 * Every page is one row of four slots of the SAME width cw = (row width - 3 gaps) / 4, so four whole cards + three 6dp gaps fill the width
 * exactly: nothing is cut on the left or the right. Page starts are 0, 4, 8 ... and the last page starts at (total - 4), so the final
 * page also shows four whole cards (it overlaps the page before it when the count is not a multiple of four).
 * The small dot between the crew and the actors is drawn in the 6dp gap in front of the first actor (when that gap is inside a page).
 */
@Composable
private fun CastRow(crew: List<CastPerson>, cast: List<CastPerson>, cw: androidx.compose.ui.unit.Dp, onPerson: (Long) -> Unit) {
    val all = remember(crew, cast) { crew + cast }
    val total = all.size
    val starts = remember(total) {
        val last = (total - 4).coerceAtLeast(0)
        (0 until last step 4).toList() + last
    }
    val pager = rememberPagerState(pageCount = { starts.size })
    HorizontalPager(
        state = pager,
        // clipToBounds: while it moves, a card never draws outside the row (not over the label, not up to the screen edge)
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentPadding = PaddingValues(top = 4.dp),
        pageSpacing = 0.dp,
        beyondViewportPageCount = 1
    ) { page ->
        val from = starts[page]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (slot in 0 until 4) {
                val i = from + slot
                if (i < total) {
                    val p = all[i]
                    if (i == crew.size && crew.isNotEmpty()) {
                        Box {
                            CastCard(p, cw, onPerson)
                            // the dot between the crew and the actors: centre of the 6dp gap, level with the middle of the round photos (44dp)
                            Box(Modifier.offset(x = (-5).dp, y = 20.dp).size(4.dp).clip(CircleShape).background(Mv.c.mut))
                        }
                    } else {
                        CastCard(p, cw, onPerson)
                    }
                } else {
                    Spacer(Modifier.width(cw))   // fewer than four people: the empty slots keep the same width
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CastCard(p: CastPerson, cw: androidx.compose.ui.unit.Dp, onPerson: (Long) -> Unit) {
    val c = Mv.c
    val copy = rememberCopyName()
    var faded by remember { mutableStateOf(false) }
    LaunchedEffect(faded) {
        if (faded) {
            delay(600)
            faded = false
        }
    }
    val a by animateFloatAsState(if (faded) 0.4f else 1f, tween(150), label = "lpf")
    Column(
        Modifier
            .width(cw)
            .alpha(a)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = { onPerson(p.id) },
                onLongClick = {
                    copy(p.name)
                    faded = true
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PersonPhoto(p.photo, p.female, 44.dp)
        Spacer(Modifier.height(3.dp))
        MvText(p.name, size = 9.sp, weight = FontWeight.Bold, lineHeight = 1.2f, align = TextAlign.Center)
        Spacer(Modifier.height(1.dp))
        MvText(p.sub, size = 8.sp, color = c.mut, lineHeight = 1.2f, align = TextAlign.Center)
    }
}


/** one grey loading box with the moving shine (.sh / #castsk i) */
@Composable
internal fun ShimBox(modifier: Modifier, shape: Shape) {
    val c = Mv.c
    val tr = rememberInfiniteTransition(label = "castsk")
    val p = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "caskP")
    Box(modifier.clip(shape).background(c.s2)) {
        Canvas(Modifier.fillMaxSize()) {
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

/** #castsk : the "Cast" label and four loading cards (photo circle + two text lines) while the cast is loading */
@Composable
fun CastSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        MvText("Cast", Modifier.padding(top = 10.dp, bottom = 4.dp), weight = FontWeight.Bold)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
            val cw = (this.maxWidth - 18.dp) / 4
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) {
                    Column(Modifier.width(cw), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimBox(Modifier.size(44.dp), CircleShape)
                        Spacer(Modifier.height(5.dp))
                        ShimBox(Modifier.fillMaxWidth(0.7f).height(8.dp), RoundedCornerShape(50))
                        Spacer(Modifier.height(5.dp))
                        ShimBox(Modifier.fillMaxWidth(0.5f).height(7.dp), RoundedCornerShape(50))
                    }
                }
            }
        }
    }
}
