package com.trackerxx.movieverse.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.releases.Releases
import com.trackerxx.movieverse.releases.UItem
import com.trackerxx.movieverse.releases.UMsg
import com.trackerxx.movieverse.ui.components.riseIn
import com.trackerxx.movieverse.ui.components.switchEnter
import com.trackerxx.movieverse.ui.components.rememberEntered
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.FieldSize
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.MvTextField
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.mvTextStyle
import com.trackerxx.movieverse.util.Countries

private val FULL: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** #usq : big search box with a magnifier on the left */
@Composable
private fun UpSearchBox(value: String, onValue: (String) -> Unit, placeholder: String) {
    val c = Mv.c
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = mvTextStyle(14.5.sp, FontWeight.Normal, c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(c.s2)
                    .border(1.dp, if (focused) c.acc else c.line, shape)
                    .padding(start = 43.dp, end = 15.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                MvIcon(
                    MvIcons.Search, 19.dp, Modifier.align(Alignment.CenterStart).offset(x = (-28).dp),
                    tint = Color(0xFF8A8FB0)
                )
                if (value.isEmpty()) MvText(placeholder, size = 14.5.sp, color = MvFixed.Placeholder, maxLines = 1)
                inner()
            }
        }
    )
}

/** .card of the Releases page: poster, tag (OTT date / Cinema / In Library), title + year */
@Composable
private fun UpCard(m: UItem, inLib: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    val url = Tmdb.imgUrl(m.poster, "w342")
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(cssGradient(160f, c.s2, c.line))
            .plainClickable(onClick = onClick)
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (inLib) c.w else MvFixed.RateBg)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            MvText(
                if (inLib) "In Library" else Releases.upTag(m),
                size = 10.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1.3f, maxLines = 1
            )
        }
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
            MvText(m.title, Modifier.weight(1f), size = 14.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 1.2f)
            MvText(m.release.take(4), size = 12.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun UpMessage(text: String, retry: Boolean) {
    val c = Mv.c
    val h = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Box(Modifier.fillMaxWidth().heightIn(min = h).padding(24.dp), contentAlignment = Alignment.Center) {
        if (retry) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MvText(text, size = 14.sp, color = c.mut, align = TextAlign.Center)
                MvButton("Retry", { Releases.retry() }, kind = BtnKind.Primary)
            }
        } else {
            MvText(text, color = c.mut, align = TextAlign.Center)
        }
    }
}

/** The Releases page as items of the home grid: search, mode pills, filters, cards, "Loading more…". */
fun LazyGridScope.releasesItems(
    searchText: String,
    onSearchText: (String) -> Unit,
    libIds: Set<Long>,
    onOpenFilter: (String) -> Unit,
    onTap: (UItem) -> Unit
) {
    val mode = Releases.umode
    val searching = Releases.usq.isNotEmpty()
    item(key = "usrow", span = FULL) {
        Box(Modifier.padding(bottom = 12.dp)) {
            UpSearchBox(
                searchText, onSearchText,
                if (mode == "cin") "Search On OTT" else if (mode == "today") "Search Released Today" else "Search In Cinemas"
            )
        }
    }
    item(key = "umode", span = FULL) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val tabs = listOf("today" to "Released Today", "soon" to "In Cinemas", "cin" to "On OTT")
            tabs.forEachIndexed { i, t ->
                if (i == 1) Spacer(Modifier.weight(1f))
                if (i > 1) Spacer(Modifier.size(8.dp))
                MvPill(t.second, mode == t.first, { Releases.setMode(t.first) }, fontSize = 12.5.sp, padH = 11.dp, padV = 4.dp)
            }
        }
    }
    if (!searching) {
        item(key = "ufil", span = FULL) {
            val ucs = Releases.ucs
            val ugs = Releases.ugs
            val uy = Releases.uy
            val utype = Releases.utype
            val cn = if (ucs.isEmpty()) "All countries" else if (ucs.size == 1) Releases.cLabel(ucs[0])
            else ucs.take(2).joinToString(", ") { Releases.cCode(it) } + (if (ucs.size > 2) "…" else "")
            val gn = if (ugs.isNotEmpty()) "Genres" else "All genres"
            val yn = uy.ifEmpty { "Any year" }
            val fs = (LocalConfiguration.current.screenWidthDp * 0.033f).coerceIn(11f, 14f)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                val cw = constraints.maxWidth
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 10.dp)) {
                    FlexFillRow(cw, 6.dp) {
                        if (mode == "cin") FilterPill((if (utype == "tv") "TV Series" else "Movie") + " ▾", false, fs) { onOpenFilter("t") }
                        FilterPill("$cn ▾", ucs.isNotEmpty(), fs) { onOpenFilter("c") }
                        FilterPill("$gn ▾", ugs.isNotEmpty(), fs) { onOpenFilter("g") }
                        if (mode == "soon") FilterPill("$yn ▾", uy.isNotEmpty(), fs) { onOpenFilter("y") }
                        if (ucs.isNotEmpty() || ugs.isNotEmpty() || (mode == "soon" && uy.isNotEmpty()) || (mode == "cin" && utype == "tv")) {
                            FilterPill("Reset", false, fs) { Releases.resetFilters() }
                        }
                    }
                }
            }
        }
    }
    val msg = Releases.msg
    val list = if (searching) Releases.sres else Releases.items
    if (msg !is UMsg.None) {
        item(key = "umsg", span = FULL) {
            when (msg) {
                is UMsg.Loading -> UpMessage("Loading…", false)
                is UMsg.Empty -> UpMessage(msg.text, false)
                is UMsg.Retry -> UpMessage(msg.text, true)
                else -> {}
            }
        }
    } else {
        itemsIndexed(list, key = { _, it -> it.id }) { i, m ->
            UpCard(m, libIds.contains(m.id), { onTap(m) }, Modifier.padding(bottom = 14.dp))
        }
    }
    if (!searching && Releases.moreVisible) {
        item(key = "umore", span = FULL) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp), horizontalArrangement = Arrangement.Center) {
                MvButton(Releases.moreText, { Releases.loadMore() })
            }
        }
    }
}

/** The search box, the Released Today / In Cinemas / On OTT buttons and the filter pills above the Releases cards (the cards are in the WebView). */
@Composable
fun ReleasesHeader(
    searchText: String,
    onSearchText: (String) -> Unit,
    onOpenFilter: (String) -> Unit
) {
    Column(Modifier.switchEnter(rememberEntered())) {
        val mode = Releases.umode
        val searching = Releases.usq.isNotEmpty()
        Box(Modifier.padding(bottom = 12.dp)) {
            UpSearchBox(
                searchText, onSearchText,
                if (mode == "cin") "Search On OTT" else if (mode == "today") "Search Released Today" else "Search In Cinemas"
            )
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val tabs = listOf("today" to "Released Today", "soon" to "In Cinemas", "cin" to "On OTT")
            tabs.forEachIndexed { i, t ->
                if (i == 1) Spacer(Modifier.weight(1f))
                if (i > 1) Spacer(Modifier.size(8.dp))
                MvPill(t.second, mode == t.first, { Releases.setMode(t.first) }, fontSize = 12.5.sp, padH = 11.dp, padV = 4.dp)
            }
        }
        if (!searching) {
            val ucs = Releases.ucs
            val ugs = Releases.ugs
            val uy = Releases.uy
            val utype = Releases.utype
            val cn = if (ucs.isEmpty()) "All countries" else if (ucs.size == 1) Releases.cLabel(ucs[0])
            else ucs.take(2).joinToString(", ") { Releases.cCode(it) } + (if (ucs.size > 2) "…" else "")
            val gn = if (ugs.isNotEmpty()) "Genres" else "All genres"
            val yn = uy.ifEmpty { "Any year" }
            val fs = (LocalConfiguration.current.screenWidthDp * 0.033f).coerceIn(11f, 14f)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                val cw = constraints.maxWidth
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 10.dp)) {
                    FlexFillRow(cw, 6.dp) {
                        if (mode == "cin") FilterPill((if (utype == "tv") "TV Series" else "Movie") + " ▾", false, fs) { onOpenFilter("t") }
                        FilterPill("$cn ▾", ucs.isNotEmpty(), fs) { onOpenFilter("c") }
                        FilterPill("$gn ▾", ugs.isNotEmpty(), fs) { onOpenFilter("g") }
                        if (mode == "soon") FilterPill("$yn ▾", uy.isNotEmpty(), fs) { onOpenFilter("y") }
                        if (ucs.isNotEmpty() || ugs.isNotEmpty() || (mode == "soon" && uy.isNotEmpty()) || (mode == "cin" && utype == "tv")) {
                            FilterPill("Reset", false, fs) { Releases.resetFilters() }
                        }
                    }
                }
            }
        }
    }
}

/** #fdlg : choose type / countries / genres / year. Reset clears the choice, Done applies it and reloads the page. */
@Composable
fun ReleaseFilterDialog(fk: String, onClose: () -> Unit) {
    var ftmp by remember(fk) {
        mutableStateOf(
            when (fk) { "c" -> Releases.ucs; "g" -> Releases.ugs; "t" -> listOf(Releases.utype); else -> listOf(Releases.uy) }
        )
    }
    var q by remember(fk) { mutableStateOf("") }
    val title = when (fk) { "c" -> "Choose countries"; "g" -> "Choose genres"; "t" -> "Choose type"; else -> "Choose year" }
    val options: List<Pair<String, String>> = when (fk) {
        "t" -> Releases.UTYPES
        "c" -> Countries.LIST
        "g" -> Releases.GENRES
        else -> Releases.YEARS
    }
    val ql = q.lowercase()
    val shown = options.filter { ql.isEmpty() || it.second.lowercase().contains(ql) }
    val resetDisabled = when (fk) {
        "t" -> ftmp.firstOrNull() == "movie"
        "y" -> ftmp.firstOrNull().isNullOrEmpty()
        else -> ftmp.isEmpty()
    }
    val cols = if (fk == "y") 3 else 2

    MvDialog(onDismiss = onClose, onClose = onClose) {
        MvText(
            title, Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center
        )
        if (fk == "c") {
            Spacer(Modifier.height(10.dp))
            MvTextField(q, { q = it }, placeholder = "Search country")
        }
        Spacer(Modifier.height(12.dp))
        val gap = 8.dp
        SnapFadeColumn(
            Modifier.fillMaxWidth().heightIn(max = OPT_VIEW_3ROWS),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            shown.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (v, label) ->
                        val on = ftmp.contains(v)
                        OptPill(label, on, true, Modifier.weight(1f)) {
                            ftmp = when (fk) {
                                "t" -> listOf(v)
                                "y" -> if (ftmp.firstOrNull() == v) listOf("") else listOf(v)
                                else -> if (on) ftmp.filter { it != v } else ftmp + v
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MvButton(
                "Reset",
                { ftmp = when (fk) { "t" -> listOf("movie"); "y" -> listOf(""); else -> emptyList() } },
                enabled = !resetDisabled
            )
            MvButton(
                "Done",
                {
                    when (fk) {
                        "c" -> Releases.ucs = ftmp
                        "g" -> Releases.ugs = ftmp
                        "t" -> Releases.utype = ftmp.firstOrNull().takeUnless { it.isNullOrEmpty() } ?: "movie"
                        else -> Releases.uy = ftmp.firstOrNull() ?: ""
                    }
                    onClose()
                    Releases.loadUp(true)
                },
                kind = BtnKind.Primary
            )
        }
    }
}
