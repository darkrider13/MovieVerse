package com.trackerxx.movieverse.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.MvApiLoadingDialog
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.MvOkDialog
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private sealed class UpState {
    object Checking : UpState()
    object UpToDate : UpState()
    class Available(val info: Updater.Info) : UpState()
    object Downloading : UpState()
    class Ready(val apk: File, val needPermission: Boolean) : UpState()
    class Failed(val msg: String, val info: Updater.Info?) : UpState()
}

private fun mb(b: Long): String =
    if (b < 1024L * 1024L) String.format(Locale.US, "%.0f KB", b / 1024.0)
    else String.format(Locale.US, "%.1f MB", b / 1048576.0)

/**
 * Settings > Update: checks GitHub for a newer build, shows "Update available", then a download dialog
 * (percentage, MB done / total, speed) in the app's own dialog style, and finally opens the Android installer.
 */
@Composable
fun UpdateFlow(onClose: () -> Unit, initial: Updater.Info? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<UpState>(if (initial != null) UpState.Available(initial) else UpState.Checking) }
    var job by remember { mutableStateOf<Job?>(null) }
    var done by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(0.0) }   // bytes / second

    fun check() {
        st = UpState.Checking
        job?.cancel()
        job = scope.launch {
            st = try {
                val info = Updater.check(ctx)
                if (info == null) UpState.UpToDate else UpState.Available(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpState.Failed(e.message ?: "Could not check for updates.", null)
            }
        }
    }

    fun download(info: Updater.Info) {
        done = 0L
        total = info.size
        speed = 0.0
        st = UpState.Downloading
        job?.cancel()
        job = scope.launch {
            val t0 = System.nanoTime()
            try {
                val apk = Updater.download(ctx, info) { d, t ->
                    done = d
                    if (t > 0) total = t
                    val sec = (System.nanoTime() - t0) / 1e9
                    if (sec > 0.4) speed = d / sec
                }
                val r = try { Updater.install(ctx, apk) } catch (e: Exception) { null }
                st = UpState.Ready(apk, r == Updater.InstallResult.NeedPermission)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                st = UpState.Failed(e.message ?: "The download failed.", info)
            }
        }
    }

    LaunchedEffect(Unit) { if (initial == null) check() }

    when (val s = st) {
        UpState.Checking -> MvApiLoadingDialog(
            caption = "Looking for the newest version",
            title = "Checking for updates",
            onDismiss = { job?.cancel(); onClose() }
        )

        UpState.UpToDate -> MvOkDialog(
            title = "You're up to date",
            message = "MovieVerse is already the newest version.",
            onOk = onClose
        )

        is UpState.Available -> MvDialog(onDismiss = onClose, widthMax = 340.dp, widthFraction = 0.86f) {
            UpdateBadge()
            MvText("Update available", Modifier.fillMaxWidth(), size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            if (s.info.versionCode != 0L) {
                MvNote(
                    "Build " + Updater.installedCode(ctx) + "  \u2192  Build " + s.info.versionCode,
                    Modifier.fillMaxWidth().padding(top = 8.dp), align = TextAlign.Center
                )
            }
            MvNote(
                if (s.info.size > 0) "A new version of MovieVerse is ready (" + mb(s.info.size) + ")."
                else "A new version of MovieVerse is ready.",
                Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Later", onClose, Modifier.weight(1f))
                MvButton("Update now", { download(s.info) }, Modifier.weight(1f), kind = BtnKind.Primary)
            }
        }

        UpState.Downloading -> MvDialog(onDismiss = null, widthMax = 340.dp, widthFraction = 0.86f) {
            val c = Mv.c
            val frac = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            val shown by animateFloatAsState(frac, tween(160, easing = LinearEasing), label = "dlbar")
            MvText("Downloading update", Modifier.fillMaxWidth(), size = 16.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            MvText(
                if (total > 0) (frac * 100f).toInt().toString() + "%" else "...",
                Modifier.fillMaxWidth().padding(top = 12.dp), size = 34.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center
            )
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(c.line)
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(4.dp)).background(c.acc))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MvText(
                    if (total > 0) mb(done) + " of " + mb(total) else mb(done),
                    size = 12.sp, weight = FontWeight.SemiBold, color = c.mut
                )
                MvText(
                    if (speed > 0) mb(speed.toLong()) + "/s" else "",
                    size = 12.sp, weight = FontWeight.SemiBold, color = c.mut
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                MvButton("Cancel", { job?.cancel(); onClose() })
            }
        }

        is UpState.Ready -> MvDialog(onDismiss = onClose, widthMax = 340.dp, widthFraction = 0.86f) {
            UpdateBadge()
            MvText(
                if (s.needPermission) "Allow installing" else "Ready to install",
                Modifier.fillMaxWidth(), size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center
            )
            MvNote(
                if (s.needPermission) "Turn on \"Allow from this source\" for MovieVerse, come back, then tap Install."
                else "The download is finished. If the installer did not open, tap Install.",
                Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Close", onClose, Modifier.weight(1f))
                MvButton(
                    "Install",
                    {
                        val r = try { Updater.install(ctx, s.apk) } catch (e: Exception) { null }
                        st = UpState.Ready(s.apk, r == Updater.InstallResult.NeedPermission)
                    },
                    Modifier.weight(1f), kind = BtnKind.Primary
                )
            }
        }

        is UpState.Failed -> MvDialog(onDismiss = onClose, widthMax = 340.dp, widthFraction = 0.86f) {
            MvText("Update failed", Modifier.fillMaxWidth(), size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            MvNote(s.msg, Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Close", onClose, Modifier.weight(1f))
                MvButton("Try again", { if (s.info != null) download(s.info) else check() }, Modifier.weight(1f), kind = BtnKind.Primary)
            }
        }
    }
}

/** round accent badge with the download arrow (same look as the green tick of the OK dialog, but in the app accent) */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.UpdateBadge() {
    val c = Mv.c
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 6.dp, bottom = 12.dp)
            .size(60.dp)
            .clip(CircleShape)
            .background(c.acc),
        contentAlignment = Alignment.Center
    ) {
        MvIcon(MvIcons.Download, 30.dp, tint = c.onAcc)
    }
}
