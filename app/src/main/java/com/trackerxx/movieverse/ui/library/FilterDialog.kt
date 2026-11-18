package com.trackerxx.movieverse.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText

private val RATS = listOf("9" to "9+", "8" to "8+", "7" to "7+", "6" to "6+", "5" to "5+")

/** one option of the filter dialog (.fopts .pill) */
@Composable
private fun OptPill(text: String, pressed: Boolean, fixedHeight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Mv.c
    val shape = RoundedCornerShape(99.dp)
    Row(
        modifier
            .then(if (fixedHeight) Modifier.height(38.dp) else Modifier)
            .clip(shape)
            .background(if (pressed) c.ink else c.s)
            .border(1.dp, if (pressed) c.ink else c.line, shape)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = if (fixedHeight) 0.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvText(
            text, size = 13.sp, weight = if (pressed) FontWeight.Bold else FontWeight.Normal,
            color = if (pressed) c.bg else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            align = TextAlign.Center, lineHeight = if (fixedHeight) 36f / 13f else 1.45f
        )
    }
}

/**
 * #sfdlg : Genre / Rating / Year / Sort by.
 * Genre: tap several (the list updates live), Rating / Year: tap again to clear, Sort: pick one. Reset and Done below.
 */
@Composable
fun FilterDialog(
    fkey: String,
    ff: FilterState,
    pool: List<Movie>,
    onChange: (FilterState) -> Unit,
    onClose: () -> Unit
) {
    val title = when (fkey) { "g" -> "Genre"; "r" -> "Rating"; "y" -> "Year"; else -> "Sort by" }
    val resetDisabled = when (fkey) { "g" -> ff.g.isEmpty(); "s" -> ff.s == "new"; "r" -> ff.r.isEmpty(); else -> ff.y.isEmpty() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.46f).dp

    MvDialog(onDismiss = onClose, onClose = onClose) {
        MvText(
            title,
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        // options: (value, label)
        val options: List<Pair<String, String>>
        val cols: Int
        val rows3: Boolean
        when (fkey) {
            "g" -> {
                val set = HashSet<String>()
                pool.forEach { m -> m.strList("genres")?.forEach { set.add(it) } }
                options = set.sorted().map { it to it }
                cols = 2; rows3 = true
            }
            "r" -> { options = RATS; cols = 5; rows3 = false }
            "y" -> {
                val ys = HashSet<String>()
                pool.filter { it.year.isNotEmpty() }.forEach { ys.add(it.year) }
                options = ys.sorted().reversed().map { it to it }
                cols = 3; rows3 = true
            }
            else -> { options = SORTS; cols = 2; rows3 = false }
        }

        fun pressed(v: String): Boolean = when (fkey) {
            "g" -> ff.g.contains(v)
            "r" -> ff.r == v
            "y" -> ff.y == v
            else -> ff.s == v
        }

        fun pick(v: String) {
            when (fkey) {
                "g" -> {
                    val g = ff.g.toMutableList()
                    if (g.contains(v)) g.remove(v) else g.add(v)
                    onChange(FilterState(g, ff.r, ff.y, ff.s))   // stays open
                }
                "r" -> { onChange(FilterState(ff.g, if (ff.r == v) "" else v, ff.y, ff.s)); onClose() }
                "y" -> { onChange(FilterState(ff.g, ff.r, if (ff.y == v) "" else v, ff.s)); onClose() }
                else -> { onChange(FilterState(ff.g, ff.r, ff.y, v)); onClose() }
            }
        }

        if (options.isEmpty()) {
            MvNote(
                "No genres yet. Movies added from search come with genres.",
                Modifier.fillMaxWidth(), align = TextAlign.Center
            )
        } else {
            val gap: Dp = 8.dp
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (rows3) 136.dp else maxH)
                    .verticalScroll(scroll)
                    .padding(vertical = if (rows3) 3.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                options.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { (v, label) ->
                            OptPill(label, pressed(v), rows3, Modifier.weight(1f)) { pick(v) }
                        }
                        // empty cells keep the column width
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MvButton(
                "Reset",
                {
                    onChange(
                        when (fkey) {
                            "g" -> FilterState(emptyList(), ff.r, ff.y, ff.s)
                            "s" -> FilterState(ff.g, ff.r, ff.y, "new")
                            "r" -> FilterState(ff.g, "", ff.y, ff.s)
                            else -> FilterState(ff.g, ff.r, "", ff.s)
                        }
                    )
                    onClose()
                },
                enabled = !resetDisabled
            )
            MvButton("Done", onClose, kind = BtnKind.Primary)
        }
    }
}
