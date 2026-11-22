package com.trackerxx.movieverse.ui.add

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.net.AwRow
import com.trackerxx.movieverse.net.AwardsFail
import com.trackerxx.movieverse.net.CastData
import com.trackerxx.movieverse.net.Credits
import com.trackerxx.movieverse.net.PersonData
import com.trackerxx.movieverse.net.TrailerInfo
import com.trackerxx.movieverse.ui.components.ToastState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * The cast carousel (#cast) and the trailer button / popup (#trl, #tdlg) of the movie form.
 * Ported from castLoad, trailerInfo + the trailer part of showForm, and openTrailer.
 */
@Stable
class CastFlow(private val scope: CoroutineScope, private val toast: ToastState) {

    // ---- cast carousel -------------------------------------------------------------------------------------------------

    /** null = the box stays hidden (castLoad: box.hidden) */
    var data by mutableStateOf<CastData?>(null)
        private set

    /** castP: the load of the current movie's cast; the form's loading gate waits for it */
    var job: Deferred<Unit>? = null
        private set
    private var tok = 0

    /** castLoad(m) */
    fun load(m: Movie?) {
        val t = ++tok
        data = null
        job = null
        if (m == null || m.tmdbId == 0L || AppData.apis.key.isEmpty()) return
        val id = m.tmdbId
        job = scope.async {
            try {
                val c = Credits.credits(id)
                if (t != tok) return@async
                val d = Credits.buildCast(c)
                if (d.crew.isEmpty() && d.cast.isEmpty()) return@async
                data = d
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    // ---- trailer button next to the title ------------------------------------------------------------------------------

    /** the red YouTube button inside the title box (#trl) is only shown when TMDB knows a trailer or teaser */
    var trailerOk by mutableStateOf(false)
        private set
    private var trailerMovie: Movie? = null

    /** the trailer part of showForm */
    fun resetTrailer(m: Movie) {
        trailerMovie = m
        trailerOk = false
        if (m.tmdbId == 0L) return
        scope.launch {
            try {
                val i = Credits.trailerInfo(m.tmdbId)
                if (trailerMovie === m && (i.t.isNotEmpty() || i.s.isNotEmpty())) trailerOk = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    // ---- trailer popup ----------------------------------------------------------------------------------------------------

    var trailer by mutableStateOf<TrailerInfo?>(null)
        private set
    var trailerTitle by mutableStateOf("")
        private set

    /** openTrailer(m) */
    fun openTrailer(m: Movie) {
        scope.launch {
            try {
                val i = Credits.trailerInfo(m.tmdbId)
                if (i.list.isEmpty()) {
                    toast.show("Trailer not found")
                    return@launch
                }
                trailerTitle = m.title
                trailer = i
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast.show(e.message ?: "Trailer not found")
            }
        }
    }

    fun closeTrailer() {
        trailer = null
    }
}

/** the Awards page of the person dialog */
sealed class AwState {
    object Loading : AwState()
    class Note(val text: String) : AwState()
    class Fail(val text: String) : AwState()
    class Rows(val rows: List<AwRow>) : AwState()
}

/** scroll positions of the person dialog; kept while the movie form is open on top so the dialog comes back exactly as it was (pRet) */
class PersonScroll {
    val body = ScrollState(0)
    val pages = LazyListState()
    val up = LazyListState()
    val mv = LazyListState()
    val aw = LazyListState()
}

/**
 * The person dialog (#pdlg): header, biography, upcoming movies, a two-page pager (Movies | Awards).
 * Ported from personOpen, pSet, the Latest / Top Rated pills and awards.
 */
@Stable
class PersonFlow(private val scope: CoroutineScope, private val onPick: (Long) -> Unit) {

    var open by mutableStateOf(false)
        private set
    var pid = 0L
        private set

    /** Loading… (data == null && !failed) */
    var failed by mutableStateOf(false)
        private set
    var data by mutableStateOf<PersonData?>(null)
        private set

    /** Latest (false) / Top Rated (true) */
    var top by mutableStateOf(false)
        private set
    var aw by mutableStateOf<AwState>(AwState.Loading)
        private set
    var scroll by mutableStateOf(PersonScroll())
        private set

    private var tok = 0

    /** pRet: the dialog was closed because a movie was tapped; it opens again when that movie's form closes */
    private var ret = false

    /** personOpen(pid) */
    fun openPerson(id: Long) {
        pid = id
        ret = false
        data = null
        failed = false
        top = false
        aw = AwState.Loading
        scroll = PersonScroll()
        open = true
        val t = ++tok
        scope.launch {
            try {
                val p = Credits.person(id)
                if (t != tok) return@launch
                data = p
                scope.launch {
                    try {
                        val rows = Credits.awards(p.wikidataId, p.all, p.name)
                        if (t != tok) return@launch
                        aw = if (rows.isEmpty()) AwState.Note("No award data found.") else AwState.Rows(rows)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AwardsFail) {
                        if (t == tok) aw = AwState.Fail(e.message ?: "Could not load awards.")
                    } catch (e: Exception) {
                        if (t == tok) aw = AwState.Fail("Could not load awards.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (t == tok) failed = true
            }
        }
    }

    /** the Retry button */
    fun retry() = openPerson(pid)

    fun close() {
        open = false
        ret = false
        tok++
    }

    /** Latest / Top Rated pills */
    fun setTop(v: Boolean) {
        if (top == v) return
        top = v
        val s = scroll
        scope.launch { s.mv.scrollToItem(0) }
    }

    /** tap on a movie: the dialog closes, the movie form opens (choose / the ulist handler) */
    fun pick(id: Long) {
        ret = true
        open = false
        onPick(id)
    }

    /** the movie form closed (dlg 'close' event): bring the person dialog back if it was left for a movie */
    fun onFormClosed() {
        if (!ret) return
        ret = false
        open = true
    }
}
