package com.trackerxx.movieverse.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import com.trackerxx.movieverse.data.Ls
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.R
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.data.Status
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvLoadingDots
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.MvSmallSpinner
import com.trackerxx.movieverse.ui.components.isDarkBg
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.mvTextStyle
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.RateClass
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** CSS linear-gradient(angle, colors...): same gradient line as the browser for any box size */
fun cssGradient(angleDeg: Float, c1: Color, c2: Color): Brush {
    // the same brush object is reused for the same arguments: a new object on every redraw made every card repaint while scrolling
    val k = Triple(angleDeg, c1, c2)
    gradCache[k]?.let { return it }
    if (gradCache.size > 96) gradCache.clear()
    val b = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val a = Math.toRadians(angleDeg.toDouble())
            val dx = sin(a).toFloat()
            val dy = -cos(a).toFloat()
            val len = abs(size.width * dx) + abs(size.height * dy)
            val cx = size.width / 2f
            val cy = size.height / 2f
            return LinearGradientShader(
                Offset(cx - dx * len / 2f, cy - dy * len / 2f),
                Offset(cx + dx * len / 2f, cy + dy * len / 2f),
                listOf(c1, c2)
            )
        }
    }
    gradCache[k] = b
    return b
}

private val gradCache = HashMap<Triple<Float, Color, Color>, Brush>()

// ================================================================================================================
// Top bar (.nav)
// ================================================================================================================

private val EaseInOutCss = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** .nav : brand, Settings button (glows red while there is un-exported data) */
@Composable
fun TopNav(
    dirty: Boolean,
    onBrand: () -> Unit,
    onSettings: () -> Unit,
    onSettingsLong: () -> Unit = {},
    onUpdate: () -> Unit = {},
    updateSpinning: Boolean = false
) {
    val c = Mv.c
    val tgSp = (LocalConfiguration.current.screenWidthDp * 0.026f).coerceIn(8.5f, 11f)
    // dark theme = the movie details look: no solid bar and no divider line. The very top (status bar + bar) is black and fades
    // softly into the red / blue glow of the page background (same gradient as the details page). Light themes keep the plain bar.
    val glow = isDarkBg(c.bg)
    val sbPx = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val fadePx = with(LocalDensity.current) { 44.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().then(
            if (glow) Modifier.drawBehind {
                val top = -sbPx
                val h = size.height + sbPx + fadePx
                drawRect(
                    Brush.verticalGradient(
                        0f to c.nav, 0.5f to c.nav.copy(alpha = 0.72f), 1f to Color.Transparent,
                        startY = top, endY = top + h
                    ),
                    topLeft = Offset(0f, top), size = Size(size.width, h)
                )
            } else Modifier.background(c.nav)
        )
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f).plainClickable(onClick = onBrand).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(26.dp))
                    Column {
                        MvText(
                            buildAnnotatedString {
                                append("Movie")
                                withStyle(SpanStyle(color = c.acc)) { append("Verse") }
                            },
                            size = 18.sp, weight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, maxLines = 1, softWrap = false
                        )
                        MvText(
                            "Every story starts with a movie",
                            size = tgSp.sp, lineHeight = 1.1f, color = c.mut, letterSpacing = 0.02.em, maxLines = 1, softWrap = false
                        )
                    }
                }
                // "check for updates": same size and look as the gear, right next to it on its left
                UpdateButton(onUpdate, updateSpinning)
                SettingsButton(dirty, onSettings, onSettingsLong)
            }
        }
        if (!glow) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/**
 * 34x34 "check for updates" button (two round arrows), the twin of the gear button.
 * spinning = the check is running: the arrows turn exactly three times (1.5 s, soft start and soft stop). If the network is slow they keep
 * turning smoothly until the answer is there, then glide to a stop upright (never frozen at a tilted angle).
 */
@Composable
private fun UpdateButton(onClick: () -> Unit, spinning: Boolean) {
    val c = Mv.c
    val shape = RoundedCornerShape(9.dp)
    val rot = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            rot.snapTo(0f)
            rot.animateTo(1080f, tween(1500, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)))
            while (true) {
                rot.snapTo(0f)
                rot.animateTo(360f, tween(800, easing = androidx.compose.animation.core.LinearEasing))
            }
        } else {
            val r = rot.value % 360f
            if (r > 0.5f) rot.animateTo(rot.value + (360f - r), tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            rot.snapTo(0f)
        }
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(shape)
            .background(c.s)
            .border(1.dp, c.line, shape)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        MvIcon(MvIcons.Sync, 18.dp, Modifier.graphicsLayer { rotationZ = rot.value }, tint = c.ink)
    }
}

/** #set : 34x34 gear button. dirty = blkg animation: border + soft glow pulse red every 2.2 s */
@Composable
private fun SettingsButton(dirty: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val c = Mv.c
    val shape = RoundedCornerShape(9.dp)
    val phase: State<Float> = if (dirty) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutCss), RepeatMode.Reverse),
            label = "glowPhase"
        )
    } else remember { mutableStateOf(0f) }
    val line = c.line
    Box(
        Modifier
            .size(34.dp)
            // own layer: the endless glow animation only redraws this button, not the whole top bar / page
            .graphicsLayer { }
            .drawBehind {
                if (dirty) {
                    val p = phase.value
                    val r = 9.dp.toPx()
                    for (i in 0 until 10) {
                        val spread = (1 + i).dp.toPx()
                        val a = 0.45f * p * (1f - i / 10f).pow(2) * 0.3f
                        drawRoundRect(
                            color = Color(1f, 70f / 255f, 70f / 255f, a),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + 2 * spread, size.height + 2 * spread),
                            cornerRadius = CornerRadius(r + spread),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
            .clip(shape)
            .background(c.s)
            .drawBehind {
                val p = phase.value
                val col = if (dirty) {
                    val a = 0.2f + (1f - 0.2f) * p
                    Color(1f, 70f / 255f, 70f / 255f, a)
                } else line
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = col, topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(9.dp.toPx() - w / 2), style = Stroke(w)
                )
            }
            .pointerInput(onClick, onLongPress) {
                detectTapGestures(onLongPress = { onLongPress() }, onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        MvIcon(MvIcons.Gear, 18.dp, tint = c.ink)
    }
}

// ================================================================================================================
// Hero + Trending / Your Library switch
// ================================================================================================================

/** two children on one line (space-between, centred); when they do not fit the second one wraps below (flex-wrap) */
@Composable
private fun WrapBetween(modifier: Modifier = Modifier, gap: Dp = 8.dp, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Layout(content = { Box { left() }; Box { right() } }, modifier = modifier) { ms: List<Measurable>, cons ->
        val g = gap.roundToPx()
        val maxW = cons.maxWidth
        val l: Placeable = ms[0].measure(Constraints(0, maxW, 0, Constraints.Infinity))
        val r: Placeable = ms[1].measure(Constraints(0, maxW, 0, Constraints.Infinity))
        if (l.width + g + r.width <= maxW) {
            val h = maxOf(l.height, r.height)
            layout(maxW, h) {
                l.placeRelative(0, (h - l.height) / 2)
                r.placeRelative(maxW - r.width, (h - r.height) / 2)
            }
        } else {
            layout(maxW, l.height + g + r.height) {
                l.placeRelative(0, 0)
                r.placeRelative(0, l.height + g)
            }
        }
    }
}

/** .hero : "Welcome" + the user's name, Add to Library button */
@Composable
fun Hero(name: String, onAdd: () -> Unit) {
    val c = Mv.c
    val shape = RoundedCornerShape(16.dp)
    WrapBetween(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.s)
            .border(1.dp, c.line, shape)
            .padding(17.dp),
        left = {
            Column {
                MvText("Welcome", size = 14.sp, lineHeight = 1.2f, color = c.mut, letterSpacing = 0.02.em, modifier = Modifier.padding(bottom = 1.dp))
                MvText(
                    name.ifEmpty { "User" },
                    size = 22.sp, weight = FontWeight.Bold, letterSpacing = (-0.02).em,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        },
        right = { MvButton("Add to Library", onAdd, kind = BtnKind.Primary) }
    )
}

private const val SWITCH_ORDER_KEY = "library.switchOrder"

/** the saved order of the three home pills as ids (0 = Trending, 1 = Your Library, 2 = Releases). Trending is fixed at the first place; anything unexpected = default */
private fun loadSwitchOrder(): List<Int> {
    val l = try { Ls.getItem(SWITCH_ORDER_KEY)?.split(",")?.mapNotNull { it.trim().toIntOrNull() } } catch (e: Exception) { null }
    return if (l != null && l.size == 3 && l.toSet() == setOf(0, 1, 2) && l[0] == 0) l else listOf(0, 1, 2)   // Trending always first
}

/**
 * .hsw : Trending | Your Library | Releases. A tap picks a pill; touch and slide the finger over the pills: the
 * highlight follows the finger (a small tick on every pill) and the pill under the finger is picked when the finger lifts.
 * Press and HOLD Your Library or Releases (same as the library tabs) and it lifts: drag it left / right, the other one glides out of
 * the way, and the new order is saved. Trending is fixed at the first place (it cannot be lifted and nothing goes before it).
 * Dropping a pill does not switch the page.
 * selected: 0 = Trending, 1 = Your Library, 2 = Releases (ids, not positions: the order of the pills can change)
 */
@Composable
fun HomeSwitch(selected: Int, onPick: (Int) -> Unit) {
    val labels = listOf("Trending", "Your Library", "Releases")
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var hover by remember { mutableIntStateOf(-1) }          // position under the finger (quick slide)
    val pickNow by rememberUpdatedState(onPick)
    val selNow by rememberUpdatedState(selected)
    var order by remember { mutableStateOf(loadSwitchOrder()) }   // ids from left to right
    var dragId by remember { mutableIntStateOf(-1) }         // id of the lifted pill, -1 = none
    var dragX by remember { mutableFloatStateOf(0f) }        // left edge of the lifted pill (px, inside the row)
    var widthPx by remember { mutableIntStateOf(0) }
    val offs = remember { List(3) { Animatable(0f) } }       // glide offset of every pill (px)
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    val step = if (widthPx > 0) (widthPx - 2 * gapPx) / 3f + gapPx else 0f
    // the lifted pill keeps the white "selected" look for the whole drag (the page behind does not change)
    val on = if (hover >= 0) hover else if (dragId >= 0) order.indexOf(dragId) else order.indexOf(selected)
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val gap = 8.dp.toPx()
                    val pw = (w - 2 * gap) / 3f
                    val stp = pw + gap
                    fun idx(x: Float): Int = (x / stp).toInt().coerceIn(0, 2)
                    var cur = idx(down.position.x)
                    hover = cur
                    // 1) held still long enough: the pill is lifted and can be moved
                    // Trending (id 0) is fixed: it is never lifted, so it skips this and works as a plain tap / slide.
                    // A pill that is not selected yet cannot be lifted either: select it first (tap), then hold and drag.
                    val lp = if (order[cur] == 0 || order[cur] != selNow) null else awaitLongPressOrCancellation(down.id)
                    if (lp != null) {
                        val id = order[cur]
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hover = -1
                        dragId = id
                        val grab = down.position.x - cur * stp
                        dragX = (lp.position.x - grab).coerceIn(stp, w - pw)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                            dragX = (ch.position.x - grab).coerceIn(stp, w - pw)   // never in front of Trending
                            val to = ((dragX + pw / 2f) / stp).toInt().coerceIn(1, 2)
                            val from = order.indexOf(id)
                            if (to != from) {
                                val l = order.toMutableList()
                                l.removeAt(from); l.add(to, id)
                                order = l
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        // dropped: the pill glides from where the finger left it into its slot
                        val tx = dragX - order.indexOf(id) * stp
                        dragId = -1
                        try { Ls.setItem(SWITCH_ORDER_KEY, order.joinToString(",")) } catch (e: Exception) { }
                        scope.launch { offs[id].snapTo(tx); offs[id].animateTo(0f, tween(170)) }
                        return@awaitEachGesture
                    }
                    // 2) the finger lifted before the long press: a tap
                    val still = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (still == null || !still.pressed) {
                        hover = -1
                        pickNow(order[cur])
                        return@awaitEachGesture
                    }
                    // 3) the finger moved: slide over the pills, the one under the finger is picked on lift
                    var commit = false
                    val slopY = 28.dp.toPx()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { commit = true; break }
                        // a vertical move is a page scroll, not a pill switch
                        if (kotlin.math.abs(ch.position.y - down.position.y) > slopY) break
                        val n = idx(ch.position.x)
                        if (n != cur) {
                            cur = n; hover = n
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (n != idx(down.position.x)) ch.consume()
                    }
                    hover = -1
                    if (commit) pickNow(order[cur])
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        order.forEachIndexed { slot, id ->
            key(id) {
                // when a pill gets a new position it starts from where it was and glides to the new place
                var prev by remember { mutableIntStateOf(slot) }
                LaunchedEffect(slot) {
                    if (prev != slot) {
                        offs[id].snapTo(offs[id].value + (prev - slot) * step)
                        prev = slot
                        offs[id].animateTo(0f, tween(180))
                    }
                }
                MvPill(
                    labels[id], on == slot, {},
                    Modifier.weight(1f).zIndex(if (dragId == id) 1f else 0f).graphicsLayer {
                        val lifted = dragId == id
                        translationX = if (lifted) dragX - slot * step else offs[id].value
                        val sc = if (lifted) 1.06f else 1f
                        scaleX = sc; scaleY = sc
                    },
                    fontSize = 14.sp, padH = 0.dp, padV = 10.dp, weight = FontWeight.Bold
                )
            }
        }
    }
}

// ================================================================================================================
// Tabs, search, folder chips, filters
// ================================================================================================================

/** #tabs : three tabs wide, scrolls sideways, selected tab = ink pill. Long press a status tab and drag to re-order. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabsRow(
    tabs: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onAddStatus: () -> Unit,
    drag: DragState,
    onReorder: (List<String>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemW = (this.maxWidth - 12.dp) / 3
        val state = rememberLazyListState()
        var rowH by remember { mutableIntStateOf(0) }
        var pillH by remember { mutableIntStateOf(0) }
        // Three tabs are visible, a swipe moves one tab at a time and every tab always sits exactly in its slot (never cut in half).
        // Item count = tabs + the "+" button.
        val totalNow by rememberUpdatedState(tabs.size + 1)
        /** the first visible item can be at most this one, so the last position always shows a full row of three */
        fun maxFirst(): Int = (totalNow - 3).coerceAtLeast(0)
        /** scroll position measured in items (e.g. 1.5 = the second tab is half way) */
        fun posNow(): Float {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            val step = (first.size + 6 * dens).coerceAtLeast(1f)
            return state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / step
        }
        LaunchedEffect(selected, tabs.size) {
            val idx = tabs.indexOfFirst { it.first == selected }
            if (idx >= 0) {
                val info = state.layoutInfo
                val it = info.visibleItemsInfo.firstOrNull { x -> x.index == idx }
                // A tab that is on screen stays exactly where it is when it is tapped. The old check (1px) failed on the right-hand
                // slot because the three tab widths are rounded to whole pixels, so tapping it threw the tab into the middle.
                val tol = (6 * dens).toInt()
                val fully = it != null && it.offset >= -tol && it.offset + it.size <= info.viewportEndOffset - info.viewportStartOffset + tol
                if (!fully) state.scrollToItem((idx - 1).coerceIn(0, maxFirst()))
            }
        }
        // after a swipe (or after a tab was dropped) the row settles on the nearest tab: no tab is ever left cut in half
        LaunchedEffect(state) {
            var startPos = 0f
            var wasBusy = false
            snapshotFlow { state.isScrollInProgress || drag.active }.collect { busy ->
                if (busy) {
                    if (!wasBusy) startPos = posNow()
                    wasBusy = true
                } else {
                    wasBusy = false
                    if (totalNow <= 3) return@collect
                    val pos = posNow()
                    val startItem = startPos.roundToInt()
                    var ti = pos.roundToInt()
                    // a short swipe still moves by one tab
                    if (ti == startItem && abs(pos - startPos) > 0.12f) ti = startItem + (if (pos > startPos) 1 else -1)
                    ti = ti.coerceIn(0, maxFirst())
                    val aligned = state.firstVisibleItemIndex == ti && state.firstVisibleItemScrollOffset <= 1
                    if (!aligned) state.animateScrollToItem(ti)
                }
            }
        }
        // while a tab is dragged near the left / right edge the row scrolls (tick() in the web version)
        LaunchedEffect(drag.active) {
            if (!drag.active) return@LaunchedEffect
            while (drag.active) {
                withFrameNanos { }
                val x = drag.pos.x
                val w = state.layoutInfo.viewportSize.width
                val moved = if (x < 50 * dens) state.scrollBy(-10 * dens)
                else if (x > w - 50 * dens) state.scrollBy(10 * dens) else 0f
                if (moved != 0f) swapAtRow(drag, state)
            }
        }
        // Lift / drop animation of the picked-up pill. lift = 0..1 while it is picked up; after the finger lifts the copy glides into
        // its slot (settleP 0..1) and only then the real pill shows again, so the drop never "pops" and the order never jumps back.
        val liftP = remember { Animatable(0f) }
        val settleP = remember { Animatable(0f) }
        var lastKey by remember { mutableStateOf<String?>(null) }
        // true from the very frame the finger lifts (drag.key is null, lastKey is still set) until the glide into the slot is over
        val settling = drag.key == null && lastKey != null
        LaunchedEffect(drag.key) {
            val k = drag.key
            if (k != null) {
                lastKey = k
                settleP.snapTo(0f)
                liftP.snapTo(0f)
                liftP.animateTo(1f, tween(140, easing = EaseCss))
            } else if (lastKey != null) {
                settleP.animateTo(1f, tween(170, easing = EaseCss))
                lastKey = null
                liftP.snapTo(0f)
            }
        }
        val shown: List<Pair<String, String>> = if (drag.active || settling) {
            val by = tabs.associateBy { it.first }
            tabs.filter { it.first == "all" } + drag.order.mapNotNull { by[it] }   // the All pill never moves
        } else tabs

        LazyRow(
            state = state,
            modifier = Modifier
                .onSizeChanged { rowH = it.height }
                .tabReorder(
                    drag, state,
                    ids = { tabs.map { it.first }.filter { it != "all" } },
                    pillTop = { (rowH - pillH) / 2f },
                    pillHeight = { pillH },
                    haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDrop = onReorder,
                    canPick = { it == selected }   // only the tab that is already selected can be lifted
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = rememberSnapFlingBehavior(state)
        ) {
            items(shown, key = { it.first }) { t ->
                val isDragged = drag.key == t.first
                val isSettling = settling && lastKey == t.first
                MvPill(
                    selected = selected == t.first,
                    onClick = { if (!drag.recentlyDragged()) onSelect(t.first) },
                    modifier = Modifier
                        .width(itemW)
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = if (drag.active || settling) tween(180, easing = EaseCss) else null,
                            fadeOutSpec = null
                        )
                        .onSizeChanged { if (it.height > 0) pillH = it.height }
                        .then(
                            if (isSettling) Modifier.alpha(0f)
                            else if (isDragged) Modifier
                                .alpha(0.3f)
                                .drawWithContent {
                                    drawContent()
                                    val w = 2.dp.toPx()
                                    drawRoundRect(
                                        Color.White, topLeft = Offset(w / 2f, w / 2f),
                                        size = Size(size.width - w, size.height - w),
                                        cornerRadius = CornerRadius(size.height / 2f), style = Stroke(w)
                                    )
                                } else Modifier
                        ),
                    fontSize = 12.5.sp, padH = 4.dp, padV = 8.dp
                ) {
                    MvText(t.second, maxLines = 1, overflow = TextOverflow.Ellipsis, align = TextAlign.Center)
                }
            }
            item(key = "+") {
                MvPill(
                    selected = false, onClick = onAddStatus, modifier = Modifier.width(itemW),
                    fontSize = 16.sp, padH = 4.dp, padV = 8.dp, weight = FontWeight.ExtraBold
                ) {
                    // the line box of a 12.5sp tab (12.5 * 1.45): the "+" pill is exactly as high as the other pills
                    val boxH = with(LocalDensity.current) { (12.5.sp * 1.45f).toDp() }
                    Box(Modifier.height(boxH), contentAlignment = Alignment.Center) {
                        MvText("+", size = 16.sp, weight = FontWeight.ExtraBold, lineHeight = 1f, maxLines = 1, softWrap = false, align = TextAlign.Center)
                    }
                }
            }
        }

        // The copy that follows the finger. It is a plain, fully opaque layer (no alpha, no tilt): a layer with alpha < 1 or a rotation is
        // first drawn at normal size and then scaled / rotated as a bitmap, which made the pill edges soft and jagged. Now the pill is
        // re-drawn as a vector at the lifted size, so the curve of the edge stays sharp. The shadow lives in the same layer.
        val dragged = shown.firstOrNull { it.first == (drag.key ?: lastKey) }
        if (dragged != null && (drag.key != null || settling)) {
            val shadowPx = with(LocalDensity.current) { 14.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        val fx = drag.pos.x - drag.grab.x
                        val fy = drag.pos.y - drag.grab.y
                        if (drag.key == null && settling) {
                            val slot = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == lastKey }
                            val t = settleP.value
                            val tx = slot?.offset?.toFloat() ?: fx
                            val ty = (rowH - pillH) / 2f
                            IntOffset((fx + (tx - fx) * t).roundToInt(), (fy + (ty - fy) * t).roundToInt())
                        } else IntOffset(fx.roundToInt(), fy.roundToInt())
                    }
                    .graphicsLayer {
                        val up = if (drag.key == null && settling) 1f - settleP.value else liftP.value
                        val sc = 1f + 0.06f * up
                        scaleX = sc
                        scaleY = sc
                        shadowElevation = shadowPx * up
                        shape = RoundedCornerShape(99.dp)
                        clip = false
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    }
            ) {
                MvPill(
                    selected = selected == dragged.first, onClick = {}, modifier = Modifier.width(itemW),
                    fontSize = 12.5.sp, padH = 4.dp, padV = 8.dp
                ) {
                    MvText(dragged.second, maxLines = 1, overflow = TextOverflow.Ellipsis, align = TextAlign.Center)
                }
            }
        }
    }
}

/** #q + #qcnt */
@Composable
fun SearchRow(value: String, onValue: (String) -> Unit, count: String) {
    val c = Mv.c
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The box can only get the focus after the user touches it: until then no cursor and no keyboard (nothing gives it the focus by itself).
    // When the focus is gone again (keyboard closed, tap elsewhere) it is locked again.
    var armed by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(armed) { if (armed) { try { focusReq.requestFocus() } catch (e: Exception) { } } }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier
                .weight(1f)
                .focusProperties { canFocus = armed }
                .focusRequester(focusReq)
                .onFocusChanged { st ->
                    if (st.isFocused) hadFocus = true
                    else if (hadFocus) { hadFocus = false; armed = false }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        armed = true
                    }
                },
            singleLine = true,
            textStyle = mvTextStyle(14.sp, FontWeight.Normal, c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(shape)
                        .background(c.s2)
                        .border(1.dp, if (focused) c.mut else c.line, shape)
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) MvText("Search movies", size = 14.sp, color = MvFixed.Placeholder, maxLines = 1)
                        inner()
                    }
                    if (value.isNotEmpty()) {
                        Box(Modifier.padding(start = 8.dp).size(16.dp).plainClickable { onValue("") }) {
                            MvIcon(MvIcons.Close, 16.dp, tint = Color.White)
                        }
                    }
                }
            }
        )
        Box(
            Modifier
                .height(34.dp)
                .widthIn(min = 40.dp)
                .clip(shape)
                .background(c.s2)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            MvText(count, size = 14.sp, weight = FontWeight.ExtraBold, lineHeight = 1f, maxLines = 1)
        }
    }
}

/** one chip of the folder row */
@Composable
private fun FolderChip(
    width: Dp,
    on: Boolean,
    isAll: Boolean,
    isNew: Boolean,
    label: String,
    count: Int?,
    folderColor: Color?,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null
) {
    val c = Mv.c
    val pill = RoundedCornerShape(99.dp)
    val fc = folderColor ?: c.acc
    val isFolder = !isAll && !isNew
    val gradient: Brush? = when {
        isFolder && on -> cssGradient(135f, fc.copy(alpha = 0.60f), fc.copy(alpha = 0.18f))
        isFolder -> cssGradient(135f, fc.copy(alpha = 0.20f), fc.copy(alpha = 0.05f))
        else -> null
    }
    val textColor = when {
        isAll && on -> c.bg
        isNew -> c.mut
        else -> c.ink
    }
    val countColor = when {
        isAll && on -> c.bg.copy(alpha = 0.65f)
        isFolder && on -> c.ink.copy(alpha = 0.75f)
        else -> c.mut
    }
    val border = when {
        isAll && on -> c.ink
        isFolder && on -> fc.copy(alpha = 0.55f)
        else -> c.line
    }
    val code = Iso2.MAP[label.trim().lowercase()]
    var useCode by remember(label, width) { mutableStateOf(false) }

    Row(
        Modifier
            .width(width)
            .clip(pill)
            .background(if (isAll && on) c.ink else c.s)
            .then(if (gradient != null) Modifier.background(gradient) else Modifier)
            .then(
                if (isNew) Modifier.drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = border, topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    )
                } else Modifier.border(1.dp, border, pill)
            )
            .pointerInput(onClick, onLong) {
                val longCb: ((Offset) -> Unit)? = onLong?.let { f -> { _: Offset -> f() } }
                detectTapGestures(onLongPress = longCb, onTap = { onClick() })
            }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFolder) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(fc))
        }
        MvText(
            if (useCode && code != null) code else label,
            Modifier.weight(1f, fill = false),
            size = 13.sp, weight = FontWeight.SemiBold, color = textColor, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (isFolder && code != null && !useCode && it.hasVisualOverflow) useCode = true }
        )
        if (count != null) {
            MvText(count.toString(), size = 12.sp, weight = FontWeight.SemiBold, color = countColor, maxLines = 1)
        }
    }
}

/** .fchips : All, one chip per folder, + Folder (two chips wide, snaps) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderChips(
    view: LibView,
    onAll: () -> Unit,
    onFolder: (String) -> Unit,
    onFolderLong: (String) -> Unit,
    onNew: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemW = (this.maxWidth - 8.dp) / 2
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
        ) {
            item(key = "all") {
                FolderChip(itemW, view.folder == null, true, false, "All", view.allCount, null, onAll)
            }
            items(view.fo, key = { it.id }) { f ->
                FolderChip(
                    itemW, view.folder == f.id, false, false, f.name, view.folderCounts[f.id] ?: 0,
                    LibraryLogic.parseHex(f.color), { onFolder(f.id) }, { onFolderLong(f.id) }
                )
            }
            item(key = "new") {
                FolderChip(itemW, false, false, true, "+ Folder", null, null, onNew)
            }
        }
    }
}

/** children keep their natural width and share the leftover width equally (flex: 1 1 auto; min-width: max-content) */
@Composable
internal fun FlexFillRow(containerWidthPx: Int, gap: Dp, content: @Composable () -> Unit) {
    Layout(content = content) { ms, cons ->
        val g = gap.roundToPx()
        val base = ms.map { it.maxIntrinsicWidth(cons.maxHeight.coerceAtMost(100000)) }
        val gaps = g * (ms.size - 1).coerceAtLeast(0)
        val total = base.sum() + gaps
        val extra = if (ms.isEmpty()) 0 else maxOf(0, containerWidthPx - total) / ms.size
        val ps = ms.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(base[i] + extra)) }
        val h = ps.maxOfOrNull { it.height } ?: 0
        val w = maxOf(containerWidthPx, total)
        layout(w, h) {
            var x = 0
            ps.forEach { p ->
                p.placeRelative(x, (h - p.height) / 2)
                x += p.width + g
            }
        }
    }
}

@Composable
internal fun FilterPill(text: String, on: Boolean, fontSize: Float, onClick: () -> Unit) {
    val c = Mv.c
    val pill = RoundedCornerShape(99.dp)
    Row(
        Modifier
            .clip(pill)
            .background(if (on) c.acc else c.s)
            .border(1.dp, if (on) c.acc else c.line, pill)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvText(
            text, size = fontSize.sp, weight = FontWeight.SemiBold,
            color = if (on) c.onAcc else c.ink, maxLines = 1, softWrap = false, align = TextAlign.Center
        )
    }
}

/** .ffil : Genre / Rating / Year / Sort (+ Reset) */
@Composable
fun FilterRow(ff: FilterState, onOpen: (String) -> Unit, onReset: () -> Unit) {
    val fs = (LocalConfiguration.current.screenWidthDp * 0.033f).coerceIn(11f, 14f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cw = constraints.maxWidth
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 10.dp)) {
            FlexFillRow(cw, 6.dp) {
                FilterPill("Genre ▾", ff.g.isNotEmpty(), fs) { onOpen("g") }
                FilterPill(
                    (if (ff.r.isNotEmpty()) (if (ff.r == "0") "Unrated" else "Rating " + ff.r + "+") else "Rating") + " ▾",
                    ff.r.isNotEmpty(), fs
                ) { onOpen("r") }
                FilterPill((if (ff.y.isNotEmpty()) ff.y else "Year") + " ▾", ff.y.isNotEmpty(), fs) { onOpen("y") }
                FilterPill("Sort ▾", ff.s != "new", fs) { onOpen("s") }
                if (ff.any) FilterPill("Reset", false, fs) { onReset() }
            }
        }
    }
}

// ================================================================================================================
// Cards
// ================================================================================================================

private fun rateColor(cls: RateClass, na: Boolean): Color = when {
    na -> Color(0xFFC9CCD6)
    cls == RateClass.Good -> Color(0xFF00E000)
    cls == RateClass.Bad -> Color(0xFFFF6B6B)
    else -> Color(0xFFFFC857)
}

/** .card : poster (2:3), status tag, IMDb rating, title / year, my score */
@Composable
fun MovieCard(
    m: Movie,
    statuses: List<Status>,
    SL: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    upd: Boolean = false,   // .card.upd : being refreshed (dark cover + spinner)
    chg: Boolean = false    // .card.chg : a refresh changed something (the card breathes)
) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    val rating = LibraryLogic.rr(m)
    val score = LibraryLogic.scoreOf(m)
    val tagColor = LibraryLogic.statusColor(m.status, statuses, c.w, c.p, c.n)
    val url = Tmdb.imgUrl(m.poster, "w342")
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .pulse(chg && !upd)
            .then(if (placeholder) Modifier.alpha(0.3f) else Modifier)   // .card.ph: the place the card was picked up from
            .clip(shape)
            .background(cssGradient(160f, c.s2, c.line))
            .plainClickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                if (placeholder) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        Color.White, topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(12.dp.toPx() - w / 2f), style = Stroke(w)
                    )
                }
            }
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // status tag
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(tagColor)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            MvText(LibraryLogic.stTag(m, SL), size = 10.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1.3f, maxLines = 1)
        }
        // IMDb rating
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MvFixed.RateBg)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                MvText("IMDb", size = 10.sp, weight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f), lineHeight = 1.3f)
                MvText(" ", size = 10.sp, weight = FontWeight.Bold, lineHeight = 1.3f)
                if (rating != null) {
                    val cls = Fmt.rcls(com.trackerxx.movieverse.util.Js.parseFloat(rating) * 10)
                    MvText(rating, size = 10.sp, weight = FontWeight.Bold, color = rateColor(cls, false), lineHeight = 1.3f)
                } else {
                    MvText("N/A", size = 10.sp, weight = FontWeight.Bold, color = rateColor(RateClass.None, true), lineHeight = 1.3f)
                }
            }
        }
        // caption
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(8 / 255f, 10 / 255f, 20 / 255f, 0f), Color(8 / 255f, 10 / 255f, 20 / 255f, 0.92f))
                    )
                )
                .padding(start = 9.dp, end = 9.dp, top = 26.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                MvText(m.title, size = 14.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1.2f)
                MvText(
                    m.year + (if (m.tmdbId < 0) " · TV" else ""),
                    size = 12.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 21.75f / 12f
                )
            }
            if (score != 0.0 && !score.isNaN()) {
                MvText("★ " + LibraryLogic.numText(score), size = 11.sp, weight = FontWeight.ExtraBold, color = Color(0xFFFFC857))
            }
        }
        if (upd) {
            // .card.upd .poster::before / ::after
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
            com.trackerxx.movieverse.ui.components.MvSpinner(
                34.dp, 3.dp, Color.White.copy(alpha = 0.35f), Color.White,
                Modifier.align(Alignment.Center), durationMs = 700
            )
        }
    }
}

/** "Loading your library…" / empty-state text of the list */
@Composable
fun EmptyBox(text: String?) {
    val c = Mv.c
    val h = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Box(Modifier.fillMaxWidth().heightIn(min = h).padding(24.dp), contentAlignment = Alignment.Center) {
        if (LibraryLogic.isLoadingText(text)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MvSmallSpinner()
                MvText("Loading your library…", color = c.mut)
            }
        } else {
            MvText(text ?: "", color = c.mut, align = TextAlign.Center)
        }
    }
}

/** #smore : the three pulsing dots under the list while more cards are waiting */
@Composable
fun MoreDots() {
    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
        MvLoadingDots(size = 34.sp, weight = FontWeight.ExtraBold, color = Mv.c.mut, lineHeight = 1f, gap = 3.dp)
    }
}
