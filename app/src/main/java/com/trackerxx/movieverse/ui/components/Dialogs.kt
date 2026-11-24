package com.trackerxx.movieverse.ui.components

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import com.trackerxx.movieverse.R
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** height the content of a fillHeight dialog can use (0.dp = the dialog is as high as its content) */
val LocalDialogMaxH = staticCompositionLocalOf { 0.dp }

/** blur of the page behind a popup (Android 12+; older phones only get the dim) */
private val COVER_BLUR = 14.dp

/** the blur part of a graphicsLayer block: on while a popup is open above [layer] (see ToastState.covered) */
fun GraphicsLayerScope.coverBlur(toast: ToastState, layer: WinLayer?) {
    if (Build.VERSION.SDK_INT >= 31 && toast.covered(layer)) {
        val r = COVER_BLUR.toPx()
        renderEffect = BlurEffect(r, r, TileMode.Clamp)
    }
}

/**
 * Blurs this window's content while a popup is open above it. Only a layer property changes (the picture is not redrawn,
 * nothing recomposes or is measured again), so the blur starts and ends in the same frame as the popup does.
 */
fun Modifier.coveredBlur(toast: ToastState, layer: WinLayer?): Modifier = graphicsLayer { coverBlur(toast, layer) }

/** Window setup of a modal <dialog>: black 50% backdrop (the 14dp blur is on the windows BEHIND it, see coveredBlur). */
@Composable
private fun DialogWindowSetup() {
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.5f)
        // no platform fade in / out: the dim shows and goes away instantly together with the blur of the page behind
        window.setWindowAnimations(R.style.MvNoWindowAnim)
        // the blur itself is done by coveredBlur on every window behind (library, pages, lower popups): FLAG_BLUR_BEHIND is ignored by many phones
    }
}

/**
 * Modal dialog, same look as the web <dialog>:
 * width min(94vw, 480px), max-height 90vh, radius 16, 1px glass border, black fill,
 * soft shadow + 1px top highlight, content scrolls inside. Tap outside closes (if onDismiss != null).
 *
 * widthMax / widthFraction  -> width:min(94vw,480px)  e.g. #mdlg = 380dp / 0.90
 * padding                   -> dialog padding (18 default, #dlg and #sdlg use 16)
 * onClose                   -> draws the corner X (.xb: top 12 / right 12)
 */
@Composable
fun MvDialog(
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    widthMax: Dp = 480.dp,
    widthFraction: Float = 0.94f,
    padding: PaddingValues = PaddingValues(18.dp),
    solidBackground: Color? = null,
    onClose: (() -> Unit)? = null,
    sideMargin: Dp = 18.dp,
    fillHeight: Boolean = false,
    dismissOnOutside: Boolean = false,
    scrollState: ScrollState? = null,
    animateIn: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Mv.c
    val currentDismiss by rememberUpdatedState(onDismiss)
    val toast = LocalToast.current
    var myDepth by remember { mutableIntStateOf(0) }
    val layer = remember { WinLayer(popup = true) }
    // opening: this effect comes before Dialog(), so the blur of everything behind is switched on right before the window is shown
    DisposableEffect(toast) {
        toast.dialogs = toast.dialogs + 1
        toast.addLayer(layer)
        myDepth = toast.dialogs
        onDispose { toast.dialogs = toast.dialogs - 1 }
    }
    // the popup rises smoothly (fade + 18dp up, 260 ms); closing has NO animation, the window is simply removed
    // animateIn = false (Settings): the popup is fully there in its very first frame, together with the dim and the blur behind it
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animateIn) enter.animateTo(1f, tween(260, easing = EaseCss)) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    Dialog(
        onDismissRequest = { currentDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogWindowSetup()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // another popup opened on top of this one: the blur is on the WHOLE window area (not on the popup box itself).
                // A blur on the box alone is cut at the box's bounds, so its shadow / rounded corners turned into a hard-edged
                // grey rectangle behind the new popup; here the box and its shadow blur softly inside a bigger, transparent layer.
                .graphicsLayer { coverBlur(toast, layer) }
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .then(
                    // a plain <dialog> does not close when the dark area around it is tapped
                    if (dismissOnOutside) Modifier.pointerInput(Unit) { detectTapGestures { currentDismiss?.invoke() } } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val boxW = minOf(this.maxWidth * widthFraction, widthMax, this.maxWidth - sideMargin * 2)
            val boxMaxH = this.maxHeight * 0.9f
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier
                    .graphicsLayer {
                        val v = enter.value
                        alpha = v
                        translationY = (1f - v) * rise
                    }
                    .width(boxW)
                    .then(if (fillHeight) Modifier.height(boxMaxH) else Modifier.heightIn(max = boxMaxH))
                    .shadow(
                        elevation = 24.dp, shape = shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clip(shape)
                    .background(solidBackground ?: c.dialogBg)
                    .drawBehind {
                        // inset 0 1px 0 rgba(255,255,255,.2): thin light edge along the top
                        val b = 1.dp.toPx()
                        val rr = RoundRect(
                            Rect(b, b, size.width - b, size.height - b),
                            CornerRadius(16.dp.toPx() - b)
                        )
                        val outer = Path().apply { addRoundRect(rr) }
                        val shifted = Path().apply { addRoundRect(RoundRect(Rect(b, b * 2f, size.width - b, size.height), CornerRadius(16.dp.toPx() - b))) }
                        val crescent = Path.combine(PathOperation.Difference, outer, shifted)
                        drawPath(crescent, Color.White.copy(alpha = 0.2f))
                    }
                    .border(1.dp, MvFixed.GlassBorder, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                // .dsc : padding 4 + margin -4, so the scroll area reaches 4dp past the content (nothing clipped at the content edge)
                val ld = LocalLayoutDirection.current
                val outer = PaddingValues(
                    start = (padding.calculateLeftPadding(ld) - 4.dp).coerceAtLeast(0.dp),
                    top = (padding.calculateTopPadding() - 4.dp).coerceAtLeast(0.dp),
                    end = (padding.calculateRightPadding(ld) - 4.dp).coerceAtLeast(0.dp),
                    bottom = (padding.calculateBottomPadding() - 4.dp).coerceAtLeast(0.dp)
                )
                val scroll = scrollState ?: rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(1.dp)
                        .padding(outer)
                        // soft fade at the bottom edge while there is more content below (--fb, 34px)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fb = (scroll.maxValue - scroll.value).toFloat().coerceIn(0f, 34.dp.toPx())
                            if (fb > 0f) {
                                drawRect(
                                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fb, endY = size.height),
                                    topLeft = Offset(0f, size.height - fb),
                                    size = Size(size.width, fb),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                        .verticalScroll(scroll)
                        .padding(4.dp),
                    content = {
                        val inner = boxMaxH - 2.dp - outer.calculateTopPadding() - outer.calculateBottomPadding() - 8.dp
                        CompositionLocalProvider(LocalDialogMaxH provides (if (fillHeight) inner else 0.dp)) { content() }
                    }
                )
                if (onClose != null) {
                    MvCloseButton(
                        onClose,
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 13.dp, end = 13.dp)
                            .zIndex(60f)
                    )
                }
            }
            // #toast is above every dialog: only the top dialog draws it
            ToastHost(toast, inDialog = true, visibleHere = myDepth == toast.dialogs)
        }
    }
    // closing: written AFTER Dialog() on purpose. When a popup leaves, effects are disposed in reverse order, so this one runs
    // before the window is dismissed: the blur behind is switched off in the same pass in which the popup goes away.
    DisposableEffect(toast) { onDispose { toast.removeLayer(layer) } }
}

/** .xb : 28x28 close button, 14px X, radius 8, no background. */
@Composable
fun MvCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .plainClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MvIcon(MvIcons.Close, 14.dp, tint = Mv.c.ink)
    }
}

/** .xw .xb : zero-height row at the top of the dialog content, X sits at top -7 / right -2 and scrolls with the content. */
@Composable
fun MvXWrap(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.dp)
            .zIndex(60f)
    ) {
        MvCloseButton(
            onClick,
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-7).dp)
                .requiredSize(28.dp)
        )
    }
}

private val OkPopEasing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)

/** #okdlg : green tick success dialog (pop .35s, tick draws .4s after .25s). */
@Composable
fun MvOkDialog(
    title: String,
    message: String?,
    onOk: () -> Unit,
    code: String? = null,
    buttonText: String = "OK"
) {
    val c = Mv.c
    val pop = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, tween(350, easing = OkPopEasing)) }
        launch {
            delay(250)
            draw.animateTo(1f, tween(400, easing = EaseCss))
        }
    }
    val tick = remember { PathParser().parsePathString(MvIcons.OkTickPath).toPath() }
    val tickLen = remember { PathMeasure().also { it.setPath(tick, false) }.length }

    MvDialog(onDismiss = onOk, widthMax = 340.dp, widthFraction = 0.86f) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 12.dp)
                .graphicsLayer {
                    val sc = 0.4f + 0.6f * pop.value
                    scaleX = sc
                    scaleY = sc
                    alpha = pop.value.coerceIn(0f, 1f)
                }
                .drawBehind {
                    drawCircle(Color(22 / 255f, 163 / 255f, 74 / 255f, 0.18f), radius = size.minDimension / 2f + 8.dp.toPx())
                }
                .size(68.dp)
                .clip(CircleShape)
                .background(MvFixed.OkGreen),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(36.dp)) {
                val k = this.size.minDimension / 24f
                val visible = minOf(tickLen, 30f * draw.value)
                if (visible > 0.01f) {
                    val seg = Path()
                    val pm = PathMeasure()
                    pm.setPath(tick, false)
                    pm.getSegment(0f, visible, seg, true)
                    scale(k, k, pivot = Offset.Zero) {
                        drawPath(seg, Color.White, style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        MvText(title, Modifier.fillMaxWidth(), size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        if (!message.isNullOrEmpty()) {
            MvNote(message, Modifier.fillMaxWidth().padding(top = 14.dp), align = TextAlign.Center)
        }
        if (!code.isNullOrEmpty()) {
            MvText(
                code,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.s2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                size = 12.sp, align = TextAlign.Center, fontFamily = FontFamily.Monospace
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            MvButton(buttonText, onOk, kind = BtnKind.Primary)
        }
    }
}

/** #apild : small spinner dialog "Fetching your API key..." */
@Composable
fun MvApiLoadingDialog(
    caption: String,
    modifier: Modifier = Modifier,
    title: String = "Fetching your API key",
    onDismiss: (() -> Unit)? = null
) {
    val c = Mv.c
    MvDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        widthMax = 290.dp,
        widthFraction = 0.78f,
        padding = PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 22.dp)
    ) {
        MvSpinner(46.dp, 4.dp, c.line, c.acc, Modifier.align(Alignment.CenterHorizontally))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            MvText(title, weight = FontWeight.Bold)
            MvLoadingDots(weight = FontWeight.Bold)
        }
        MvText(
            caption,
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 16.dp),
            size = 13.sp, weight = FontWeight.SemiBold, color = c.mut, align = TextAlign.Center
        )
    }
}


/**
 * Soft fade at the top and bottom edge of a scrolling page (web: #dlg.tall .dsc mask).
 * Top: always 24dp (also at rest). Bottom: always 40dp. The mask is drawn outside the scroll,
 * so it stays at the edges while the content moves under it.
 */
private fun Modifier.pageEdgeFade(scroll: ScrollState, topMax: Dp = 24.dp, bottom: Dp = 40.dp, topFollowsScroll: Boolean = false): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = size.height
            if (h <= 0f) return@drawWithContent
            // the top fade is ALWAYS there (also at rest, not only while scrolling): content melts softly under the header
            // topFollowsScroll (movie details): at rest nothing is faded, so the poster can sit close under the header; the fade grows
            // with the scroll up to topMax, so scrolled content still melts under the header
            val ftMax = minOf(topMax.toPx(), h / 2f)
            val ft = if (topFollowsScroll) scroll.value.toFloat().coerceIn(0f, ftMax) else ftMax
            val fb = minOf(bottom.toPx(), h / 2f)
            val a = (ft / h).coerceIn(0f, 1f)
            val b = ((h - fb) / h).coerceIn(a, 1f)
            val brush = Brush.verticalGradient(0f to (if (ft > 0f) Color.Transparent else Color.Black), a to Color.Black, b to Color.Black, 1f to Color.Transparent)
            drawRect(brush, blendMode = BlendMode.DstIn)
        }

/**
 * Soft fade at the top and bottom edge of a scrolling area. Each edge fades only while there is more content to
 * scroll to on that side (top: 0 at the very top, bottom: 0 at the very end), up to [fade] high.
 * Put it BEFORE verticalScroll so the mask stays at the edges while the content moves under it.
 */
fun Modifier.scrollEdgeFade(scroll: ScrollState, fade: Dp = 32.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = size.height
            if (h <= 0f) return@drawWithContent
            val f = fade.toPx()
            val ft = scroll.value.toFloat().coerceIn(0f, f)
            val fb = (scroll.maxValue - scroll.value).toFloat().coerceIn(0f, f)
            if (ft <= 0f && fb <= 0f) return@drawWithContent
            val a = (ft / h).coerceIn(0f, 1f)
            val b = ((h - fb) / h).coerceIn(a, 1f)
            val brush = Brush.verticalGradient(
                0f to (if (ft > 0f) Color.Transparent else Color.Black),
                a to Color.Black,
                b to Color.Black,
                1f to (if (fb > 0f) Color.Transparent else Color.Black)
            )
            drawRect(brush, blendMode = BlendMode.DstIn)
        }

/** Window setup of a full page: no dim, light system bar icons. */
@Composable
private fun PageWindowSetup(instantIn: Boolean = false) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // going back closes the page instantly (no fade / blur while the library shows again)
        window.setWindowAnimations(if (instantIn) R.style.MvNoWindowAnim else R.style.MvPageAnim)
        val ic = WindowCompat.getInsetsController(window, view)
        ic.isAppearanceLightStatusBars = false
        ic.isAppearanceLightNavigationBars = false
    }
}

/** the glow background is only for dark themes (a light theme keeps its plain colour) */
fun isDarkBg(bg: Color): Boolean = bg.red + bg.green + bg.blue < 0.6f

/** the red glow (top-left) and the deep blue glow (right) of the app background, drawn into a w x h area at the current origin */
fun DrawScope.drawGlow(w: Float, h: Float) {
    drawRect(
        Brush.radialGradient(
            0f to Color(0x66C00018), 0.45f to Color(0x33800010), 1f to Color.Transparent,
            center = Offset(w * 0.02f, h * 0.06f), radius = w * 1.0f
        ),
        size = Size(w, h)
    )
    drawRect(
        Brush.radialGradient(
            0f to Color(0x4D2B4BFF), 0.5f to Color(0x261A2FA0), 1f to Color.Transparent,
            center = Offset(w * 1.0f, h * 0.38f), radius = w * 0.9f
        ),
        size = Size(w, h)
    )
}

/** App background (the movie details look): black with a soft red glow in the top-left corner and a deep blue glow on the right (drawn once, no animation). */
fun Modifier.detailGlow(): Modifier = drawBehind { drawGlow(size.width, size.height) }

/**
 * A full-screen page (movie details, subtitles). It is its own window above the library, so the popups that open
 * from it (trailer, person, questions, toasts) still stack on top. Back button / the arrow in the bar = onBack.
 * Layout: top bar (arrow + title), scrolling content (max 640dp wide, centred), optional fixed bottom bar.
 */
@Composable
fun MvPage(
    onBack: () -> Unit,
    title: String,
    bottomBar: (@Composable () -> Unit)? = null,
    edgeFade: Boolean = false,
    tightTop: Boolean = false,   // movie details: the content starts close under the header (no big empty gap)
    scrollable: Boolean = true,
    instantIn: Boolean = false,
    resetKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Mv.c
    // always starts at the very top: not saved/restored (no rememberSaveable) and reset whenever resetKey (the shown movie) changes
    val pageScroll = remember(resetKey) { ScrollState(0) }
    LaunchedEffect(resetKey) { pageScroll.scrollTo(0) }
    val currentBack by rememberUpdatedState(onBack)
    val toast = LocalToast.current
    var myDepth by remember { mutableIntStateOf(0) }
    val layer = remember { WinLayer(popup = false) }
    DisposableEffect(toast) {
        toast.dialogs = toast.dialogs + 1
        toast.addLayer(layer)
        myDepth = toast.dialogs
        onDispose { toast.dialogs = toast.dialogs - 1; toast.removeLayer(layer) }
    }
    Dialog(
        onDismissRequest = { currentBack() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PageWindowSetup(instantIn)
        Box(Modifier.fillMaxSize().background(c.bg).then(if (isDarkBg(c.bg)) Modifier.detailGlow() else Modifier)) {
          // the page is blurred while a popup (trailer, person, question ...) is open on top of it
          Box(Modifier.fillMaxSize().coveredBlur(toast, layer)) {
            // movie details: the very top is black (same as the status bar) and fades softly into the red / blue glow: one background, no bar
            if (edgeFade) {
                val sbH = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
                Box(
                    Modifier.fillMaxWidth().height(sbH + 52.dp + 44.dp).background(
                        Brush.verticalGradient(0f to c.nav, 0.5f to c.nav.copy(alpha = 0.72f), 1f to Color.Transparent)
                    )
                )
            }
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
                // top bar
                Row(
                    // edgeFade (movie details): no separate bar, the soft black fade below it is the top; other pages: a bar in the status bar colour
                    Modifier.fillMaxWidth().height(52.dp).then(if (edgeFade) Modifier else Modifier.background(c.nav)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).plainClickable { currentBack() },
                        contentAlignment = Alignment.Center
                    ) { MvIcon(MvIcons.Back, 22.dp, tint = c.ink) }
                    MvText(
                        title, Modifier.weight(1f).padding(horizontal = 6.dp),
                        size = 16.sp, weight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (!edgeFade) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                // content
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                            .then(if (edgeFade) Modifier.pageEdgeFade(pageScroll, topFollowsScroll = tightTop) else Modifier)
                            // scrollable = false: the page itself stays still, the content decides what scrolls
                            .then(if (scrollable) Modifier.verticalScroll(pageScroll) else Modifier)
                            .padding(start = 16.dp, end = 16.dp, top = if (edgeFade && tightTop) 2.dp else if (edgeFade) 22.dp else 8.dp, bottom = if (!scrollable) 0.dp else if (edgeFade) 56.dp else 24.dp),
                        content = content
                    )
                }
                if (bottomBar != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Box(Modifier.fillMaxWidth().background(c.nav), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) { bottomBar() }
                    }
                }
            }
            // status bar / navigation bar colours, like MovieVerseApp
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(if (edgeFade) Color.Transparent else c.nav))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(c.bot))
          }
            ToastHost(toast, inDialog = true, visibleHere = myDepth == toast.dialogs)
        }
    }
}
