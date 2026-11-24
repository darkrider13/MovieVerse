package com.trackerxx.movieverse.ui.add

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.net.SubItem
import com.trackerxx.movieverse.net.Subs
import com.trackerxx.movieverse.ui.components.ToastState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

sealed class SubsUi {
    object Loading : SubsUi()
    object NoKey : SubsUi()
    class Error(val message: String) : SubsUi()
    object Ready : SubsUi()
}

/**
 * The Subtitles page of a movie (from SubSource): only Bangla + English, filter chips (All / Bangla / English), "Show more",
 * Download = saved into the phone's Download folder.
 */
@Stable
class SubsFlow(private val scope: CoroutineScope, private val toast: ToastState) {
    var open by mutableStateOf(false)
        private set
    var movie by mutableStateOf<Movie?>(null)
        private set
    var ui by mutableStateOf<SubsUi>(SubsUi.Loading)
        private set

    /** all | BN | EN */
    var lang by mutableStateOf("all")
        private set

    val items = mutableStateListOf<SubItem>()
    var moreBn by mutableStateOf(false)
        private set
    var moreEn by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    val busy = mutableStateListOf<String>()
    val done = mutableStateListOf<String>()

    /** set by the page (application context + storage permission for Android 9 and older) */
    var ctx: Context? = null
    var launchPerm: ((String) -> Unit)? = null
    var permDone: CompletableDeferred<Boolean>? = null

    private var pageBn = 0
    private var pageEn = 0
    private var tok = 0
    private var job: Job? = null

    /** the Subtitle button of the details page: 0 = still looking (shimmer), 1 = available, 2 = "Subtitle not found" */
    var probe by mutableStateOf(1)
        private set
    private var probeTok = 0

    /** looks for subtitles in the background when a movie page opens (subProbe); a failed look-up never blocks the button */
    fun probeFor(m: Movie) {
        val my = ++probeTok
        if (m.tmdbId == 0L || !Subs.hasKey()) {
            probe = 1
            return
        }
        probe = 0
        scope.launch {
            val has = try {
                Subs.probe(m)
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                true
            }
            if (my == probeTok) probe = if (has) 1 else 2
        }
    }

    fun openFor(m: Movie) {
        movie = m
        lang = "all"
        open = true
        load()
    }

    fun close() {
        open = false
        tok++
        job?.cancel()
    }

    fun pickLang(l: String) {
        lang = l
    }

    fun load() {
        val m = movie ?: return
        val my = ++tok
        job?.cancel()
        items.clear()
        busy.clear()
        done.clear()
        pageBn = 0; pageEn = 0; moreBn = false; moreEn = false
        if (!Subs.hasKey()) {
            ui = SubsUi.NoKey
            return
        }
        ui = SubsUi.Loading
        job = scope.launch {
            try {
                val bn = async { Subs.search(m, "BN", 1) }
                val en = async { Subs.search(m, "EN", 1) }
                val b = bn.await()
                val e = en.await()
                if (my != tok) return@launch
                items.addAll(b.items)
                items.addAll(e.items)
                pageBn = 1; pageEn = 1
                moreBn = b.more; moreEn = e.more
                ui = SubsUi.Ready
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                if (my != tok) return@launch
                ui = if (x.message == "NOKEY") SubsUi.NoKey else SubsUi.Error(x.message ?: "Could not load subtitles")
            }
        }
    }

    /** "Show more" for the language(s) that are on screen */
    fun more() {
        val m = movie ?: return
        if (loadingMore) return
        val my = tok
        val wantBn = moreBn && lang != "EN"
        val wantEn = moreEn && lang != "BN"
        if (!wantBn && !wantEn) return
        loadingMore = true
        scope.launch {
            try {
                if (wantBn) {
                    val r = Subs.search(m, "BN", pageBn + 1)
                    if (my != tok) return@launch
                    pageBn++
                    moreBn = r.more
                    items.addAll(r.items.filter { n -> items.none { it.id == n.id } })
                }
                if (wantEn) {
                    val r = Subs.search(m, "EN", pageEn + 1)
                    if (my != tok) return@launch
                    pageEn++
                    moreEn = r.more
                    items.addAll(r.items.filter { n -> items.none { it.id == n.id } })
                }
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                toast.show(x.message ?: "Could not load more")
            } finally {
                loadingMore = false
            }
        }
    }

    fun visible(): List<SubItem> = items.filter { lang == "all" || it.code == lang }

    fun canMore(): Boolean = (moreBn && lang != "EN") || (moreEn && lang != "BN")

    /** Android 9 and older need the storage permission for the Download folder */
    private suspend fun askStorage(c: Context) {
        if (android.os.Build.VERSION.SDK_INT <= 28 &&
            androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val d = CompletableDeferred<Boolean>()
            permDone = d
            launchPerm?.invoke(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            d.await()
        }
    }

    fun download(s: SubItem) {
        val m = movie ?: return
        val c = ctx ?: return
        if (busy.contains(s.id)) return
        busy.add(s.id)
        scope.launch {
            try {
                askStorage(c)
                val names = Subs.download(c, m, s)
                if (!done.contains(s.id)) done.add(s.id)
                toast.show(
                    if (names.size == 1) "Saved to Downloads\n" + names[0] else "Saved " + names.size + " files to Downloads",
                    3600
                )
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                toast.show(x.message ?: "Download failed", 3600)
            } finally {
                busy.remove(s.id)
            }
        }
    }
}
