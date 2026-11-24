package com.trackerxx.movieverse.ui.add

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class SubsUi {
    object Loading : SubsUi()
    object NoKey : SubsUi()
    class Error(val message: String) : SubsUi()
    object Ready : SubsUi()
}

/**
 * The Subtitles page of a movie (from SubSource): only Bangla + English, filter chips (All / Bangla / English), "Show more",
 * Download = saved into the phone's Movies folder.
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

    /** the subtitle whose details are open (only one at a time) */
    var openId by mutableStateOf<String?>(null)
        private set

    /** subtitle id -> uploader found later in the subtitle's own details ("" = none found) */
    val authors = mutableStateMapOf<String, String>()

    fun toggleDetails(id: String) {
        openId = if (openId == id) null else id
    }

    fun closeDetails() {
        openId = null
    }

    /** a double tap on the details opens the subtitle's own page on SubSource */
    fun openPage(s: SubItem) {
        val m = movie ?: return
        val c = ctx ?: return
        try {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Subs.pageUrl(m, s)))
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (x: Exception) {
            toast.show("Could not open SubSource")
        }
    }

    /** subtitles whose list entry has no uploader name: ask SubSource for each one's details, one after the other */
    private fun fillAuthors(my: Int) {
        scope.launch {
            for (s in items.toList()) {
                if (my != tok) return@launch
                if (s.author.isNotEmpty() || authors.containsKey(s.id)) continue
                val found = try {
                    Subs.authorOf(s)
                } catch (x: CancellationException) {
                    throw x
                } catch (x: Exception) {
                    val msg = x.message ?: ""
                    if (msg.contains("Too many") || msg.contains("not accepted") || msg == "NOKEY") return@launch
                    ""
                }
                if (my != tok) return@launch
                authors[s.id] = found
                delay(250)
            }
        }
    }

    /** set by the page (application context + storage permission for Android 9 and older) */
    var ctx: Context? = null
    var launchPerm: ((String) -> Unit)? = null
    var permDone: CompletableDeferred<Boolean>? = null
    var launchTree: (() -> Unit)? = null
    var treeDone: CompletableDeferred<android.net.Uri?>? = null

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
        openId = null
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
        authors.clear()
        openId = null
        pageBn = 0; pageEn = 0; moreBn = false; moreEn = false
        if (!Subs.hasKey()) {
            ui = SubsUi.NoKey
            return
        }
        ui = SubsUi.Loading
        job = scope.launch {
            try {
                // coroutineScope: a failing search is thrown HERE (caught below) instead of cancelling the parent job, which crashed the app
                val (b, e) = coroutineScope {
                    val bn = async { Subs.search(m, "BN", 1) }
                    val en = async { Subs.search(m, "EN", 1) }
                    Pair(bn.await(), en.await())
                }
                if (my != tok) return@launch
                items.addAll(b.items)
                items.addAll(e.items)
                pageBn = 1; pageEn = 1
                moreBn = b.more; moreEn = e.more
                ui = SubsUi.Ready
                fillAuthors(my)
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

    /** Android 9 and older need the storage permission for the Movies folder */
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

    /**
     * Android 10+: the Movies folder is allowed once (the picker opens inside Movies, one tap on "Use this folder"),
     * the permission is kept, so later downloads go straight into Movies.
     */
    private suspend fun moviesTree(c: Context): android.net.Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val sp = c.getSharedPreferences("mv_subs", Context.MODE_PRIVATE)
        val saved = sp.getString("movies_tree", null)?.let { android.net.Uri.parse(it) }
        if (saved != null && c.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isWritePermission }) return saved
        val d = CompletableDeferred<android.net.Uri?>()
        treeDone = d
        launchTree?.invoke()
        val u = d.await() ?: throw java.io.IOException("Select the Movies folder to save subtitles")
        val id = android.provider.DocumentsContract.getTreeDocumentId(u)
        if (!id.endsWith(":Movies")) throw java.io.IOException("Please select the Movies folder")
        c.contentResolver.takePersistableUriPermission(
            u,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        sp.edit().putString("movies_tree", u.toString()).apply()
        return u
    }

    fun download(s: SubItem) {
        val m = movie ?: return
        val c = ctx ?: return
        if (busy.contains(s.id)) return
        busy.add(s.id)
        scope.launch {
            try {
                askStorage(c)
                val tree = moviesTree(c)
                val names = Subs.download(c, m, s, tree)
                if (!done.contains(s.id)) done.add(s.id)
                toast.show(
                    if (names.size == 1) "Saved to Movies\n" + names[0] else "Saved " + names.size + " files to Movies",
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

/** opens the folder picker inside Movies (Android 10+) */
class PickMoviesFolder : androidx.activity.result.contract.ActivityResultContract<Unit, android.net.Uri?>() {
    override fun createIntent(context: Context, input: Unit): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Movies")
            )
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}
