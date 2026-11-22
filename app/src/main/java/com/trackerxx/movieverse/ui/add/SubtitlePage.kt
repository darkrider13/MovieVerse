package com.trackerxx.movieverse.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText

/** The Subtitles page of a movie: Bangla + English only, Download saves the file into the Download folder. */
@Composable
fun SubtitlePage(flow: SubsFlow) {
    val c = Mv.c
    val m = flow.movie ?: return
    val head = if (m.title.isNotEmpty()) m.title + (if (m.year.isNotEmpty()) " (" + m.year + ")" else "") else "Subtitles"

    MvPage(onBack = { flow.close() }, title = head) {
        MvText("Subtitles", Modifier.padding(top = 6.dp), size = 20.sp, weight = FontWeight.Bold)
        MvText(
            "Only Bangla and English. Tap Download and the file is saved in your Download folder.",
            Modifier.padding(top = 4.dp), size = 12.sp, color = c.mut, lineHeight = 1.4f
        )

        // language filter
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MvPill("All", flow.lang == "all", { flow.pickLang("all") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
            MvPill("Bangla", flow.lang == "BN", { flow.pickLang("BN") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
            MvPill("English", flow.lang == "EN", { flow.pickLang("EN") }, fontSize = 13.sp, padH = 14.dp, padV = 5.dp)
        }
        Spacer(Modifier.height(14.dp))

        when (val u = flow.ui) {
            is SubsUi.Loading -> Note("Searching subtitles…")
            is SubsUi.NoKey -> Note("Add your subtitle API key in Settings (the Subtitles tab) to find subtitles.")
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
                            else -> "No Bangla or English subtitle found for this title."
                        }
                    )
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { s -> SubRow(flow, s) }
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
    }
}

@Composable
private fun Note(text: String) {
    MvText(
        text, Modifier.fillMaxWidth().padding(vertical = 18.dp), size = 13.sp, color = Mv.c.mut,
        align = TextAlign.Center, lineHeight = 1.4f
    )
}

@Composable
private fun SubRow(flow: SubsFlow, s: SubItem) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    val busy = flow.busy.contains(s.id)
    val saved = flow.done.contains(s.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.s2)
            .border(1.dp, c.line, shape)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MvText(s.release, size = 13.sp, weight = FontWeight.Bold, lineHeight = 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val bs = RoundedCornerShape(6.dp)
                Box(Modifier.clip(bs).background(c.s).border(1.dp, c.line, bs).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    MvText(s.langName, size = 11.sp, weight = FontWeight.Bold, color = if (s.code == "BN") c.w else c.mut, lineHeight = 1.2f, maxLines = 1)
                }
                val extra = buildString {
                    if (s.season > 0) append("S" + s.season + (if (s.episode > 0) " E" + s.episode else ""))
                    if (s.hi) append(if (isEmpty()) "HI" else " · HI")
                    if (s.author.isNotEmpty()) append(if (isEmpty()) s.author else " · " + s.author)
                }
                if (extra.isNotEmpty()) MvText(extra, Modifier.weight(1f, fill = false), size = 11.sp, color = c.mut, lineHeight = 1.2f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MvButton(
            { flow.download(s) }, kind = if (saved) BtnKind.Normal else BtnKind.Primary, size = BtnSize.Dialog,
            icon = false, enabled = !busy
        ) {
            if (saved && !busy) MvIcon(MvIcons.CheckGreen, 14.dp) else MvIcon(MvIcons.Download, 14.dp)
            MvText(if (busy) "Saving…" else if (saved) "Saved" else "Download")
        }
    }
}
