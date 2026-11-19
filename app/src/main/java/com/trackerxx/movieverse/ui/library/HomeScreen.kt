package com.trackerxx.movieverse.ui.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.AppScope
import com.trackerxx.movieverse.ui.add.AddDialog
import com.trackerxx.movieverse.ui.add.AddFlow
import com.trackerxx.movieverse.ui.components.LocalModal
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.MvApiLoadingDialog
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private fun nf(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n.toLong())

/**
 * The home page of the app: top bar, hero, Trending / Your Library switch and the Library (or Trending / Releases).
 * The whole page scrolls as one list, the top bar stays (like the sticky .nav).
 */
@Composable
fun HomeScreen(splashGone: Boolean, onDev: () -> Unit) {
    val toast = LocalToast.current
    val modal = LocalModal.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current.density
    val lib = remember { LibraryState() }
    val actions = remember { LibraryActions(lib, modal, toast, scope) }
    var home by remember { mutableStateOf("trend") }      // var home='trend' : the app starts on Trending
    var view by remember { mutableStateOf("library") }    // library | up (Releases)
    var qBox by remember { mutableStateOf("") }
    var fkey by remember { mutableStateOf<String?>(null) } // open filter dialog: g | r | y | s
    val gridState = rememberLazyGridState()
    val add = remember { AddFlow(scope, modal, toast) }
    val cardDrag = remember { DragState() }
    val tabDrag = remember { DragState() }

    // ---- start: load the library from the database (loadAllP) ---------------------------------------------------
    var progress by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(AppData.ready) }
    LaunchedEffect(Unit) {
        if (!AppData.ready) {
            val n = try { AppData.db.count() } catch (e: CancellationException) { throw e } catch (e: Exception) { 0 }
            if (n > 0) progress = "0 / " + nf(n) + " movies"
            try {
                AppData.movies = AppData.db.loadAll { done ->
                    progress = nf(done) + (if (n > 0) " / " + nf(n) else "") + " movies"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppData.movies = ArrayList()
            }
            AppData.ready = true
        }
        loaded = true
        Enrich.onChanged = { AppData.render() }
        AppData.render()
        AppScope.launch { Enrich.backfill() }
    }

    // ---- what the page shows -------------------------------------------------------------------------------------------
    val rev = AppData.rev
    val ready = AppData.ready
    val folderEff = lib.folder?.takeIf { id -> AppData.folders.any { it.status == lib.tab && it.id == id } }
    LaunchedEffect(folderEff, lib.folder) { if (lib.folder != folderEff) lib.folder = folderEff }
    val fctx = lib.tab + ":" + (folderEff ?: "")
    val ffEff = if (lib.ffor == fctx) lib.ff else FilterState()
    LaunchedEffect(fctx) {
        if (lib.ffor != fctx) {
            lib.ff = FilterState()
            lib.ffor = fctx
        }
    }
    LaunchedEffect(qBox) {
        delay(250)
        if (lib.q != qBox) {
            lib.q = qBox
            lib.lim = 60
        }
    }
    val lv = remember(rev, ready, lib.tab, folderEff, ffEff, lib.q, lib.country, lib.lim) {
        LibraryLogic.build(
            AppData.movies.toTypedArray().toList(), AppData.folders.toList(), AppData.statuses.toList(), AppData.SL,
            ready, lib.tab, folderEff, ffEff, lib.q, lib.country, lib.lim
        )
    }
    val dirty = remember(rev) { AppData.isDirty() }
    val lvNow by rememberUpdatedState(lv)

    // while a card is picked up the list shows the live order
    val cards = if (cardDrag.active) {
        val by = lv.list.associateBy { it.id }
        cardDrag.order.mapNotNull { by[it] }
    } else lv.list

    // ---- more cards when the end of the list comes near (IntersectionObserver, rootMargin 1500px) ----------------------
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            Pair(info.visibleItemsInfo.lastOrNull()?.index ?: 0, info.totalItemsCount)
        }.collect { (last, total) ->
            if (lvNow.total > lib.lim && last >= total - 12) lib.lim += 60
        }
    }

    // ---- page scrolls while a card is dragged near the top / bottom edge (tick() in the web version) ----------------------
    LaunchedEffect(cardDrag.active) {
        if (!cardDrag.active) return@LaunchedEffect
        while (cardDrag.active) {
            withFrameNanos { }
            val y = cardDrag.pos.y
            val h = gridState.layoutInfo.viewportSize.height
            val moved = if (y < 91 * dens) gridState.scrollBy(-14 * dens)
            else if (y > h - 110 * dens) gridState.scrollBy(14 * dens) else 0f
            if (moved != 0f) swapAtGrid(cardDrag, gridState)
        }
    }

    fun toTop() { scope.launch { gridState.scrollToItem(0) } }
    fun soon(what: String) { toast.show("$what comes in the next update") }

    Column(Modifier.fillMaxSize()) {
        TopNav(
            releasesLabel = if (view == "up") "Home" else "Releases",
            dirty = dirty,
            onBrand = { view = "library" },
            onReleases = { view = if (view == "up") "library" else "up" },
            onSettings = { toast.show("Settings come in Step 10") },
            onSettingsLong = onDev
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .cardReorder(
                            drag = cardDrag, grid = gridState,
                            ids = { if (lvNow.ordCtx != null) lvNow.list.map { it.id } else null },
                            haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrop = { ids -> actions.saveOrder(ids, lvNow.ordCtx) }
                        ),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (view == "library") {
                        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.padding(bottom = 14.dp)) { Hero(AppData.uname, onAdd = { add.openAdd(lib.tab, folderEff) }) }
                        }
                        item(key = "hsw", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.padding(bottom = 12.dp)) {
                                HomeSwitch(home, onPick = { h ->
                                    if (h == "lib") lib.openTab("all")
                                    if (h != home) {
                                        home = h
                                        Ls.setItem("library.home", h)
                                        view = "library"
                                        toTop()
                                    } else if (h == "lib") AppData.render()
                                })
                            }
                        }
                    }

                    if (view == "library" && home == "lib") {
                        item(key = "bar", span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(bottom = 12.dp)) {
                                TabsRow(
                                    tabs = lv.tabs, selected = lib.tab,
                                    onSelect = { t ->
                                        if (t == lib.tab && t != "all") actions.editStatus()
                                        else lib.openTab(t)
                                    },
                                    onAddStatus = { actions.addStatus() },
                                    drag = tabDrag,
                                    onReorder = { ids -> actions.saveTabOrder(ids) }
                                )
                                Box(Modifier.padding(top = 10.dp)) {
                                    SearchRow(qBox, { qBox = it }, LibraryLogic.fmtK(lv.inTabCount))
                                }
                            }
                        }
                        if (lv.showFilters) {
                            item(key = "fold", span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.padding(bottom = if (lv.emptyText != null) 4.dp else 6.dp)) {
                                    FolderChips(
                                        view = lv,
                                        onAll = { lib.folder = null; lib.lim = 60 },
                                        onFolder = { id ->
                                            if (lib.folder == id) actions.editFolder(id)
                                            else { lib.folder = id; lib.country = "all"; lib.lim = 60 }
                                        },
                                        onFolderLong = { id -> actions.editFolder(id) },
                                        onNew = { actions.newFolder() }
                                    )
                                    FilterRow(
                                        ff = ffEff,
                                        onOpen = { k -> fkey = k },
                                        onReset = { lib.ff = FilterState(); lib.lim = 60 }
                                    )
                                }
                            }
                        }
                        if (lv.emptyText != null) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyBox(lv.emptyText) }
                        } else {
                            items(cards, key = { it.id }) { m ->
                                MovieCard(
                                    m, AppData.statuses, AppData.SL,
                                    onClick = {
                                        if (!cardDrag.recentlyDragged()) {
                                            Enrich.chgSet.remove(m.id)
                                            add.openLib(m, false, 800L)
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(bottom = 14.dp)
                                        .animateItem(
                                            fadeInSpec = null,
                                            placementSpec = if (cardDrag.active) tween(180, easing = EaseCss) else null,
                                            fadeOutSpec = null
                                        ),
                                    placeholder = cardDrag.key == m.id
                                )
                            }
                            if (lv.total > lib.lim) {
                                item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.padding(top = 4.dp, bottom = 32.dp)) { MoreDots() }
                                }
                            }
                        }
                    }

                    if (view == "library" && home == "trend") {
                        item(key = "trend", span = { GridItemSpan(maxLineSpan) }) {
                            TrendNone(if (AppData.apis.key.isEmpty()) "Add your TMDB key in Settings to see Trending." else "Trending comes in Step 8.")
                        }
                    }
                    if (view == "up") {
                        item(key = "up", span = { GridItemSpan(maxLineSpan) }) { TrendNone("Releases come in Step 8.") }
                    }
                }

                // the card that follows the finger (.card.fly: scale 1.07, tilt 1.5 degrees, soft shadow)
                val dragged = if (cardDrag.active) lv.list.firstOrNull { it.id == cardDrag.key } else null
                if (dragged != null) {
                    val wDp = (cardDrag.size.width / dens).dp
                    Box(
                        Modifier
                            .offset { IntOffset((cardDrag.pos.x - cardDrag.grab.x).roundToInt(), (cardDrag.pos.y - cardDrag.grab.y).roundToInt()) }
                            .width(wDp)
                            .graphicsLayer { scaleX = 1.07f; scaleY = 1.07f; rotationZ = 1.5f; alpha = 0.97f }
                            .shadow(
                                24.dp, RoundedCornerShape(12.dp), clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.6f)
                            )
                    ) {
                        MovieCard(dragged, AppData.statuses, AppData.SL, onClick = {})
                    }
                }
            }
        }
    }

    // ---- filter dialog ---------------------------------------------------------------------------------------------------
    fkey?.let { k ->
        val pool = AppData.movies.toTypedArray().filter { it.status == lib.tab && (if (folderEff != null) it.folder == folderEff else true) }
        FilterDialog(
            fkey = k, ff = ffEff, pool = pool,
            onChange = { nf2 -> lib.ff = nf2; lib.lim = 60 },
            onClose = { fkey = null }
        )
    }

    AddDialog(add)

    if (splashGone && !loaded) {
        MvApiLoadingDialog(caption = progress, title = "Loading your library")
    }
}

/** #trend .tnone : centred note, at least 55% of the screen high */
@Composable
private fun TrendNone(text: String) {
    val h = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    Box(Modifier.fillMaxWidth().heightIn(min = h).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        MvText(text, size = 14.sp, color = Mv.c.mut, align = TextAlign.Center)
    }
}
