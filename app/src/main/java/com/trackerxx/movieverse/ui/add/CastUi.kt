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
 * One row, exactly four people visible. The row scrolls page by page (4 cards, see CardSnapFling): it always comes to rest with
 * four whole cards in view, so nobody is ever left cut in half at the sides (left or right, also at the end of the row:
 * the card width is (row width - 3 gaps) / 4, so the last four cards fill the width exactly).
 * The small dot between the crew and the actors is not a row item: it is drawn in the 6dp gap in front of the first actor,
 * so every slot has the same width.
 */
@Composable
private fun CastRow(crew: List<CastPerson>, cast: List<CastPerson>, cw: androidx.compose.ui.unit.Dp, onPerson: (Long) -> Unit) {
    val st = rememberLazyListState()
    val dens = LocalDensity.current
    val slotPx = with(dens) { (cw + 6.dp).toPx() }
    val total = crew.size + cast.size
    val lastStart = (total - 4).coerceAtLeast(0)
    val fling = remember(st, slotPx, lastStart) { CardSnapFling(st, slotPx, lastStart) }
    // Safety net: whenever the row stands still (whatever way it got there: fling, slow drag, new data, rotation) and it is not exactly
    // on a snap point, it glides to the nearest one. So a half card at the left or right edge can never stay.
    LaunchedEffect(st, slotPx, lastStart) {
        val pts = (0 until lastStart step 4).toList() + lastStart
        snapshotFlow { st.isScrollInProgress }.collectLatest { busy ->
            if (busy || slotPx <= 0f) return@collectLatest
            delay(60)   // a fling may start right after the finger lifts; a new emission cancels this wait
            if (st.isScrollInProgress) return@collectLatest
            val pos = st.firstVisibleItemIndex + st.firstVisibleItemScrollOffset / slotPx
            val t = pts.minByOrNull { abs(it - pos) } ?: 0
            if (abs(t - pos) > 0.02f) {
                try {
                    st.animateScrollToItem(t)
                } catch (e: CancellationException) {
                    // a touch took over: only a real cancellation of this effect may stop here, otherwise the next release settles again
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }
    LazyRow(
        // clipToBounds: while it moves, a card never draws outside the row (not over the label, not up to the screen edge)
        Modifier.fillMaxSize().clipToBounds(), state = st,
        contentPadding = PaddingValues(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        flingBehavior = fling
    ) {
        items(crew) { p -> CastCard(p, cw, onPerson) }
        itemsIndexed(cast) { i, p ->
            if (i == 0 && crew.isNotEmpty()) {
                Box {
                    CastCard(p, cw, onPerson)
                    // the dot between the crew and the actors: centre of the 6dp gap, level with the middle of the round photos (44dp)
                    Box(Modifier.offset(x = (-5).dp, y = 20.dp).size(4.dp).clip(CircleShape).background(Mv.c.mut))
                }
            } else {
                CastCard(p, cw, onPerson)
            }
        }
    }
}

/**
 * Page snapping for the cast row, the same rules as the Trending rows (CSS scroll-snap with "stop always"): every 4th card is a
 * snap point, so one swipe moves exactly one page of four cards, never more, and the row always comes to rest with four whole
 * cards in view (nobody cut in half at the sides). The last snap point is the position where the final four cards fill the row.
 * It runs every time the finger lifts (also with zero speed): a flick goes to the next / previous snap point, a slow drag that
 * stops between two points goes to the nearest one. Then it glides there with a soft spring (no bounce). A new touch simply takes
 * over, and its own release settles again, so the row can never stay stuck half way.
 */
private class CardSnapFling(
    private val st: LazyListState,
    private val slotPx: Float,
    private val lastStart: Int
) : FlingBehavior {
    private val points: List<Int> = (0 until lastStart step 4).toList() + lastStart

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (slotPx <= 0f) return initialVelocity
        val pos = st.firstVisibleItemIndex + st.firstVisibleItemScrollOffset / slotPx   // position in cards
        val eps = 0.001f
        val flick = abs(initialVelocity) > slotPx * 2f   // faster than two cards per second
        val target: Int = if (!flick) {
            points.minByOrNull { abs(it - pos) } ?: 0
        } else if (initialVelocity < 0f) {
            // finger moves left = forward: the next snap point after the one the gesture started from
            val from = points.lastOrNull { it <= pos + eps } ?: 0
            points.firstOrNull { it > from } ?: from
        } else {
            // finger moves right = back: the previous snap point before the one the gesture started from
            val from = points.firstOrNull { it >= pos - eps } ?: lastStart
            points.lastOrNull { it < from } ?: from
        }
        val distance = (target - pos) * slotPx
        if (abs(distance) < 0.5f) return 0f
        var done = 0f
        animate(
            initialValue = 0f, targetValue = distance, initialVelocity = -initialVelocity,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        ) { v, _ ->
            val d = v - done
            done += scrollBy(d)
        }
        return 0f
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
