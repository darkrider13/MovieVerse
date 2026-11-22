package com.trackerxx.movieverse.ui.add

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackerxx.movieverse.net.TrailerVideo
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.plainClickable
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.launch

/** ytGo(id): the official YouTube app first, the browser when it is not installed */
private fun ytGo(ctx: Context, id: String) {
    val web = "https://www.youtube.com/watch?v=$id"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
        }
    }
}

private val MON = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** #tdlg : slider with every official trailer (teasers when no trailer is out yet) */
@Composable
fun TrailerDialog(flow: AddFlow) {
    val cf = flow.cast
    val info = cf.trailer ?: return
    val c = Mv.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = info.list
    val n = l.size
    val isT = info.isTrailer
    MvDialog(onDismiss = { cf.closeTrailer() }, onClose = { cf.closeTrailer() }) {
        MvText(
            (if (isT) "Official Trailers" else "Teasers") + (if (cf.trailerTitle.isNotEmpty()) " · " + cf.trailerTitle else ""),
            Modifier.padding(end = 34.dp, bottom = 4.dp), size = 17.sp, weight = FontWeight.Bold, lineHeight = 1.25f
        )
        MvText(
            if (isT) n.toString() + (if (n > 1) " trailers" else " trailer") + " released"
            else "No trailer released yet · " + n + (if (n > 1) " teasers" else " teaser"),
            Modifier.padding(bottom = 12.dp), size = 12.5.sp, color = c.mut
        )
        val st = rememberLazyListState()
        LazyRow(
            Modifier.fillMaxWidth().padding(bottom = 2.dp), state = st,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            flingBehavior = rememberSnapFlingBehavior(st)
        ) {
            itemsIndexed(l) { _, v ->
                TrailerCard(v, isT, Modifier.fillParentMaxWidth()) { ytGo(ctx, v.key) }
            }
        }
        if (n > 1) {
            val cur by remember(st) {
                derivedStateOf {
                    val f = st.layoutInfo.visibleItemsInfo.firstOrNull()
                    val w = (f?.size ?: 1) + 10
                    val k = Math.round((st.firstVisibleItemIndex * w + st.firstVisibleItemScrollOffset).toFloat() / w)
                    k.coerceIn(0, n - 1)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (k in 0 until n) {
                    Box(
                        Modifier.height(7.dp).width(if (k == cur) 20.dp else 7.dp).clip(RoundedCornerShape(99.dp))
                            .background(if (k == cur) c.acc else c.line)
                            .plainClickable { scope.launch { st.animateScrollToItem(k) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerCard(v: TrailerVideo, isT: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Mv.c
    val shape = RoundedCornerShape(12.dp)
    var lvl by remember(v.key) { mutableIntStateOf(0) }   // maxresdefault -> sddefault -> hqdefault -> hidden
    val quality = arrayOf("maxresdefault", "sddefault", "hqdefault")
    Column(
        modifier.clip(shape).background(c.s).border(1.dp, c.line, shape).padding(1.dp).plainClickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            if (lvl < 3) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/" + v.key + "/" + quality[lvl] + ".jpg",
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    onSuccess = { if (it.result.drawable.intrinsicWidth < 200) lvl++ },
                    onError = { lvl++ }
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(54.dp, 38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) { MvIcon(MvIcons.PlayWhite, 22.dp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 14.dp)) {
            MvText(
                v.name.ifEmpty { if (isT) "Trailer" else "Teaser" }, size = 14.5.sp, weight = FontWeight.Bold, lineHeight = 1.3f,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val dt = v.publishedAt.take(10).let { d ->
                if (d.length == 10) (d.substring(8, 10).toIntOrNull() ?: 0).toString() + " " + MON[((d.substring(5, 7).toIntOrNull() ?: 1) - 1).coerceIn(0, 11)] + " " + d.substring(0, 4) else d
            }
            if (dt.isNotEmpty() || v.lang.isNotEmpty()) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dt.isNotEmpty()) MvText(dt, size = 12.sp, weight = FontWeight.Medium, color = c.mut)
                    if (v.lang.isNotEmpty()) {
                        val cs = RoundedCornerShape(6.dp)
                        Box(Modifier.clip(cs).background(c.s2).border(1.dp, c.line, cs).padding(horizontal = 6.dp, vertical = 3.dp)) {
                            MvText(v.lang.uppercase(), size = 10.sp, weight = FontWeight.Bold, lineHeight = 1f, letterSpacing = 0.7.sp)
                        }
                    }
                }
            }
        }
    }
}
