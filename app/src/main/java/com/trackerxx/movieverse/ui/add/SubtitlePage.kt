package com.trackerxx.movieverse.ui.add

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    MvPage(onBack = { flow.close() }, title = head, scrollable = false) {
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
                if (list.isEmpty()) {
                    Note(
                        when (flow.lang) {
                            "BN" -> "No Bangla subtitle found for this title."
                            "EN" -> "No English subtitle found for this title."
                            else -> "No Bangla or English subtitle found on SubSource."
                        }
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        list.forEachIndexed { i, s -> SubRow(flow, s, i == 0) }
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

@Composable
private fun Note(text: String) {
    MvText(
        text, Modifier.fillMaxWidth().padding(vertical = 18.dp), size = 13.sp, color = Mv.c.mut,
        align = TextAlign.Center, lineHeight = 1.4f
    )
}

private val VoteUp = Color(0xFF22C55E)
private val VoteDown = Color(0xFFEF4444)

/** one subtitle: name + uploader, a details arrow and a red download button; the details open under it */
@Composable
private fun SubRow(flow: SubsFlow, s: SubItem, first: Boolean) {
    val c = Mv.c
    val btn = RoundedCornerShape(10.dp)
    val busy = flow.busy.contains(s.id)
    val saved = flow.done.contains(s.id) && !busy
    val open = flow.openId == s.id
    val who = flow.authors[s.id]?.takeIf { it.isNotEmpty() } ?: s.author
    val noRipple = remember { MutableInteractionSource() }
    val parts = ArrayList<String>()
    if (flow.lang == "all") parts.add(s.langName)
    if (s.season > 0) parts.add("S" + s.season + (if (s.episode > 0) " E" + s.episode else ""))
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
