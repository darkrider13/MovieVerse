package com.trackerxx.movieverse.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.trackerxx.movieverse.ui.components.LocalDialogMaxH
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.net.AwRow
import com.trackerxx.movieverse.net.PCredit
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvPage
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.util.Js

/**
 * Person page: a full-screen page like the movie details (MvPage, same black / red-blue glow look). It opens above the movie page,
 * the back arrow / back button closes it and the movie details are there again.
 */
@Composable
fun PersonDialog(flow: AddFlow) {
    val pf = flow.person
    if (!pf.open) return
    MvPage(
        onBack = { pf.close() },
        title = "Person details",
        edgeFade = true,
        // the page itself stays still, the Movies / Awards lists scroll inside it
        scrollable = false
    ) {
        PersonBody(pf)
    }
}

/**
 * Soft fade at the top edge of a scrolling list: while the list is scrolled, the rows that slide up under the tabs melt away
 * softly (up to [fade] high). At the very top nothing is faded; a few pixels of scroll fade in gradually.
 */
private fun Modifier.topFade(state: LazyListState, fade: Dp = 30.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = fade.toPx()
            val off = if (state.firstVisibleItemIndex > 0) f else state.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, f)
            if (off > 0f) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - off / f), Color.Black), startY = 0f, endY = f),
                    size = Size(size.width, f),
                    blendMode = BlendMode.DstIn
                )
            }
        }

@Composable
private fun NoteP(text: String) {
    MvText(text, Modifier.padding(vertical = 14.dp), size = 14.sp, color = Mv.c.mut)
}

@Composable
private fun RetryBox(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MvText(text, size = 14.sp, color = Mv.c.mut, align = TextAlign.Center)
        MvButton("Retry", onRetry, kind = BtnKind.Primary)
    }
}

@Composable
private fun Ph3(text: String) {
    MvText(text, Modifier.padding(top = 14.dp, bottom = 2.dp), size = 13.sp, weight = FontWeight.Bold)
}

@Composable
private fun ColumnScope.PersonBody(pf: PersonFlow) {
    val c = Mv.c
    val d = pf.data
    val sc = pf.scroll
    val copy = rememberCopyName()
    val scope = rememberCoroutineScope()
    if (pf.failed) {
        RetryBox("Could not load details.") { pf.retry() }
        return
    }
    if (d == null) {
        NoteP("Loading…")
        return
    }
    // ---- header: photo, name, facts, known credits ----
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonPhoto(d.photo, d.female, 96.dp)
        Column(Modifier.weight(1f)) {
            MvText(d.name, Modifier.plainClickable { copy(d.name) }, size = 20.sp, weight = FontWeight.Bold, lineHeight = 1.2f)
            MvText(d.info, Modifier.padding(top = 4.dp), size = 12.5.sp, color = c.mut)
            if (d.knownCredits > 0) {
                Box(
                    Modifier.padding(top = 8.dp).clip(CircleShape).background(c.s2)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    MvText("Known Credits " + d.knownCredits, size = 11.sp, weight = FontWeight.Bold)
                }
            }
        }
    }
    // ---- biography: four lines, tap More / Less ----
    if (d.bio.isNotEmpty()) {
        var open by remember(d) { mutableStateOf(false) }
        MvText(
            d.bio, Modifier.padding(top = 12.dp), size = 12.5.sp, color = c.mut,
            maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis
        )
        if (d.bio.length > 220) {
            MvText(
                if (open) "Less" else "More", Modifier.plainClickable { open = !open }.padding(top = 4.dp, bottom = 2.dp),
                size = 12.sp, weight = FontWeight.Bold
            )
        }
    }
    // ---- tabs: Movies | Awards (they also follow the swipe of the pages below) ----
    val second by remember(sc.pages) {
        derivedStateOf {
            val w = sc.pages.layoutInfo.viewportEndOffset - sc.pages.layoutInfo.viewportStartOffset
            sc.pages.firstVisibleItemIndex > 0 || sc.pages.firstVisibleItemScrollOffset > w / 2
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MvPill("Movies", !second, { scope.launch { sc.pages.animateScrollToItem(0) } }, Modifier.weight(1f), padH = 0.dp, padV = 9.dp, weight = FontWeight.Bold)
        MvPill("Awards", second, { scope.launch { sc.pages.animateScrollToItem(1) } }, Modifier.weight(1f), padH = 0.dp, padV = 9.dp, weight = FontWeight.Bold)
    }

    // ---- #ppg : Movies | Awards, takes all the room that is left ----
    LazyRow(
        Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), state = sc.pages,
        flingBehavior = rememberSnapFlingBehavior(sc.pages)
    ) {
        item {
            Column(Modifier.fillParentMaxWidth().fillMaxHeight()) {
                val list = if (pf.top) d.top else d.latest
                LazyColumn(Modifier.fillMaxSize().topFade(sc.mv), state = sc.mv, contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (d.upcoming.isNotEmpty()) {
                        item { Ph3("Upcoming movies") }
                        items(d.upcoming) { x -> CreditRow(x, 69.dp, false) { pf.pick(it) } }
                    }
                    item {
                        MhRow(if (pf.top) "Top Rated (" + d.top.size + ")" else "Movies (" + d.all.size + ")") {
                            MvPill("Latest", !pf.top, { pf.pickTop(false) }, fontSize = 11.sp, padH = 10.dp, padV = 3.dp)
                            MvPill("Top Rated", pf.top, { pf.pickTop(true) }, fontSize = 11.sp, padH = 10.dp, padV = 3.dp)
                        }
                    }
                    if (list.isEmpty()) item { NoteP("No credits found.") }
                    else items(list) { x -> CreditRow(x, 69.dp, pf.top) { pf.pick(it) } }
                }
            }
        }
        item {
            Column(Modifier.fillParentMaxWidth().fillMaxHeight()) {
                AwardsPage(pf)
            }
        }
    }
}

/** .mh : header row with the title on the left and optional pills on the right */
@Composable
private fun MhRow(title: String, right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp).heightIn(min = 30.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvText(title, size = 13.sp, weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { right() }
    }
}

/** .snv : at most five rows high, rows snap to the top edge */
@Composable
private fun CreditList(list: List<PCredit>, state: LazyListState, rh: Dp, rt: Boolean, modifier: Modifier, onTap: (Long) -> Unit) {
    LazyColumn(
        modifier, state = state,
        flingBehavior = rememberSnapFlingBehavior(state)
    ) {
        items(list) { x -> CreditRow(x, rh, rt, onTap) }
    }
}

@Composable
private fun CreditRow(x: PCredit, rh: Dp, rt: Boolean, onTap: (Long) -> Unit) {
    val c = Mv.c
    Box(Modifier.fillMaxWidth().height(rh).plainClickable { onTap(if (x.tv) -x.id else x.id) }) {
        Row(
            Modifier.fillMaxSize().padding(top = 6.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sh = RoundedCornerShape(6.dp)
            if (x.poster != null) {
                AsyncImage(
                    model = Tmdb.imgUrl(x.poster, "w154"), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp, 56.dp).clip(sh).background(c.s2)
                )
            } else {
                Box(Modifier.size(38.dp, 56.dp).clip(sh).background(c.s2))
            }
            Column(Modifier.weight(1f)) {
                MvText(
                    x.t + (if (x.tv) " (TV)" else ""), size = 13.sp, weight = FontWeight.Bold, lineHeight = 1.25f,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                MvText(
                    (if (rt && x.v != 0.0) "★ " + Js.toFixed(x.v, 1) + " · " else "") + x.d.take(4) + " · " + x.r,
                    Modifier.padding(top = 1.dp), size = 11.sp, color = c.mut
                )
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.line))
    }
}

// ---- Awards page ------------------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.AwardsPage(pf: PersonFlow) {
    val a = pf.aw
    var head = "Awards"
    if (a is AwState.Rows && a.rows.isNotEmpty()) {
        val t = a.rows.size
        if (a.rows[0].badge == 0) head += " ($t)"
        else {
            val w = a.rows.count { it.badge == 1 }
            head += if (w > 0 && t > w) " ($w won · ${t - w} nominated)" else if (t > w) " ($t nominated)" else " ($t won)"
        }
    }
    MhRow(head)
    when (a) {
        is AwState.Loading -> MvText("Loading…", size = 14.sp, color = Mv.c.mut)
        is AwState.Note -> MvText(a.text, size = 14.sp, color = Mv.c.mut)
        is AwState.Fail -> RetryBox(a.text) { pf.retry() }
        is AwState.Rows -> {
            val st = pf.scroll.aw
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).topFade(st), state = st,
                flingBehavior = rememberSnapFlingBehavior(st)
            ) {
                items(a.rows) { r -> AwardRow(r) }
            }
        }
    }
}

@Composable
private fun AwardRow(r: AwRow) {
    val c = Mv.c
    Box(Modifier.fillMaxWidth().height(70.dp)) {
        Column(Modifier.fillMaxSize().padding(top = 6.dp, bottom = 7.dp), verticalArrangement = Arrangement.Center) {
            MvText(r.name, size = 13.sp, weight = FontWeight.SemiBold, lineHeight = 1.25f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.badge != 0 || r.category.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.badge != 0) {
                        val won = r.badge == 1
                        Box(
                            Modifier.padding(end = 6.dp).clip(CircleShape).background(if (won) c.w else c.s2)
                                .padding(horizontal = 7.dp)
                        ) {
                            MvText(
                                if (won) "Won" else "Nominee", size = 10.sp, weight = FontWeight.Bold, lineHeight = 1.5f,
                                color = if (won) Color.White else c.mut
                            )
                        }
                    }
                    MvText(
                        r.category, Modifier.weight(1f, fill = false), size = 12.sp, weight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (r.work.isNotEmpty()) {
                MvText(
                    r.work, Modifier.padding(top = 2.dp), size = 11.5.sp, color = c.mut,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.line))
    }
}
