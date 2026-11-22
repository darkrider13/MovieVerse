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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.trackerxx.movieverse.releases.Releases
import com.trackerxx.movieverse.trending.Trending
import com.trackerxx.movieverse.ui.add.AddDialog
import com.trackerxx.movieverse.ui.add.AddFlow
import com.trackerxx.movieverse.ui.settings.SettingsDialog
import com.trackerxx.movieverse.ui.components.riseIn
import com.trackerxx.movieverse.ui.components.LocalModal
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.MvApiLoadingDialog
import com.trackerxx.movieverse.ui.theme.EaseCss
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.trackerxx.movieverse.ui.settings.KeySave
import com.trackerxx.movieverse.enrich.Refresh
import com.trackerxx.movieverse.ui.settings.ImportDialogs
import com.trackerxx.movieverse.ui.settings.rememberExportFlow
import com.trackerxx.movieverse.ui.settings.rememberImportFlow
import com.trackerxx.movieverse.ui.components.MvOkDialog
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private fun nf(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n.toLong())

/**
 * The home page of the app: top bar, hero, Trending / Your Library switch and the Library (or Trending / Releases).
 * The whole page scrolls as one list, the top bar stays (like the sticky .nav).
 */
@Composable
fun HomeScreen(splashGone: Boolean) {
    val toast = LocalToast.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val modal = LocalModal.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current.density
    val lib = remember { LibraryState() }
    val actions = remember { LibraryActions(lib, modal, toast, scope) }
    var home by remember { mutableStateOf("trend") }      // var home='trend' : the app starts on Trending
    var view by remember { mutableStateOf("library") }    // library | up (Releases)
    var settingsOpen by remember { mutableStateOf(false) } // #sdlg (Settings dialog)
    val export = rememberExportFlow()                      // Settings > Export
    val imp = rememberImportFlow()                         // Settings > Import
    var keyBusy by remember { mutableStateOf(false) }      // #apild "Fetching your API key" after Save
    var qBox by remember { mutableStateOf("") }
    var fkey by remember { mutableStateOf<String?>(null) } // open filter dialog: g | r | y | s
    var ufkey by remember { mutableStateOf<String?>(null) } // Releases filter dialog: t | c | g | y
    var usText by remember { mutableStateOf("") }          // Releases search box text
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
        Enrich.snapF = { m -> Refresh.snapF(m) }
        AppData.render()
        AppScope.launch { Enrich.backfill() }
        // the app starts on Trending: show what was cached at once, then refresh it from the network
        Trending.showCached()
        AppScope.launch { try { Trending.load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
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
    // The library list is only (re)built while Your Library is on screen. On Trending / Releases a background refresh
    // (rev bump) used to re-sort the whole library on the main thread -> scroll stutter. Now it reuses the last result.
    val lvOn = view == "library" && home == "lib"
    val lvHold = remember { arrayOfNulls<LibView>(1) }
    // The heavy part (filter + sort of the whole library) does NOT depend on lib.lim. Loading the next 60 cards while scrolling used to
    // filter + sort the whole library again on the main thread (a hitch every 60 cards). Now only the cheap cut (first lim cards) re-runs.
    // A background refresh (rev bump: ratings, posters, Trending...) must never rebuild the list while the finger is scrolling.
    // The new rev is applied as soon as the scroll has stopped.
    var revApplied by remember { mutableIntStateOf(AppData.rev) }
    LaunchedEffect(gridState) {
        snapshotFlow { Pair(AppData.rev, gridState.isScrollInProgress) }.collect { (r, scrolling) -> if (!scrolling) revApplied = r }
    }
    val lvFull = remember(revApplied, ready, lib.tab, folderEff, ffEff, lib.q, lib.country, lvOn) {
        val held = lvHold[0]
        if (!lvOn && held != null) held
        else LibraryLogic.build(
            AppData.movies.toTypedArray().toList(), AppData.folders.toList(), AppData.statuses.toList(), AppData.SL,
            ready, lib.tab, folderEff, ffEff, lib.q, lib.country, Int.MAX_VALUE
        ).also { lvHold[0] = it }
    }
    val lv = remember(lvFull, lib.lim) { lvFull.cut(lib.lim) }
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
            if (last >= total - 8) Releases.checkMore()
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

    // Trending is on screen: ratings are retried while it is; entering it loads (or re-uses) the rows (showView)
    val trendOn = view == "library" && home == "trend"
    SideEffect { Trending.visible = trendOn }
    LaunchedEffect(trendOn, loaded) {
        if (trendOn && loaded) {
            try { Trending.load(false) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
        }
    }
    // Releases is on screen: it loads (or re-uses) its list (showView('up'))
    val upOn = view == "up"
    SideEffect { Releases.visible = upOn }
    LaunchedEffect(upOn) { if (upOn) Releases.onShow() }
    val libIds = remember(rev) { AppData.movies.map { it.tmdbId }.toHashSet() }

    fun toTop() { scope.launch { gridState.scrollToItem(0) } }

    /** selTrend(): back to Trending, top of the page */
    fun selTrend() {
        home = "trend"
        Ls.setItem("library.home", "trend")
        view = "library"
        toTop()
    }

    /** apiBusy(keyLoad(), 900, 45000): "Fetching your API key" dialog (at least 0.9 s, at most 45 s); returns the error text or null */
    suspend fun runKeyBusy(): String? {
        keyBusy = true
        val t0 = System.currentTimeMillis()
        val job = scope.async { KeySave.keyLoad(home == "trend" && view != "up") }
        val err = withTimeoutOrNull(45000L) { job.await() }
        val wait = 900L - (System.currentTimeMillis() - t0)
        if (wait > 0) delay(wait)
        keyBusy = false
        return err
    }

    Column(Modifier.fillMaxSize()) {
        TopNav(
            releasesLabel = if (view == "up") "Home" else "Releases",
            dirty = dirty,
            onBrand = { view = "library" },
            onReleases = { view = if (view == "up") "library" else "up" },
            onSettings = { settingsOpen = true }
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(
                            grid = gridState,
                            enabled = { view != "up" && !Refresh.isBusy },
                            dragging = { cardDrag.active },
                            onBuzz = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            onPull = {
                                scope.launch {
                                    if (home == "trend") {
                                        try { Trending.silent() } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                                    } else {
                                        Refresh.runLibrary(ctx, toast)
                                    }
                                }
                            }
                        )
                        .cardReorder(
                            drag = cardDrag, grid = gridState,
                            ids = { if (lvNow.canOrder) lvNow.list.map { it.id } else null },
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
                            Box(Modifier.padding(bottom = if (home == "trend" && AppData.apis.key.isNotEmpty()) 0.dp else 12.dp)) {
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
                            itemsIndexed(cards, key = { _, it -> it.id }, contentType = { _, _ -> "card" }) { i, m ->
                                MovieCard(
                                    m, AppData.statuses, AppData.SL,
                                    upd = Refresh.upd.containsKey(m.id),
                                    chg = remember(rev) { Enrich.chgSet.contains(m.id) },
                                    onClick = {
                                        if (!cardDrag.recentlyDragged()) {
                                            Enrich.chgSet.remove(m.id)
                                            add.openLib(m, false, 800L)
                                        }
                                    },
                                    // the placement animation is only needed while a card is being dragged; the modifier costs
                                    // time on every layout pass of every visible card, so normal scrolling does not carry it
                                    modifier = Modifier
                                        .padding(bottom = 14.dp)
                                        .then(
                                            if (cardDrag.active) Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = tween(180, easing = EaseCss),
                                                fadeOutSpec = null
                                            ) else Modifier
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
                        if (AppData.apis.key.isEmpty()) {
                            item(key = "trend", span = { GridItemSpan(maxLineSpan) }) {
                                TrendNone("Add your TMDB key in Settings to see Trending.")
                            }
                        } else {
                            for (tid in Trending.ORDER) {
                                item(key = "tr-$tid", span = { GridItemSpan(maxLineSpan) }) {
                                    TrendSection(
                                        id = tid,
                                        list = Trending.lists[tid],
                                        failed = Trending.failed[tid] == true,
                                        libIds = libIds,
                                        onTap = { o ->
                                            val d0 = AppData.movies.firstOrNull { it.tmdbId == o.id }
                                            val us = if (AppData.statuses.any { it.id == "upcoming" }) "upcoming"
                                            else if (AppData.statuses.any { it.id == "plan" }) "plan" else AppData.statuses[0].id
                                            add.choose(o.id, d0?.status ?: us, fromUp = true)
                                        },
                                        onRetry = { scope.launch { try { Trending.load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
                                    )
                                }
                            }
                        }
                    }
                    if (view == "up") {
                        releasesItems(
                            searchText = usText,
                            onSearchText = { usText = it; Releases.onQuery(it) },
                            libIds = libIds,
                            onOpenFilter = { k -> ufkey = k },
                            onTap = { m ->
                                val d0 = AppData.movies.firstOrNull { it.tmdbId == m.id }
                                val us = if (AppData.statuses.any { it.id == "upcoming" }) "upcoming"
                                else if (AppData.statuses.any { it.id == "plan" }) "plan" else AppData.statuses[0].id
                                add.choose(m.id, d0?.status ?: us, minMs = if (Releases.umode == "soon") 600L else 1300L, fromUp = true, initial = m.toQItem())
                            }
                        )
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

    ufkey?.let { k -> ReleaseFilterDialog(k) { ufkey = null } }

    AddDialog(add)

    // ---- Settings (#sdlg) ----------------------------------------------------------------------------------------------
    if (settingsOpen) {
        SettingsDialog(
            dirty = dirty,
            onClose = { settingsOpen = false },
            // $('#ksave').onclick after the keys were stored: the dialog closes first, then the key check runs
            onSaved = { changed, has ->
                settingsOpen = false
                if (changed) selTrend()
                scope.launch {
                    if (!changed || !has) {
                        KeySave.after(view == "up")
                        if (changed) {
                            try { Trending.load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                        }
                    } else {
                        // apiBusy(keyLoad().then(after), 900, 45000)
                        keyBusy = true
                        val t0 = System.currentTimeMillis()
                        val job = async {
                            val e = KeySave.keyLoad(home == "trend" && view != "up")
                            KeySave.after(view == "up")
                            e
                        }
                        val err = withTimeoutOrNull(45000L) { job.await() }
                        val wait = 900L - (System.currentTimeMillis() - t0)
                        if (wait > 0) delay(wait)
                        keyBusy = false
                        if (err != null) toast.show(err, 4000L)
                    }
                }
            },
            onExport = { export.start { settingsOpen = false } },
            // Import: the dialog stays open under the progress dialog and closes when the import is over
            onImport = { imp.start(closeSettings = { settingsOpen = false }, selTrend = { selTrend() }, keyBusy = { runKeyBusy() }) }
        )
    }

    if (splashGone && !loaded) {
        MvApiLoadingDialog(caption = progress, title = "Loading your library")
    }

    // Export: "Exporting backup" progress and the green success dialog
    export.progress?.let { MvApiLoadingDialog(caption = it, title = "Exporting backup") }
    export.ok?.let { MvOkDialog(title = "Export successful", message = it.message, onOk = { export.dismissOk() }, code = it.code) }

    // Import: "Importing backup" progress and the green result dialogs
    ImportDialogs(imp)

    // #apild after Settings > Save (cannot be closed by the user)
    if (keyBusy) {
        MvApiLoadingDialog(caption = "")
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
