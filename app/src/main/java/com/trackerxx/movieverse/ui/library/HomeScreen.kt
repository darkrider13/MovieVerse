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
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import com.trackerxx.movieverse.ui.theme.MvFixed
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
    val grid = remember { GridHost() }                  // the poster grids (WebView)
    val cNow = Mv.c
    val add = remember { AddFlow(scope, modal, toast) }
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
        AppScope.launch { try { com.trackerxx.movieverse.util.Quiet.await(); Trending.load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
    }

    // The Your Library header (tabs, search, filters) is composed in the background a moment after the app has loaded and is only
    // hidden (zero height, not placed) while Trending / Releases is open. Tapping "Your Library" then only has to show it:
    // nothing is built or measured for the first time in that frame, so the whole header appears at once.
    var libHeaderWarm by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        delay(350)
        while (grid.scrolling) delay(150)
        libHeaderWarm = true
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
    val lvHold = remember { arrayOfNulls<Any>(2) }   // [0] = LibView, [1] = the inputs it was built from
    // The heavy part (filter + sort of the whole library) does NOT depend on lib.lim. Loading the next 60 cards while scrolling used to
    // filter + sort the whole library again on the main thread (a hitch every 60 cards). Now only the cheap cut (first lim cards) re-runs.
    // A background refresh (rev bump: ratings, posters, Trending...) must never rebuild the list while the finger is scrolling.
    // The new rev is applied as soon as the scroll has stopped.
    var revApplied by remember { mutableIntStateOf(AppData.rev) }
    LaunchedEffect(grid) {
        snapshotFlow { Pair(AppData.rev, grid.scrolling) }.collect { (r, scrolling) ->
            com.trackerxx.movieverse.util.Quiet.scroll(scrolling)
            if (!scrolling) revApplied = r
        }
    }
    // While Trending / Releases is open, Trending keeps bumping rev (ratings arrive). The old code then rebuilt the whole Library list
    // (filter + sort + page payload) on the main thread in the very frame of the tap on "Your Library": that was the slow, piece by
    // piece arrival of the tabs / search row. Now the list is prepared on a background thread (never while scrolling), so the tap
    // finds it ready and only swaps it in.
    var preTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(revApplied, ready, lib.tab, folderEff, ffEff, lib.q, lib.country, lvOn) {
        if (lvOn || !ready) return@LaunchedEffect
        val inputs = listOf<Any?>(revApplied, ready, lib.tab, folderEff, ffEff, lib.q, lib.country)
        if (lvHold[0] != null && lvHold[1] == inputs) return@LaunchedEffect
        delay(250)
        while (grid.scrolling) delay(150)
        val mv = AppData.movies.toTypedArray().toList()
        val fo = AppData.folders.toList()
        val st = AppData.statuses.toList()
        val sl = AppData.SL
        val tab0 = lib.tab
        val fol0 = folderEff
        val ff0 = ffEff
        val q0 = lib.q
        val c0 = lib.country
        val built = try {
            withContext(Dispatchers.Default) { LibraryLogic.build(mv, fo, st, sl, true, tab0, fol0, ff0, q0, c0, Int.MAX_VALUE) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (built != null) {
            lvHold[0] = built
            lvHold[1] = inputs
            preTick++
        }
    }
    val lvFull = remember(revApplied, ready, lib.tab, folderEff, ffEff, lib.q, lib.country, lvOn, preTick) {
        val held = lvHold[0] as LibView?
        val inputs = listOf(revApplied, ready, lib.tab, folderEff, ffEff, lib.q, lib.country)
        // hidden: keep the last result. Coming back to Your Library: if nothing changed meanwhile, reuse it as it is
        // (no filter + sort on the main thread while the screen switches, so the switch is instant)
        if (held != null && (!lvOn || lvHold[1] == inputs)) held
        else LibraryLogic.build(
            AppData.movies.toTypedArray().toList(), AppData.folders.toList(), AppData.statuses.toList(), AppData.SL,
            ready, lib.tab, folderEff, ffEff, lib.q, lib.country, Int.MAX_VALUE
        ).also { lvHold[0] = it; lvHold[1] = inputs }
    }
    val lv = remember(lvFull, lib.lim) { lvFull.cut(lib.lim) }
    val dirty = remember(rev) { AppData.isDirty() }
    val lvNow by rememberUpdatedState(lv)

    val cards = lv.list

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

    // ---- the poster grids live in a WebView: what the page tells the app, and what the app sends to the page -----------------
    val gridMode = if (view == "up") "up" else if (home == "trend") "trend" else "lib"
    // switching Trending <-> Your Library <-> Releases only shows the list that is already built in the page: instant, no reload
    SideEffect { grid.setMode(gridMode) }
    SideEffect {
        grid.onTapLib = { id ->
            val m = AppData.movies.firstOrNull { it.id == id }
            if (m != null) {
                Enrich.chgSet.remove(m.id)
                add.openLib(m, false, 800L)
            }
        }
        grid.onTapTrend = { id ->
            val d0 = AppData.movies.firstOrNull { it.tmdbId == id }
            val us = if (AppData.statuses.any { it.id == "upcoming" }) "upcoming"
            else if (AppData.statuses.any { it.id == "plan" }) "plan" else AppData.statuses[0].id
            add.choose(id, d0?.status ?: us, fromUp = true)
        }
        grid.onTapUp = { id ->
            val m = (if (Releases.usq.isNotEmpty()) Releases.sres else Releases.items).firstOrNull { it.id == id }
            if (m != null) {
                val d0 = AppData.movies.firstOrNull { it.tmdbId == m.id }
                val us = if (AppData.statuses.any { it.id == "upcoming" }) "upcoming"
                else if (AppData.statuses.any { it.id == "plan" }) "plan" else AppData.statuses[0].id
                add.choose(m.id, d0?.status ?: us, minMs = if (Releases.umode == "soon") 600L else 1300L, fromUp = true, initial = m.toQItem())
            }
        }
        grid.onMore = {
            if (view == "library" && home == "lib" && lvNow.total > lib.lim) lib.lim += 60
            if (view == "up") Releases.checkMore()
        }
        grid.onReorder = { ids -> actions.saveOrder(ids, lvNow.ordCtx) }
        grid.onRetryTrend = { scope.launch { try { Trending.load(true) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
        grid.onRetryUp = { Releases.retry() }
        grid.onMoreTap = { Releases.loadMore() }
        grid.onBuzz = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LaunchedEffect(Unit) { grid.setTheme(GridJson.theme(cNow)) }
    LaunchedEffect(fctx) { grid.scrollTop() }
    // Trending / Releases: the page is rebuilt whenever one of the lists (or a rating that arrives later) changes
    LaunchedEffect(gridMode, libIds) {
        if (gridMode == "lib") return@LaunchedEffect
        val wGreen = cNow.w
        val tagBg = MvFixed.RateBg
        snapshotFlow { if (gridMode == "trend") GridJson.trend(libIds) else GridJson.up(libIds, wGreen, tagBg) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect { grid.setPayload(gridMode, it) }
    }
    // Your Library: the cards (already filtered and sorted above) go to the page
    val updN = Refresh.upd.size
    // (also while Trending is on screen: the Library is prepared in the background, so switching to it is instant)
    LaunchedEffect(lv, revApplied, updN) {
        val sts = AppData.statuses.toList()
        val sl = AppData.SL
        val updSet = HashSet(Refresh.upd.keys)
        val chgCopy = HashSet(Enrich.chgSet)
        val json = withContext(Dispatchers.Default) { GridJson.lib(lv, cards, sts, sl, cNow, updSet, chgCopy) }
        grid.setPayload("lib", json)
    }

    fun toTop() { grid.scrollTop() }

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

    // where the tabs / search row sit on screen (a pull-down refresh must not start there): y in the window, height, y of the list box
    val barBox = remember { FloatArray(3) }
    // A swipe that starts on the Kotlin header (welcome box / buttons) scrolls the WebView page too. It now flings like a normal
    // list (before it stopped dead the moment the finger lifted, which felt like the page got stuck).
    val headScroll = rememberScrollableState { d ->
        val w = grid.web
        if (w == null) 0f else {
            val before = w.scrollY
            w.scrollBy(0, (-d).roundToInt())
            (before - w.scrollY).toFloat()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopNav(
            releasesLabel = if (view == "up") "Home" else "Releases",
            dirty = dirty,
            onBrand = { view = "library" },
            onReleases = { view = if (view == "up") "library" else "up" },
            onSettings = { settingsOpen = true }
        )
        // clipToBounds: the header (translated up while scrolling) must never draw over the top bar
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    .onGloballyPositioned { barBox[2] = it.positionInWindow().y }
                    .pullRefreshWeb(
                        atTop = { grid.scrollY == 0 },
                        blocked = { y ->
                            val yw = y + barBox[2]
                            home == "lib" && view == "library" && barBox[1] > 0f && yw >= barBox[0] && yw <= barBox[0] + barBox[1]
                        },
                        enabled = { view != "up" && !Refresh.isBusy },
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
            ) {
                // the cards (WebView scrolls them itself)
                GridWebView(grid, cNow.bg, Modifier.fillMaxSize())

                // the Kotlin header (welcome box, buttons, tabs, search ...) sits on top and moves up with the page
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = -grid.scrollY.toFloat() }
                        .onSizeChanged { grid.setTop(it.height / dens) }
                        .scrollable(headScroll, Orientation.Vertical)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    if (view == "library") {
                        Box(Modifier.padding(bottom = 14.dp)) { Hero(AppData.uname, onAdd = { add.openAdd(lib.tab, folderEff) }) }
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
                    val libShown = view == "library" && home == "lib"
                    if (libShown || libHeaderWarm) {
                        Column(
                            Modifier.layout { m, c ->
                                val p = m.measure(c)
                                layout(p.width, if (libShown) p.height else 0) { if (libShown) p.placeRelative(0, 0) }
                            }
                        ) {
                            Column(
                                Modifier
                                    .padding(bottom = 12.dp)
                                    .onGloballyPositioned { barBox[0] = it.positionInWindow().y; barBox[1] = it.size.height.toFloat() }
                            ) {
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
                            if (lv.showFilters) {
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
                    }
                    if (view == "up") {
                        ReleasesHeader(
                            searchText = usText,
                            onSearchText = { usText = it; Releases.onQuery(it) },
                            onOpenFilter = { k -> ufkey = k }
                        )
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
