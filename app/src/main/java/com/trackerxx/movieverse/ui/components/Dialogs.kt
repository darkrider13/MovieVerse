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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Window setup of a modal <dialog>: black 50% backdrop + 14px blur (blur works on Android 12+). */
@Composable
private fun DialogWindowSetup() {
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.5f)
        // the blur itself is done in MovieVerseApp (Modifier.blur on the page): FLAG_BLUR_BEHIND is ignored by many phones
    }
}

/**
 * Modal dialog, same look as the web <dialog>:
 * width min(94vw, 480px), max-height 90vh, radius 16, 1px glass border, black (dark) / white 72% (light) fill,
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
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Mv.c
    val currentDismiss by rememberUpdatedState(onDismiss)
    val toast = LocalToast.current
    var myDepth by remember { mutableIntStateOf(0) }
    DisposableEffect(toast) {
        toast.dialogs = toast.dialogs + 1
        myDepth = toast.dialogs
        onDispose { toast.dialogs = toast.dialogs - 1 }
    }
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
                    content = content
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
 * Top: grows with the scroll (0 at the very top, up to 36dp). Bottom: always 40dp. The mask is drawn outside the scroll,
 * so it stays at the edges while the content moves under it.
 */
private fun Modifier.pageEdgeFade(scroll: ScrollState, topMax: Dp = 36.dp, bottom: Dp = 40.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = size.height
            if (h <= 0f) return@drawWithContent
            val ft = minOf(scroll.value.toFloat(), topMax.toPx()).coerceAtLeast(0f)
            val fb = minOf(bottom.toPx(), h / 2f)
            val a = (ft / h).coerceIn(0f, 1f)
            val b = ((h - fb) / h).coerceIn(a, 1f)
            val brush = if (ft < 0.5f) {
                Brush.verticalGradient(0f to Color.Black, b to Color.Black, 1f to Color.Transparent)
            } else {
                Brush.verticalGradient(0f to Color.Transparent, a to Color.Black, b to Color.Black, 1f to Color.Transparent)
            }
            drawRect(brush, blendMode = BlendMode.DstIn)
        }

/** Window setup of a full page: no dim, system bar icons follow the theme. */
@Composable
private fun PageWindowSetup() {
    val view = LocalView.current
    val dark = Mv.c.dark
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val ic = WindowCompat.getInsetsController(window, view)
        ic.isAppearanceLightStatusBars = !dark
        ic.isAppearanceLightNavigationBars = !dark
    }
}

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
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Mv.c
    val pageScroll = rememberScrollState()
    val currentBack by rememberUpdatedState(onBack)
    val toast = LocalToast.current
    var myDepth by remember { mutableIntStateOf(0) }
    DisposableEffect(toast) {
        toast.dialogs = toast.dialogs + 1
        myDepth = toast.dialogs
        onDispose { toast.dialogs = toast.dialogs - 1 }
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
        PageWindowSetup()
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
                // top bar
                Row(
                    Modifier.fillMaxWidth().height(52.dp).background(c.nav).padding(horizontal = 6.dp),
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
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                // content
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                            .then(if (edgeFade) Modifier.pageEdgeFade(pageScroll) else Modifier)
                            .verticalScroll(pageScroll)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (edgeFade) 56.dp else 24.dp),
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
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.nav))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(c.bot))
            ToastHost(toast, inDialog = true, visibleHere = myDepth == toast.dialogs)
        }
    }
}
