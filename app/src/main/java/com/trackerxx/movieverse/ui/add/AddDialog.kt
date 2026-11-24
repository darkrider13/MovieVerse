package com.trackerxx.movieverse.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.trackerxx.movieverse.ui.components.MvPage
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.net.QItem
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.BtnSize
import com.trackerxx.movieverse.ui.components.FieldSize
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.MvTextField
import com.trackerxx.movieverse.ui.components.MvXWrap
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.mvTextStyle
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import com.trackerxx.movieverse.util.RateClass
import kotlinx.coroutines.delay

/**
 * The Add flow: the search part is a popup, the movie itself (new or already in the library) is a full page
 * (MoviePage) with the Subtitles page on top of it when that option is opened.
 */
@Composable
fun AddDialog(flow: AddFlow) {
    val ctx = LocalContext.current
    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flow.subs.permDone?.complete(granted)
    }
    SideEffect {
        flow.subs.ctx = ctx.applicationContext
        flow.subs.launchPerm = { permL.launch(it) }
    }
    if (flow.open && flow.phase == "find") {
        MvDialog(
            onDismiss = { flow.close() },
            padding = PaddingValues(16.dp),
            sideMargin = 16.dp,
            fillHeight = false
        ) {
            FindPhase(flow)
        }
    }
    if (flow.open && flow.phase == "form") MoviePage(flow)
    // pages / dialogs that open above the movie page (later = on top); the person dialog can also come back after the page closed
    if (flow.open && flow.subs.open) SubtitlePage(flow.subs)
    PersonDialog(flow)
    TrailerDialog(flow)
}

// ================================================================================================================
// search phase
// ================================================================================================================

@Composable
private fun FindPhase(flow: AddFlow) {
    val c = Mv.c
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(120)
        try { focus.requestFocus() } catch (e: Exception) { }
    }
    Column(Modifier.fillMaxWidth()) {
        MvText(
            "Add to Library", Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            size = 16.sp, weight = FontWeight.Bold, align = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        MvTextField(
            value = flow.query,
            onValueChange = { flow.onQuery(it) },
            modifier = Modifier.focusRequester(focus),
            placeholder = "Type a movie name",
            size = FieldSize.Dialog,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { flow.search(); fm.clearFocus() }),
            endExtra = if (flow.query.isNotEmpty()) 24.dp else 0.dp,
            trailing = {
                if (flow.query.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).offset(x = 24.dp).size(16.dp).plainClickable { flow.onQuery("") }
                    ) {
                        MvIcon(MvIcons.Close, 16.dp, tint = Color.White)
                    }
                }
            }
        )
        val f = flow.find
        if (f is FindUi.Suggest) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth()) {
                f.items.forEach { x -> SuggestRow(x) { fm.clearFocus(); flow.pickResult(x) } }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(10.dp))
            when (f) {
                is FindUi.NoKey -> NoteP("Add your TMDB API key in Settings to search with posters. You can still add a movie without a poster.")
                is FindUi.Searching -> NoteP("Searching…")
                is FindUi.Results ->
                    if (f.items.isEmpty()) NoteP("No results. Try another spelling, or add it without a poster.")
                    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        f.items.forEach { x -> HitRow(x, flow) { flow.pickResult(x) } }
                    }
                is FindUi.Error -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MvText(f.message, size = 12.sp, color = c.mut, align = TextAlign.Center)
                    MvButton("Retry", { flow.search() }, kind = BtnKind.Primary, size = BtnSize.Dialog)
                }
                else -> {}
            }
        }
        Spacer(Modifier.height(14.dp))
        MvButton("Cancel", { flow.close() }, Modifier.fillMaxWidth(), kind = BtnKind.Red, size = BtnSize.Dialog)
    }
}

/** <p class="note"> inside the results box: 12px margins above and below */
@Composable
private fun NoteP(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        MvText(text, size = 12.sp, color = Mv.c.mut)
    }
}

/** .sg : one suggestion (poster, title, year) */
@Composable
private fun SuggestRow(x: QItem, onClick: () -> Unit) {
    val c = Mv.c
    Column(Modifier.fillMaxWidth().plainClickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shape = RoundedCornerShape(5.dp)
            if (!x.poster.isNullOrEmpty()) {
                AsyncImage(
                    model = Tmdb.imgUrl(x.poster, "w92"), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp, 51.dp).clip(shape).background(c.s2)
                )
            } else {
                Box(Modifier.size(34.dp, 51.dp).clip(shape).background(c.s2))
            }
            MvText(x.title, Modifier.weight(1f), size = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MvText(x.releaseDate.take(4) + (if (x.tv) " · TV" else ""), size = 11.sp, color = c.mut, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** .hit : one search result (poster, title, year, overview, "Already added") */
@Composable
private fun HitRow(x: QItem, flow: AddFlow, onClick: () -> Unit) {
    val c = Mv.c
    val shape = RoundedCornerShape(10.dp)
    val ex = flow.existing(x.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.line, shape)
            .plainClickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ps = RoundedCornerShape(6.dp)
        if (!x.poster.isNullOrEmpty()) {
            AsyncImage(
                model = Tmdb.imgUrl(x.poster, "w92"), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp, 54.dp).clip(ps).background(c.s2)
            )
        } else {
            Box(Modifier.size(36.dp, 54.dp).clip(ps).background(c.s2))
        }
        Column(Modifier.weight(1f)) {
            MvText(x.title, weight = FontWeight.Bold)
            MvText(x.releaseDate.take(4) + (if (x.tv) " · TV" else ""), size = 12.sp, color = c.mut)
            if (x.overview.isNotEmpty()) {
                MvText(
                    x.overview, Modifier.padding(top = 2.dp), size = 11.5.sp, color = c.mut, lineHeight = 1.35f,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            if (ex != null) {
                MvText(
                    "Already added · " + (AppData.SL[ex.status] ?: ex.status),
                    size = 10.sp, weight = FontWeight.Bold, color = c.w, lineHeight = 1.6f
                )
            }
        }
    }
}

// ================================================================================================================
// form phase
// ================================================================================================================

/** fmtRt(m): "2h 28m" ("Not available" when TMDB has no runtime, "" while unknown) */
private fun fmtRt(m: Movie): String {
    if (!m.has("runtime")) return ""
    val r = m.num("runtime") ?: 0.0
    if (r == 0.0) return "Not available"
    val h = Math.floor(r / 60).toInt()
    val mi = (r % 60).toInt()
    val t = (if (h > 0) "${h}h" else "") + (if (h > 0 && mi > 0) " " else "") + (if (mi > 0) "${mi}m" else "")
    return t + (if (m.tmdbId < 0) " / episode" else "")
}

@Composable
private fun DlgLabel(text: String, top: Dp = 10.dp, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = top, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MvText(text, weight = FontWeight.Bold)
        extra()
    }
}

/** a read-only text box that looks like the dialog's inputs; badge = the age rating shown right after the text (PG-13) */
@Composable
private fun StaticField(text: String, modifier: Modifier = Modifier, endPad: Dp = 10.dp, badge: String? = null, trailer: Boolean = false) {
    val c = Mv.c
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.s2)
            .border(1.dp, c.line, shape)
            .padding(start = 11.dp, end = (if (trailer) 44.dp else if (badge != null) 12.dp else endPad) + 1.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvText(text, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            val bs = RoundedCornerShape(6.dp)
            Box(Modifier.clip(bs).background(c.s).border(1.dp, c.line, bs).padding(horizontal = 7.dp, vertical = 3.dp)) {
                MvText(badge, size = 11.sp, weight = FontWeight.Bold, color = c.mut, lineHeight = 1.2f, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The age rating as "CODE · full meaning" (e.g. "PG-13 · Parents Strongly Cautioned"). Unknown codes are shown as they are.
 * Covers US movie (MPAA), US TV, Indian (CBFC) and UK (BBFC) ratings.
 */
private fun certFull(raw: String?): String? {
    val c0 = raw?.trim().orEmpty()
    if (c0.isEmpty()) return null
    val key = c0.uppercase().replace(" ", "").replace("_", "-")
    val meaning = when (key) {
        "G" -> "General Audiences"
        "PG" -> "Parental Guidance Suggested"
        "PG-13" -> "Parents Strongly Cautioned"
        "R" -> "Restricted"
        "NC-17" -> "Adults Only"
        "NR", "UNRATED" -> "Not Rated"
        "TV-Y" -> "All Children"
        "TV-Y7", "TV-Y7-FV" -> "Directed to Older Children"
        "TV-G" -> "General Audience"
        "TV-PG" -> "Parental Guidance Suggested"
        "TV-14" -> "Parents Strongly Cautioned"
        "TV-MA" -> "Mature Audiences Only"
        "U" -> "Universal, Suitable for All"
        "UA", "U/A" -> "Parental Guidance for Under 12"
        "U/A7+", "UA7+" -> "Parental Guidance for Under 7"
        "U/A13+", "UA13+" -> "Parental Guidance for Under 13"
        "U/A16+", "UA16+" -> "Parental Guidance for Under 16"
        "A" -> "Adults Only"
        "S" -> "Specialised Audiences"
        "12A" -> "12 and Over, Under 12 with Adult"
        "12" -> "12 and Over"
        "15" -> "15 and Over"
        "18" -> "18 and Over, Adults Only"
        "18+" -> "Adults Only"
        "16+" -> "16 and Over"
        "13+" -> "13 and Over"
        "7+" -> "7 and Over"
        "ALL" -> "All Ages"
        else -> null
    }
    return if (meaning != null) "$c0 · $meaning" else c0
}

@Composable
private fun MoviePage(flow: AddFlow) {
    val c = Mv.c
    val m = flow.pick ?: return
    val lock = m.tmdbId != 0L
    val busy = flow.busy
    // redraw when details changed in the background
    @Suppress("UNUSED_VARIABLE") val rev = flow.infoRev
    val focus = remember { FocusRequester() }
    val copyName = rememberCopyName()

    MvPage(
        onBack = { flow.close() },
        title = "Movie details",
        edgeFade = true,
        resetKey = m
    ) {
        // ---- poster, title (year), overview, Trailer + Subtitle buttons ---------------------------------------------------
        PvHeader(flow, m, copyName)

        // ---- title -----------------------------------------------------------------------------------------------------
        DlgLabel("Title", top = 18.dp)
        if (lock) StaticField(flow.ft, badge = certFull(m.str("cert")))
        else MvTextField(flow.ft, { flow.ft = it }, Modifier.focusRequester(focus), placeholder = "Title", size = FieldSize.Dialog)

        // ---- duration + country ------------------------------------------------------------------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (lock) {
                    DlgLabel("Duration", top = 14.dp)
                    StaticField(fmtRt(m))
                } else {
                    DlgLabel("Year", top = 14.dp)
                    MvTextField(flow.fy, { flow.fy = it }, placeholder = "Year", size = FieldSize.Dialog)
                }
            }
            Column(Modifier.weight(1f)) {
                DlgLabel("Country", top = 14.dp)
                if (lock) StaticField(flow.fc)
                else MvTextField(flow.fc, { flow.fc = it }, placeholder = "Country", size = FieldSize.Dialog)
            }
        }

        // ---- info tiles ------------------------------------------------------------------------------------------------------
        Spacer(Modifier.height(10.dp))
        InfoGrid(flow, m)

        // loading boxes for the cast until it has arrived
        if (busy && lock) CastSkeleton()

        // while loading: Status / Folder / My rating / Save are already there as shimmering boxes (#dlg.hold)
        if (busy && lock) LoadingBoxes(flow, m)

        if (!busy) {
            // ---- cast & crew ------------------------------------------------------------------------------------------------
            flow.cast.data?.let { cd -> CastBlock(cd) { id -> flow.person.openPerson(id) } }

            // ---- status ---------------------------------------------------------------------------------------------------
            DlgLabel("Status", top = 16.dp)
            StatusBar(flow)

            // ---- folder ---------------------------------------------------------------------------------------------------
            DlgLabel("Folder", top = 16.dp)
            FolderPills(flow)

            // ---- my rating --------------------------------------------------------------------------------------------------
            if (!Enrich.unrel(m)) RatingBlock(flow)

            if (flow.isEdit && m.added != 0L) {
                val fa = AppData.folders.firstOrNull { it.id == m.folder }
                MvText(
                    (if (fa != null) "Added to " + fa.name + " · " else "Added · ") + Fmt.fmtTs(m.added),
                    Modifier.fillMaxWidth().padding(top = 18.dp), size = 12.sp, color = c.mut, align = TextAlign.Center
                )
            }
            // Save / Delete: the very end of the content (scrolls with the page, no separate bar)
            if (!busy) {
                Box(Modifier.fillMaxWidth().padding(top = 22.dp)) { PageActions(flow, m, focus) }
            }
        }
    }
}

/** #dlg.hold : Status, Folder, My rating and Save as grey shimmering boxes until everything has loaded */
@Composable
private fun LoadingBoxes(flow: AddFlow, m: Movie) {
    val r = RoundedCornerShape(12.dp)
    DlgLabel("Status", top = 16.dp)
    ShimBox(Modifier.fillMaxWidth().height(48.dp), r)
    DlgLabel("Folder", top = 16.dp)
    ShimBox(Modifier.fillMaxWidth().height(40.dp), r)
    if (!Enrich.unrel(m)) {
        DlgLabel("My rating")
        ShimBox(Modifier.fillMaxWidth().height(40.dp), r)
    }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShimBox(Modifier.weight(1f).height(38.dp), r)
        if (flow.isEdit) ShimBox(Modifier.weight(1f).height(38.dp), r)
    }
}

/** Save (and Delete for a movie that is in the library) at the end of the page content; was: the fixed bar at the bottom: Save (and Delete for a movie that is in the library) */
@Composable
private fun PageActions(flow: AddFlow, m: Movie, focus: FocusRequester) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MvButton(
            flow.saveLabel,
            {
                if (!flow.save()) {
                    try { focus.requestFocus() } catch (e: Exception) { }
                }
            },
            Modifier.weight(1f),
            kind = BtnKind.Primary, size = BtnSize.Dialog, enabled = flow.saveEnabled
        )
        if (flow.isEdit) {
            MvButton("Delete", { flow.delete() }, Modifier.weight(1f), danger = true, size = BtnSize.Dialog)
        }
    }
}

/** #fsb : status buttons, three wide, scrolls sideways */
@Composable
private fun StatusBar(flow: AddFlow) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemW = (this.maxWidth - 8.dp - 12.dp) / 3
        val scroll = rememberScrollState()
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.s2)
                .horizontalScroll(scroll)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppData.statuses.forEach { st ->
                val on = flow.fstat == st.id
                val bs = RoundedCornerShape(9.dp)
                Box(
                    Modifier
                        .width(itemW)
                        .height(32.dp)
                        .clip(bs)
                        .background(if (on) c.acc else Color.Transparent)
                        .plainClickable { flow.selectStatus(st.id) }
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MvText(
                        AppData.SL[st.id] ?: "", size = 12.sp, weight = FontWeight.Bold, lineHeight = 1.15f,
                        color = if (on) c.onAcc else c.mut, maxLines = 2, overflow = TextOverflow.Ellipsis, align = TextAlign.Center
                    )
                }
            }
        }
        // the selected status is always fully visible: scroll only when it is not
        val dens = LocalDensity.current
        val viewportPx = with(dens) { this@BoxWithConstraints.maxWidth.toPx() }
        LaunchedEffect(flow.fstat) {
            val idx = AppData.statuses.indexOfFirst { it.id == flow.fstat }
            if (idx >= 0) {
                val itemPx = with(dens) { itemW.toPx() }
                val gapPx = with(dens) { 6.dp.toPx() }
                val padPx = with(dens) { 4.dp.toPx() }
                val l = padPx + idx * (itemPx + gapPx)
                val r = l + itemPx
                val v0 = scroll.value.toFloat()
                val v1 = v0 + viewportPx
                if (l < v0 + 2) scroll.scrollTo(maxOf(0f, l - 4f).toInt())
                else if (r > v1 - 2) scroll.scrollTo((r - viewportPx + 4f).toInt())
            }
        }
    }
}

/** #ffol : the folders of the chosen status */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderPills(flow: AddFlow) {
    val c = Mv.c
    val fo = AppData.folders.filter { it.status == flow.fstat }
    if (fo.isEmpty()) {
        MvText(
            "No folders in " + (AppData.SL[flow.fstat] ?: "this status") + " yet. Create one from the library.",
            size = 12.sp, color = c.mut
        )
    } else {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fo.forEach { f ->
                MvPill(f.name, flow.fsel == f.id, { flow.toggleFolder(f.id) }, fontSize = 12.5.sp, padH = 11.dp, padV = 4.dp)
            }
        }
    }
}

// ---- my rating ------------------------------------------------------------------------------------------------------

private const val STAR = "M13 3l2.9 6.2 6.8.8-5 4.7 1.3 6.7L13 18l-6 3.4 1.3-6.7-5-4.7 6.8-.8z"

/** .rs : ten stars, filled up to the rating (stars are mask shapes with a 1.6 round outline) */
@Composable
private fun StarMeter(value: Double) {
    val path = remember { PathParser().parsePathString(STAR).toPath() }
    val gold = Color(0xFFFFB52E)
    val grey = Color(140 / 255f, 145 / 255f, 175 / 255f, 0.32f)
    val p = (value / 10.0).toFloat().coerceIn(0f, 1f)
    Canvas(
        Modifier
            .size(205.dp, 20.5.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val cell = size.height
        val k = cell / 26f
        for (i in 0 until 10) {
            translate(left = i * cell) {
                scale(k, k, pivot = Offset.Zero) {
                    drawPath(path, Color.Black)
                    drawPath(path, Color.Black, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        drawRect(
            Brush.horizontalGradient(0f to gold, p to gold, p to grey, 1f to grey),
            blendMode = BlendMode.SrcIn
        )
    }
}

@Composable
private fun RatingBlock(flow: AddFlow) {
    val c = Mv.c
    val v = flow.ratingValue()
    val vs = if (v == Math.floor(v)) Js.toFixed(v, 0) else Js.toFixed(v, 1)
    DlgLabel("My rating") {
        MvText(if (v != 0.0) "($vs out of 10)" else "(out of 10)", size = 12.sp, weight = FontWeight.Bold, color = c.mut)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        var focused by remember { mutableStateOf(false) }
        val fm = LocalFocusManager.current
        BasicTextField(
            value = flow.fr,
            onValueChange = { flow.onFrChange(it) },
            modifier = Modifier
                .padding(start = 6.dp)
                .size(44.dp, 30.dp)
                .onFocusChanged { st ->
                    if (st.isFocused && !focused) flow.onFrFocus()
                    if (!st.isFocused && focused) flow.fmtFr()
                    focused = st.isFocused
                },
            singleLine = true,
            textStyle = mvTextStyle(13.sp, FontWeight.Normal, c.ink, align = TextAlign.Center),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { flow.fmtFr(); fm.clearFocus() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                    if (flow.fr.isEmpty()) {
                        MvText(
                            if (flow.fr.isEmpty() && focused) flow.frKeep.ifEmpty { "0.00" } else "", size = 13.sp, color = c.ink.copy(alpha = 0.25f),
                            align = TextAlign.Center
                        )
                    }
                    inner()
                }
            }
        )
        StarMeter(v)
        if (v != 0.0) {
            val cls = Fmt.rcls(v * 10)
            MvText(
                "$vs / 10", size = 12.sp, modifier = Modifier.offset(x = (-6).dp),
                color = when (cls) { RateClass.Good -> c.cg; RateClass.Mid -> c.cy; RateClass.Bad -> c.cr; else -> c.mut }
            )
        }
    }
}


/**
 * Poster (112 x 168) + title + summary + Trailer + Subtitle (pvFit): the right column ends exactly at the poster's bottom edge.
 * If a long title wraps, the closed summary loses a line (or the title gets smaller) instead of the buttons crossing the edge.
 */
@Composable
private fun PvHeader(flow: AddFlow, m: Movie, copyName: (String) -> Unit) {
    val c = Mv.c
    val dens = LocalDensity.current
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val posterH = 168.dp
    // opened from a person's movie list: nothing about the title is known yet -> grey shimmer boxes instead of a "New movie" text
    val loadingTitle = m.title.isEmpty() && flow.placeholder
    val shown = if (m.title.isNotEmpty()) m.title + (if (m.year.isNotEmpty()) " (" + m.year + ")" else "") else "New movie"
    val bx = InfoModel.boxOf(m)
    // "Running": released in cinemas within the last 21 days. It no longer waits for Budget + Collection (a new release often has no collection yet).
    val runNow = m.tmdbId > 0 && !m.truthy("direct") && !Enrich.unrel(m) && m.rel.isNotEmpty() &&
        (System.currentTimeMillis() - Fmt.dayStartMs(m.rel)) / 864e5 < 21
    val titleAnn = buildAnnotatedString {
        append(shown)
        if (runNow) {
            withStyle(SpanStyle(color = Color(0xFFA855F7), fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append("\u00A0\u00A0Running") }
        } else if (bx != null && !bx.bo && !bx.go) {
            val col = when (bx.tone) {
                VerdictTone.Running -> Color(0xFFA855F7)
                VerdictTone.Good -> c.w
                VerdictTone.Average -> Color(0xFFF5C518)
                VerdictTone.Flop -> Color(0xFFFF8C42)
                VerdictTone.Disaster -> Color(0xFFFF4D4D)
            }
            withStyle(SpanStyle(color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append("\u00A0\u00A0" + bx.verdict) }
        }
    }
    val ov = m.str("ov") ?: ""
    var openOv by remember(m) { mutableStateOf(false) }
    val showTrailer = m.tmdbId != 0L

    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(112.dp, posterH).clip(RoundedCornerShape(12.dp)).background(c.s2)) {
            // the poster loads like every other part of the page: the same grey shimmer box until the picture is there
            var posterLoading by remember(m.poster) { mutableStateOf(true) }
            if (!m.poster.isNullOrEmpty()) {
                AsyncImage(
                    model = Tmdb.imgUrl(m.poster, "w342"), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { st -> posterLoading = st is coil.compose.AsyncImagePainter.State.Loading || st is coil.compose.AsyncImagePainter.State.Empty }
                )
            }
            // no poster known yet (details still arriving) or the picture is still loading
            if ((m.poster.isNullOrEmpty() && flow.placeholder) || (!m.poster.isNullOrEmpty() && posterLoading)) {
                ShimBox(Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
            }
            if (flow.dup) {
                // green bookmark in the poster's top-right corner; a tap shows the status for a moment
                var showSt by remember(m) { mutableStateOf(false) }
                LaunchedEffect(showSt) {
                    if (showSt) {
                        kotlinx.coroutines.delay(2200)
                        showSt = false
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(30.dp, 34.dp)
                        .plainClickable { showSt = !showSt },
                    contentAlignment = Alignment.TopEnd
                ) {
                    // the icon's own empty margin (5/24 of its width at the right, 3/28 at the top) is moved out, so the green shape touches the poster's top-right edge
                    MvIcon(MvIcons.BookmarkGreen, 30.dp, Modifier.offset(x = 9.64.dp, y = (-3.21).dp))
                }
                if (showSt) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp, start = 6.dp, end = 6.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        MvText(
                            "Already added \u00B7 " + (AppData.SL[m.status] ?: m.status),
                            size = 10.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1.3f, maxLines = 2
                        )
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wPx = with(dens) { maxWidth.roundToPx() }
            val btnN = (if (showTrailer) 1 else 0) + 1
            // [summary lines, title size] : first step that fits under the poster's bottom edge
            val fit: Pair<Int, Int> = if (openOv || wPx <= 0) {
                3 to 21
            } else {
                val limit = with(dens) { posterH.toPx() } + 1f
                val steps = listOf(3 to 21, 2 to 21, 3 to 18, 2 to 18, 2 to 16, 1 to 16)
                var pick = steps.last()
                for (st in steps) {
                    val th = tm.measure(
                        titleAnn,
                        style = com.trackerxx.movieverse.ui.theme.mvTextStyle(st.second.sp, FontWeight.Bold, c.ink, 1.25f),
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = wPx)
                    ).size.height
                    var h = th.toFloat()
                    if (ov.isNotEmpty()) {
                        h += with(dens) { 4.dp.toPx() } + tm.measure(
                            androidx.compose.ui.text.AnnotatedString(ov),
                            style = com.trackerxx.movieverse.ui.theme.mvTextStyle(13.sp, FontWeight.Normal, c.mut, 19f / 13f),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = st.first,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = wPx)
                        ).size.height
                    }
                    h += with(dens) { (8.dp + 46.dp * btnN + 6.dp * (btnN - 1)).toPx() }
                    if (h <= limit) {
                        pick = st
                        break
                    }
                }
                pick
            }
            Column(Modifier.fillMaxWidth().heightIn(min = posterH)) {
                if (loadingTitle) {
                    ShimBox(Modifier.padding(top = 4.dp).size(170.dp, 24.dp), RoundedCornerShape(8.dp))
                    if (ov.isEmpty()) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimBox(Modifier.fillMaxWidth().height(13.dp), RoundedCornerShape(6.dp))
                            ShimBox(Modifier.fillMaxWidth(0.8f).height(13.dp), RoundedCornerShape(6.dp))
                        }
                    }
                } else MvText(
                    titleAnn,
                    size = fit.second.sp, weight = FontWeight.Bold, lineHeight = 1.25f,
                    modifier = Modifier.plainClickable { copyName(if (m.title.isNotEmpty()) shown else "") }
                )
                if (ov.isNotEmpty()) {
                    val maxOpen = with(dens) { (19.sp * 9).toDp() }
                    Box(
                        Modifier.padding(top = 4.dp).plainClickable { openOv = !openOv }
                            .then(if (openOv) Modifier.heightIn(max = maxOpen).verticalScroll(rememberScrollState()) else Modifier)
                    ) {
                        MvText(
                            ov, size = 13.sp, weight = FontWeight.Normal, color = c.mut, lineHeight = 19f / 13f,
                            maxLines = if (openOv) Int.MAX_VALUE else fit.first, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showTrailer) {
                        val st = flow.cast.trailerState
                        QuickOpt(
                            icon = MvIcons.YtFill, red = true,
                            title = if (st == 2) "Trailer not found" else "Trailer",
                            sub = if (st == 2) "Not available on YouTube" else "Watch on YouTube",
                            loading = st == 0, na = st == 2,
                            onClick = { flow.cast.openTrailer(m) }
                        )
                    }
                    val sp = flow.subs.probe
                    QuickOpt(
                        icon = MvIcons.Subtitle, red = false,
                        title = if (sp == 2) "Subtitle not found" else "Subtitle",
                        sub = if (sp == 2) "Not available on SubSource" else "Bangla \u00B7 English",
                        loading = sp == 0, na = sp == 2,
                        onClick = { flow.subs.openFor(m) }
                    )
                }
            }
        }
    }
}

/** .qopt : the wide Trailer (red) / Subtitle (grey) buttons next to the poster; loading = shimmer line, na = greyed and not tappable */
@Composable
private fun QuickOpt(
    icon: com.trackerxx.movieverse.ui.theme.MvIconData,
    red: Boolean,
    title: String,
    sub: String,
    loading: Boolean,
    na: Boolean,
    onClick: () -> Unit
) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    val plain = loading || na
    val bg: Brush = when {
        na -> SolidColor(c.s2)
        red -> Brush.linearGradient(listOf(Color(0x57FF2828), Color(0x33960000)))
        else -> Brush.linearGradient(listOf(Color(0x42AAB0C4), Color(0x295A6074)))
    }
    val bd: Color = when {
        na -> c.line
        red -> Color(0x80FF5050)
        else -> Color(0x52CDD2E4)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, bd, shape)
            .plainClickable(enabled = !plain, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        MvIcon(icon, 22.dp, Modifier.alpha(if (na) 0.55f else 1f), tint = c.ink)
        Column(Modifier.weight(1f).alpha(if (na) 0.75f else 1f)) {
            MvText(title, size = 13.sp, weight = FontWeight.ExtraBold, lineHeight = 1.15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) {
                val a by androidx.compose.animation.core.rememberInfiniteTransition(label = "qsh").animateFloat(
                    initialValue = 0.08f, targetValue = 0.28f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween<Float>(600),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "qsh-a"
                )
                Box(Modifier.padding(top = 3.dp).size(74.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(c.ink.copy(alpha = a)))
            } else {
                MvText(sub, size = 10.5.sp, weight = FontWeight.SemiBold, color = c.mut, lineHeight = 1.15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!plain) MvIcon(MvIcons.Chevron, 16.dp, tint = c.ink)
    }
}
