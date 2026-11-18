package com.trackerxx.movieverse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.CompletableDeferred

/** modal(o): the options of one question dialog (title, optional text / input / colour choice, buttons) */
class ModalSpec(
    val title: String,
    val text: String = "",
    val input: Boolean = false,
    val value: String = "",
    val placeholder: String = "",
    val colors: Boolean = false,
    val color: String? = null,
    val ok: String = "OK",
    val red: Boolean = false,
    val danger: Boolean = false,
    val plain: Boolean = false,
    val del: Boolean = false,
    val alt: String? = null
)

/** what the person answered */
sealed class ModalResult {
    /** closed with X / back / tap outside */
    object Cancel : ModalResult()

    /** the left button (Delete, or the alt text) */
    object Delete : ModalResult()

    /** OK pressed: text = the trimmed input ("" when there is no input), color = chosen colour when colors = true */
    class Ok(val text: String, val color: String?) : ModalResult()
}

/** The 10 folder colours (FPAL) */
val FolderPalette = listOf("#ff3b3b", "#ff8a1f", "#f5c518", "#00b800", "#2fb3a6", "#2f9fd6", "#5b6cff", "#9a7bff", "#e84fb0", "#8d99ae")

/** One question dialog at a time. ask() waits for the answer, like the web version's modal() promise. */
@Stable
class ModalState {
    var spec by mutableStateOf<ModalSpec?>(null)
        private set
    private var pending: CompletableDeferred<ModalResult>? = null

    suspend fun ask(s: ModalSpec): ModalResult {
        pending?.complete(ModalResult.Cancel)
        val d = CompletableDeferred<ModalResult>()
        pending = d
        spec = s
        return d.await()
    }

    fun finish(r: ModalResult) {
        spec = null
        val d = pending
        pending = null
        d?.complete(r)
    }
}

val LocalModal = staticCompositionLocalOf<ModalState> { error("ModalState not provided") }

/** the 34dp colour circles (.sw): selected one has an ink ring */
@Composable
private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = Mv.c
    val color = parseHex(hex) ?: c.acc
    val ink = c.ink
    val line = c.line
    Box(
        Modifier
            .size(34.dp)
            .drawBehind {
                // box-shadow: 0 0 0 1px var(--line)
                drawCircle(line, radius = size.minDimension / 2f + 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            .clip(CircleShape)
            .background(color)
            .drawBehind {
                if (selected) {
                    val w = 3.dp.toPx()
                    drawCircle(ink, radius = size.minDimension / 2f - w / 2f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(w))
                }
            }
            .plainClickable(onClick = onClick)
    )
}

private val HEX = Regex("^#([0-9a-fA-F]{6})$")

/** "#rrggbb" -> Color (null when it is not a 6 digit hex colour) */
fun parseHex(s: String?): Color? {
    val m = HEX.matchEntire(s ?: "") ?: return null
    return Color(0xFF000000L or m.groupValues[1].toLong(16))
}

/** Draws the current question dialog (put once at the top of the app). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ModalHost(state: ModalState) {
    val s = state.spec ?: return
    var text by remember(s) { mutableStateOf(s.value) }
    var selC by remember(s) {
        mutableStateOf(if (s.color != null && HEX.matches(s.color)) s.color else FolderPalette[0])
    }
    val focus = remember(s) { FocusRequester() }
    MvDialog(
        onDismiss = { state.finish(ModalResult.Cancel) },
        widthMax = 380.dp,
        widthFraction = 0.90f,
        onClose = { state.finish(ModalResult.Cancel) }
    ) {
        MvText(
            s.title,
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center
        )
        if (s.text.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            MvNote(s.text, Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
        if (s.input) {
            Spacer(Modifier.height(14.dp))
            MvTextField(text, { text = it }, Modifier.focusRequester(focus), placeholder = s.placeholder)
        }
        if (s.colors) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FolderPalette.forEach { hex ->
                    Swatch(hex, selected = hex == (selC ?: "").lowercase(), onClick = { selC = hex })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val hasLeft = s.del || s.alt != null
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (hasLeft) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasLeft) {
                MvButton(s.alt ?: "Delete", { state.finish(ModalResult.Delete) }, danger = s.alt == null)
            }
            MvButton(
                s.ok,
                {
                    val v = if (s.input) text.trim() else ""
                    if (s.input && v.isEmpty()) {
                        try { focus.requestFocus() } catch (e: Exception) { }
                    } else {
                        state.finish(ModalResult.Ok(v, if (s.colors) selC else null))
                    }
                },
                kind = if (s.red) BtnKind.Red else if (s.danger || s.plain) BtnKind.Normal else BtnKind.Primary,
                danger = s.danger && !s.red
            )
        }
    }
}
