package com.trackerxx.movieverse.ui.settings

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.BtnSize
import com.trackerxx.movieverse.ui.components.FieldSize
import com.trackerxx.movieverse.ui.components.LocalModal
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.ModalResult
import com.trackerxx.movieverse.ui.components.ModalSpec
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.MvTextField
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.LocalMvText
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvH2
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIconData
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvLabel
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.MvTextDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.pow

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val FB_URL = "https://www.facebook.com/insanul.islam.abirr"
private const val DEV_NAME = "INSANUL ISLAM ABIR"

/**
 * Settings dialog (#sdlg): name, appearance, API keys (tabs + key boxes), Export / Import, Save, credit.
 * Step 10A: everything is edited locally; Save stores the keys and closes.
 *   onSaved(changed, has): called after the keys were stored (10B adds the "Fetching your API key" check there).
 *   onExport / onImport: wired in 10C / 10D.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsDialog(
    dirty: Boolean,
    onClose: () -> Unit,
    onSaved: (changed: Boolean, has: Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val c = Mv.c
    val st = remember { SettingsState() }
    val toast = LocalToast.current
    val modal = LocalModal.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val dark = AppData.theme.dark
    var updateOpen by remember { mutableStateOf(false) }
    if (updateOpen) UpdateFlow(onClose = { updateOpen = false })

    MvDialog(
        onDismiss = onClose,
        padding = PaddingValues(16.dp),
        onClose = onClose
    ) {
        // #sdlg{font-size:13px}
        CompositionLocalProvider(LocalMvText provides MvTextDefaults(13.sp, FontWeight.Normal, c.ink)) {
            Column(Modifier.fillMaxWidth()) {
                // h2 (margin-bottom 4) collapses into the label's margin-top 12 -> the gap is 12, so the h2 adds none
                MvH2(
                    "Settings",
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    size = 17.sp, top = 0.dp, bottom = 0.dp, align = TextAlign.Center
                )

                MvLabel("Your name", size = 13.sp)
                MvTextField(
                    st.name, { st.onName(it) },
                    size = FieldSize.Settings, placeholder = "Your name"
                )

                MvLabel("Appearance", size = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MvButton(
                        onClick = { AppData.theme.toggle() },
                        size = BtnSize.Settings, spacing = 8.dp
                    ) {
                        MvIcon(if (dark) MvIcons.Moon else MvIcons.Sun, 16.dp)
                        MvText(if (dark) "Dark mode" else "Light mode")
                    }
                    // Update: downloads the newest APK from GitHub and installs it over this app
                    MvButton(
                        onClick = { updateOpen = true },
                        size = BtnSize.Settings, spacing = 8.dp
                    ) {
                        MvIcon(MvIcons.Download, 16.dp)
                        MvText("Update")
                    }
                }

                MvLabel("API keys", size = 13.sp)
                // .atabs margin-top 6 (the label's own bottom margin 4 stays outside it)
                Spacer(Modifier.height(6.dp))
                ApiTabs(st)
                Spacer(Modifier.height(8.dp))

                val cur = st.apis.getOrNull(st.cur)
                if (cur == null) {
                    // <p class="note" style="margin:18px 0">
                    MvNote(
                        "No API yet. Tap + to add one.",
                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        size = 12.sp, align = TextAlign.Center
                    )
                } else {
                    androidx.compose.runtime.key(st.cur) {
                        KeyList(
                            st, st.cur, cur,
                            onDeleteApi = {
                                scope.launch {
                                    val r = modal.ask(
                                        ModalSpec(
                                            title = "Remove \"" + cur.name + "\"?",
                                            text = "Its keys are removed too.",
                                            ok = "Remove", danger = true
                                        )
                                    )
                                    if (r is ModalResult.Ok) st.removeApi(st.apis.indexOf(cur))
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        MvButton(
                            "+ Add key",
                            {
                                if (!st.addKey(st.cur)) {
                                    toast.show("Up to 10 keys")
                                } else {
                                    scope.launch {
                                        withFrameNanos { }
                                        cur.listState.scrollToItem(maxOf(0, cur.keys.size - 1))
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(), size = BtnSize.Settings
                        )
                    }
                }

                // label margin-bottom 4 and .srow.ex margin-top 6 collapse to 6
                MvLabel("Export or import your data", size = 13.sp, bottom = 6.dp)
                val glow = rememberGlow(dirty)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MvButton(
                        "Export", onExport,
                        Modifier.weight(1f).glowBehind(dirty, glow),
                        size = BtnSize.SettingsSmall,
                        borderColor = if (dirty) Color(1f, 70f / 255f, 70f / 255f, 0.2f + 0.8f * glow.value) else null
                    )
                    MvButton("Import", onImport, Modifier.weight(1f), size = BtnSize.SettingsSmall)
                }

                Spacer(Modifier.height(14.dp))
                MvButton("Save", { commitAndNotify(st, onSaved) }, Modifier.fillMaxWidth(), kind = BtnKind.Primary, size = BtnSize.Settings)

                Credit(uri::openUri, Modifier.padding(top = 10.dp).fillMaxWidth())
            }
        }
    }
}

/** `$('#ksave').onclick`, first part: store the edited keys. (Close + key check come from the caller.) */
private fun commitAndNotify(st: SettingsState, onSaved: (Boolean, Boolean) -> Unit) {
    val (changed, has) = st.commit()
    onSaved(changed, has)
}

// ================================================================================================================
// API tabs (#atabs)
// ================================================================================================================

/** .atabs : one pill per API + a dashed "+" pill; 6 pills fit the width, swipe to see more, snaps to a pill. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApiTabs(st: SettingsState) {
    val c = Mv.c
    val tabState = rememberLazyListState()
    val snap = rememberSnapFlingBehavior(tabState)
    val scope = rememberCoroutineScope()
    val modal = LocalModal.current
    val toast = LocalToast.current

    // centerTab(): the open tab is scrolled into view
    LaunchedEffect(st.cur, st.apis.size) {
        val info = tabState.layoutInfo
        val it = info.visibleItemsInfo.firstOrNull { x -> x.index == st.cur }
        if (it == null || it.offset < 0 || it.offset + it.size > info.viewportEndOffset + 1) {
            tabState.animateScrollToItem(minOf(st.cur, maxOf(0, st.apis.size)))
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // flex: 0 0 calc((100% - 25px) / 6)
        val w = (this.maxWidth - 25.dp) / 6
        LazyRow(
            state = tabState,
            flingBehavior = snap,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(st.apis.size) { i ->
                val a = st.apis[i]
                MvPill(
                    selected = i == st.cur,
                    onClick = { st.setTab(i) },
                    modifier = Modifier.width(w),
                    fontSize = 11.sp, padH = 2.dp, padV = 3.dp,
                    unselectedColor = c.ink, selectedColor = c.bg,
                    weight = if (i == st.cur) FontWeight.Bold else FontWeight.SemiBold
                ) {
                    MvText(a.name, lineHeight = 1.3f, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                // [data-newapi] : dashed border, muted "+" icon
                val shape = RoundedCornerShape(99.dp)
                val line = c.line
                Box(
                    Modifier
                        .width(w)
                        .height(if (st.apis.isEmpty()) 14.dp else 22.3.dp)
                        .clip(shape)
                        .background(c.s)
                        .drawBehind {
                            val sw = 1.dp.toPx()
                            drawRoundRect(
                                color = line,
                                topLeft = Offset(sw / 2, sw / 2),
                                size = Size(size.width - sw, size.height - sw),
                                cornerRadius = CornerRadius(size.height / 2f),
                                style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                            )
                        }
                        .plainClickable {
                            // addApi()
                            if (st.apis.size >= 20) {
                                toast.show("Up to 20 APIs")
                            } else {
                                scope.launch {
                                    val r = modal.ask(
                                        ModalSpec(
                                            title = "New API", text = "Name it, then add its keys.",
                                            input = true, placeholder = "API name", ok = "Add"
                                        )
                                    )
                                    if (r is ModalResult.Ok) {
                                        val err = st.addApi(r.text)
                                        if (err != null) toast.show(err)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MvIcon(MvIcons.Plus, 12.dp, tint = c.mut)
                }
            }
        }
    }
}

// ================================================================================================================
// Key list (.akl) and one key row (.kr)
// ================================================================================================================

/** .akl : rows of 40dp with 8dp gaps, at most 3 rows visible (138dp), scrolls and snaps to a row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyList(st: SettingsState, i: Int, a: EditApi, onDeleteApi: () -> Unit) {
    val snap = rememberSnapFlingBehavior(a.listState)
    LazyColumn(
        state = a.listState,
        flingBehavior = snap,
        modifier = Modifier.fillMaxWidth().heightIn(max = 138.dp),
        contentPadding = PaddingValues(1.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(a.keys.size) { j ->
            KeyRow(st, i, a, j, onDeleteApi)
        }
    }
}

@Composable
private fun KeyRow(st: SettingsState, i: Int, a: EditApi, j: Int, onDeleteApi: () -> Unit) {
    val c = Mv.c
    val clip = LocalClipboardManager.current
    // the eye goes back to "hidden" whenever the list is drawn again (gen changes)
    var shown by remember(a.gen) { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash) {
            delay(1400)
            flash = false
        }
    }
    val value = a.keys.getOrNull(j) ?: ""
    // the delete-API button sits only on the first row and not for the built-in TMDB / XMDb / OMDb
    val canDelete = j == 0 && a.id != "tmdb" && a.id != "xmdb" && a.id != "omdb"

    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MvTextField(
                value = value,
                onValueChange = { st.setKey(i, j, it) },
                size = FieldSize.Settings,
                placeholder = a.name + " key " + (j + 1),
                password = !shown,
                // .pw input{padding-right:76px}: 76 - 11
                endExtra = 65.dp,
                fixedHeight = 40.dp,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Password)
            )
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // .eye.cp : copy (turns into a green tick for 1.4 s)
                EyeButton(
                    icon = if (flash) MvIcons.CheckGreen else MvIcons.Copy,
                    iconSize = 17.dp,
                    tint = c.mut
                ) {
                    if (value.isNotEmpty()) {
                        clip.setText(AnnotatedString(value))
                        flash = true
                    }
                }
                // .eye : show / hide, accent colour while the key is shown
                EyeButton(icon = MvIcons.Eye, iconSize = 18.dp, tint = if (shown) c.acc else c.mut) {
                    shown = !shown
                }
            }
        }
        if (canDelete) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(c.s2)
                    .border(1.dp, c.line, shape)
                    .plainClickable(onClick = onDeleteApi),
                contentAlignment = Alignment.Center
            ) {
                MvIcon(MvIcons.Trash2, 17.dp, tint = MvFixed.Del)
            }
        }
    }
}

/** .eye : 34x34 round-cornered (8) transparent button */
@Composable
private fun EyeButton(icon: MvIconData, iconSize: Dp, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .plainClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MvIcon(icon, iconSize, tint = tint)
    }
}

// ================================================================================================================
// Credit (.credit) and the red blink of the Export button (blkg)
// ================================================================================================================

/** two right-aligned 9px lines, the developer name opens his Facebook page */
@Composable
private fun Credit(open: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Mv.c
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val line1 = remember(c) {
        buildAnnotatedString {
            append("Developed by ")
            withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)) { append(DEV_NAME) }
        }
    }
    val linkStart = "Developed by ".length
    val linkEnd = linkStart + DEV_NAME.length
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MvText(
            line1,
            Modifier.fillMaxWidth().pointerInput(Unit) {
                detectTapGestures { pos ->
                    val l = layout
                    if (l != null) {
                        val off = l.getOffsetForPosition(pos)
                        if (off in linkStart until linkEnd) open(FB_URL)
                    }
                }
            },
            size = 9.sp, color = c.mut, lineHeight = 1.35f, letterSpacing = 0.2.sp, align = TextAlign.End,
            onTextLayout = { layout = it }
        )
        MvText(
            "© $year All Rights Reserved.",
            Modifier.fillMaxWidth(),
            size = 9.sp, color = c.mut, lineHeight = 1.35f, letterSpacing = 0.2.sp, align = TextAlign.End
        )
    }
}

/** @keyframes blkg : 2.2 s ease-in-out loop (0 -> 1 -> 0) while there is un-exported data */
@Composable
private fun rememberGlow(on: Boolean): State<Float> =
    if (on) {
        rememberInfiniteTransition(label = "exportGlow").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse),
            label = "exportGlowPhase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

/** box-shadow 0 0 10px 1px rgba(255,70,70,.45) at the peak, drawn outside the button (same drawing as the gear button) */
private fun Modifier.glowBehind(on: Boolean, phase: State<Float>): Modifier =
    if (!on) this else this.drawBehind {
        val p = phase.value
        val r = 10.dp.toPx()
        for (k in 0 until 10) {
            val spread = (1 + k).dp.toPx()
            val a = 0.45f * p * (1f - k / 10f).pow(2) * 0.3f
            drawRoundRect(
                color = Color(1f, 70f / 255f, 70f / 255f, a),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(r + spread),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
