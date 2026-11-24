package com.trackerxx.movieverse.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.net.SubItem
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.BtnSize
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvPage
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.isDarkBg
import com.trackerxx.movieverse.ui.components.scrollEdgeFade
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText

/** The Subtitles page of a movie: Bangla + English only, Download saves the file into the Download folder. */
@Composable
fun SubtitlePage(flow: SubsFlow) {
    val m = flow.movie ?: return
    val head = if (m.title.isNotEmpty()) m.title + (if (m.year.isNotEmpty()) " (" + m.year + ")" else "") else "Subtitles"
    // which season folders are open (season number -> open); kept while the language filter changes, reset for another title
    val openSeasons = remember(m.tmdbId, m.title) { mutableStateMapOf<Int, Boolean>() }

    // dark theme = same top as the other pages: no solid bar, no line; the black top fades softly into the red / blue glow
    MvPage(onBack = { flow.close() }, title = head, scrollable = false, edgeFade = isDarkBg(Mv.c.bg)) {
        MvText("Subtitles", Modifier.padding(top = 6.dp), size = 20.sp, weight = FontWeight.Bold)

        // language filter
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MvPill("All", flow.lang == "all", { flow.pickLang("all") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
            MvPill("Bangla", flow.lang == "BN", { flow.pickLang("BN") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
            MvPill("English", flow.lang == "EN", { flow.pickLang("EN") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
        }
        Spacer(Modifier.height(10.dp))

        // only the subtitle cards scroll (header + language pills stay), with a soft fade at the top and bottom edge
        val listScroll = rememberScrollState()
        LaunchedEffect(flow.lang) { listScroll.scrollTo(0) }
        Column(Modifier.weight(1f).fillMaxWidth().scrollEdgeFade(listScroll).verticalScroll(listScroll)) {
        when (val u = flow.ui) {
            is SubsUi.Loading -> Note("Searching subtitles…")
            is SubsUi.NoKey -> Note("Add your SubSource API key in Settings \u2192 API keys \u2192 Subtitles.")
            is SubsUi.Error -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Note(u.message)
                MvButton("Retry", { flow.load() }, kind = BtnKind.Primary, size = BtnSize.Dialog)
            }
            is SubsUi.Ready -> {
                val list = flow.visible()
                // key(lang): switching All / Bangla / English builds the list fresh, so it plays the entrance again.
                // "Show more" keeps the rows that are already there (they stay still) and only the new ones come in.
                key(flow.lang) {
                    if (list.isEmpty()) {
                        Reveal(0) {
                            Note(
                                when (flow.lang) {
                                    "BN" -> "No Bangla subtitle found for this title."
                                    "EN" -> "No English subtitle found for this title."
                                    else -> "No Bangla or English subtitle found on SubSource."
                                }
                            )
                        }
                    } else if (flow.items.any { it.season > 0 }) {
                        // a TV series: one folder per season, tap a folder to open its subtitles
                        SeasonFolders(flow, list, openSeasons)
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            list.forEachIndexed { i, s -> Reveal(i) { SubRow(flow, s, i == 0, i + 1) } }
                        }
                    }
                }
                if (flow.canMore()) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                        MvButton(
                            if (flow.loadingMore) "Loading…" else "Show more", { flow.more() },
                            size = BtnSize.Dialog, enabled = !flow.loadingMore
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Light entrance for a list row: fades in and rises 10dp, each row a little after the one before it (the delay stops growing
 * after 8 rows, so a long list never waits). Only alpha + translation on a graphics layer: no relayout, no recomposition per frame.
 */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    val rise = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(minOf(index, 8) * 30L)
        p.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * rise }) { content() }
}

@Composable
private fun Note(text: String) {
    MvText(
        text, Modifier.fillMaxWidth().padding(vertical = 18.dp), size = 13.sp, color = Mv.c.mut,
        align = TextAlign.Center, lineHeight = 1.4f
    )
}

/** TV series: the subtitles sorted into one folder per season (Season 1, Season 2 ...; entries without a season go last as "Other") */
@Composable
private fun SeasonFolders(flow: SubsFlow, list: List<SubItem>, openSeasons: SnapshotStateMap<Int, Boolean>) {
    val groups = list.groupBy { it.season }.toList().sortedBy { if (it.first == 0) Int.MAX_VALUE else it.first }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEachIndexed { gi, g ->
            val season = g.first
            // a single folder opens by itself, with several they all start closed
            val isOpen = openSeasons[season] ?: (groups.size == 1)
            Reveal(gi) {
                SeasonFolder(flow, season, g.second, isOpen) { openSeasons[season] = !isOpen }
            }
        }
    }
}

/** one season folder: folder icon, "Season N", how many subtitles (Bangla / English), an arrow; the subtitles open inside it */
@Composable
private fun SeasonFolder(flow: SubsFlow, season: Int, subs: List<SubItem>, open: Boolean, onToggle: () -> Unit) {
    val c = Mv.c
    val card = RoundedCornerShape(16.dp)
    val noRipple = remember { MutableInteractionSource() }
    val turn by animateFloatAsState(if (open) 90f else 0f, tween(220, easing = FastOutSlowInEasing), label = "season-arrow")
    val bn = subs.count { it.code == "BN" }
    val en = subs.count { it.code == "EN" }
    val total = subs.size
    val info = ArrayList<String>()
    info.add(total.toString() + (if (total == 1) " subtitle" else " subtitles"))
    if (bn > 0 && en > 0) {
        info.add(bn.toString() + " Bangla")
        info.add(en.toString() + " English")
    }
    Column(
        Modifier.fillMaxWidth().clip(card)
            .background(if (open) c.s2.copy(alpha = .55f) else c.s2.copy(alpha = .3f))
            .border(1.dp, if (open) c.acc.copy(alpha = .45f) else c.line, card)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = noRipple, indication = null) { onToggle() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.acc.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) {
                MvIcon(MvIcons.Folder, 22.dp, tint = c.acc)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MvText(
                    if (season > 0) "Season " + season else "Other", size = 16.sp, weight = FontWeight.Bold,
                    lineHeight = 1.2f, maxLines = 1
                )
                MvText(
                    info.joinToString(" · "), size = 12.sp, color = c.mut, lineHeight = 1.2f,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            MvIcon(MvIcons.Chevron, 18.dp, Modifier.rotate(turn), tint = if (open) c.acc else c.mut)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 4.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = .55f)))
                // episodes in order: Episode 1, 2, 3 ... (an episode nobody uploaded is simply skipped);
                // season packs / names without an episode number come first as "Full season"
                val eps = subs.groupBy { episodeOf(it) }.toList().sortedBy { it.first }
                eps.forEach { (ep, list) ->
                    EpisodeHeader(if (ep > 0) "Episode " + ep else "Full season / Other", list.size)
                    list.forEachIndexed { i, s -> SubRow(flow, s, i == 0, i + 1, inFolder = true) }
                }
            }
        }
    }
}

private val EpS = Regex("S\\d{1,2}[ ._-]?E(\\d{1,3})", RegexOption.IGNORE_CASE)
private val EpX = Regex("(?<![0-9])\\d{1,2}x(\\d{2,3})(?![0-9])", RegexOption.IGNORE_CASE)
private val EpW = Regex("(?<![A-Za-z0-9])(?:Episode|Ep|E)[ ._-]?(\\d{1,3})(?![0-9A-Za-z])", RegexOption.IGNORE_CASE)

/** the episode number of a subtitle, read from its release name (S01E03, 1x03, Episode 3, EP03); 0 = none found (season pack) */
private fun episodeOf(s: SubItem): Int {
    if (s.episode > 0) return s.episode
    val n = s.release
    val m = EpS.find(n) ?: EpX.find(n) ?: EpW.find(n)
    return m?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

/** small title line above the subtitles of one episode */
@Composable
private fun EpisodeHeader(text: String, count: Int) {
    val c = Mv.c
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(c.acc))
        MvText(text, size = 13.sp, weight = FontWeight.Bold, color = c.ink, lineHeight = 1.2f, maxLines = 1)
        MvText(count.toString(), size = 12.sp, color = c.mut, lineHeight = 1.2f, maxLines = 1)
    }
}

private val VoteUp = Color(0xFF22C55E)
private val VoteDown = Color(0xFFEF4444)

/** one subtitle: name + uploader, a details arrow and a red download button; the details open under it */
@Composable
private fun SubRow(flow: SubsFlow, s: SubItem, first: Boolean, n: Int, inFolder: Boolean = false) {
    val c = Mv.c
    val btn = RoundedCornerShape(10.dp)
    val busy = flow.busy.contains(s.id)
    val saved = flow.done.contains(s.id) && !busy
    val open = flow.openId == s.id
    val who = flow.authors[s.id]?.takeIf { it.isNotEmpty() } ?: s.author
    val noRipple = remember { MutableInteractionSource() }
    val parts = ArrayList<String>()
    if (flow.lang == "all") parts.add(s.langName)
    // inside a season folder the season is already the folder's name
    if (!inFolder && s.season > 0) parts.add("S" + s.season + (if (s.episode > 0) " E" + s.episode else ""))
    if (s.hi) parts.add("HI")
    if (who.isNotEmpty()) parts.add("By " + who)
    val subLine = parts.joinToString(" \u00B7 ")

    Column(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = .55f)))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1, 2, 3 ... counting of the subtitles
            MvText(
                n.toString() + ".", Modifier.widthIn(min = 20.dp), size = 13.sp, weight = FontWeight.Bold,
                color = c.mut, lineHeight = 1.3f, maxLines = 1, align = TextAlign.End
            )
            Column(
                Modifier.weight(1f).clickable(interactionSource = noRipple, indication = null) { flow.toggleDetails(s.id) },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                MvText(s.release, size = 13.sp, color = c.mut, lineHeight = 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subLine.isNotEmpty()) {
                    MvText(subLine, size = 11.5.sp, color = c.ink.copy(alpha = .75f), lineHeight = 1.2f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // details arrow
            Box(
                Modifier.size(36.dp).clip(btn).background(if (open) c.line else c.s2).border(1.dp, c.line, btn)
                    .clickable { flow.toggleDetails(s.id) },
                contentAlignment = Alignment.Center
            ) {
                MvIcon(MvIcons.Chevron, 17.dp, Modifier.rotate(if (open) -90f else 90f), tint = if (open) c.ink else c.mut)
            }
            // download
            Box(
                Modifier.size(36.dp).clip(btn).background(if (saved) c.s2 else c.acc)
                    .border(1.dp, if (saved) c.line else Color.Transparent, btn)
                    .clickable(enabled = !busy) { flow.download(s) },
                contentAlignment = Alignment.Center
            ) {
                if (saved) MvIcon(MvIcons.CheckGreen, 17.dp) else DownloadIcon(busy, c.onAcc)
            }
        }
        if (open) Details(flow, s)
    }
}

/** the download arrow; while the file is being saved it pulses */
@Composable
private fun DownloadIcon(busy: Boolean, tint: Color) {
    if (busy) {
        val tr = rememberInfiniteTransition(label = "sub-dl")
        val a by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "sub-dl-a")
        MvIcon(MvIcons.Download, 18.dp, Modifier.alpha(a), tint = tint)
    } else {
        MvIcon(MvIcons.Download, 18.dp, tint = tint)
    }
}

/** what SubSource tells about the subtitle. One tap closes it, a double tap opens the subtitle's page on SubSource. */
@Composable
private fun Details(flow: SubsFlow, s: SubItem) {
    val c = Mv.c
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).pointerInput(s.id) {
            detectTapGestures(onTap = { flow.closeDetails() }, onDoubleTap = { flow.openPage(s) })
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (s.desc.isNotEmpty()) {
            MvText(s.desc, size = 13.sp, color = c.ink.copy(alpha = .85f), lineHeight = 1.5f)
        }
        if (s.date.isNotEmpty() || s.relType.isNotEmpty()) {
            InfoBox {
                if (s.date.isNotEmpty()) InfoCell("Uploaded", s.date)
                if (s.relType.isNotEmpty()) InfoCell("Release type", s.relType)
            }
        }
        InfoBox {
            InfoCell("Rated", s.score + "/10")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (s.good >= 0) Vote(true, s.good)
                if (s.bad >= 0) Vote(false, s.bad)
            }
        }
    }
}

@Composable
private fun InfoBox(content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Mv.c.s2).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

@Composable
private fun InfoCell(label: String, value: String) {
    val c = Mv.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MvText(label, size = 13.sp, weight = FontWeight.Bold, color = c.mut, lineHeight = 1.2f, maxLines = 1)
        MvText(value, size = 13.sp, color = c.ink, lineHeight = 1.2f, maxLines = 1)
    }
}

@Composable
private fun Vote(up: Boolean, n: Int) {
    val col = if (up) VoteUp else VoteDown
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MvIcon(MvIcons.Chevron, 14.dp, Modifier.rotate(if (up) -90f else 90f), tint = col)
        MvText(n.toString(), size = 13.sp, weight = FontWeight.ExtraBold, color = col, lineHeight = 1.2f, maxLines = 1)
    }
}
