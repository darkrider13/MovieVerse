package com.trackerxx.movieverse.ui.add

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.net.QItem
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.ui.components.ModalResult
import com.trackerxx.movieverse.ui.components.ModalSpec
import com.trackerxx.movieverse.ui.components.ModalState
import com.trackerxx.movieverse.ui.components.ToastState
import com.trackerxx.movieverse.ui.library.LibraryLogic
import com.trackerxx.movieverse.util.Countries
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** what the search part of the Add dialog shows under the search box */
sealed class FindUi {
    object Idle : FindUi()
    object NoKey : FindUi()
    object Searching : FindUi()
    class Suggest(val items: List<QItem>) : FindUi()
    class Results(val items: List<QItem>) : FindUi()
    class Error(val message: String) : FindUi()
}

/**
 * The Add / movie dialog (#dlg): search -> choose -> form, and the same form for a movie that is already in the library.
 * Ported from openAdd / search / suggest / choose / openLib / showForm / save / delete.
 */
@Stable
class AddFlow(
    private val scope: CoroutineScope,
    private val modal: ModalState,
    private val toast: ToastState
) {
    // ---- cast carousel, trailer popup, person dialog (9B) -------------------------------------------------------------
    val cast = CastFlow(scope, toast)
    val person = PersonFlow(scope) { id -> pickFromPerson(id) }
    /** the Subtitles page of the open movie */
    val subs = SubsFlow(scope, toast)

    // ---- dialog state ----------------------------------------------------------------------------------------------------
    var open by mutableStateOf(false)
        private set

    /** find | form */
    var phase by mutableStateOf("find")
        private set

    /** dlg.busy + hold: status / folder / rating / save stay hidden until everything has loaded */
    var busy by mutableStateOf(false)
        private set

    /** true from the moment a title is tapped until its full details arrive: the info grid and the cast show loading boxes */
    var placeholder by mutableStateOf(false)
        private set
    var saveEnabled by mutableStateOf(true)
        private set
    var saveLabel by mutableStateOf("Save")
        private set

    // ---- search ------------------------------------------------------------------------------------------------------------
    var query by mutableStateOf("")
        private set
    var find by mutableStateOf<FindUi>(FindUi.Idle)
        private set
    private var sres: List<QItem> = emptyList()
    private var curStatus = "watched"
    private var curFolder: String? = null
    private var timer: Job? = null
    private var tok = 0

    // ---- form ------------------------------------------------------------------------------------------------------------
    var pick by mutableStateOf<Movie?>(null)
        private set
    var editId by mutableStateOf<String?>(null)
        private set
    var isEdit by mutableStateOf(false)
        private set
    var dup by mutableStateOf(false)
        private set
    var ft by mutableStateOf("")
    var fy by mutableStateOf("")
    var fc by mutableStateOf("")
    var fstat by mutableStateOf("")
    var fsel by mutableStateOf<String?>(null)
    var fr by mutableStateOf("0.00")
    /** the rating before the box was tapped (shown faded as the hint while typing) */
    var frKeep by mutableStateOf("")
        private set

    /** page of the Rating tile (rsPage) and whether "Episodes per season" is open (soOpen) */
    var rsPage by mutableIntStateOf(0)
        private set
    var soOpen by mutableStateOf(false)
        private set

    fun rsGo(n: Int, count: Int) {
        rsPage = n.coerceIn(0, maxOf(0, count - 1))
    }

    fun toggleSeasons() {
        soOpen = !soOpen
        infoRev++
    }

    /** bumped when the movie's details changed while the form is open (the info tiles redraw) */
    var infoRev by mutableIntStateOf(0)
        private set

    private val key: String get() = AppData.apis.key

    private fun xmKeys() = AppData.apis.apiKeys("xmdb").any { it.isNotEmpty() }

    private fun firstStatus(): String = if (AppData.SL.containsKey("watched")) "watched" else AppData.statuses[0].id

    // ======================================================================================================================
    // search
    // ======================================================================================================================

    /** openAdd(): Add to Library button */
    fun openAdd(tab: String, folder: String?) {
        curStatus = if (tab == "all") firstStatus() else tab
        curFolder = folder
        editId = null
        pick = null
        tok++
        phase = "find"
        qReset()
        query = ""
        find = if (key.isEmpty()) FindUi.NoKey else FindUi.Idle
        busy = false
        open = true
    }

    private fun qReset() {
        timer?.cancel()
        sres = emptyList()
        find = FindUi.Idle
    }

    /** typing in the search box: suggestions appear 280 ms after the last key */
    fun onQuery(v: String) {
        query = v
        timer?.cancel()
        if (v.trim().isEmpty()) {
            sres = emptyList()
            find = FindUi.Idle
            return
        }
        timer = scope.launch {
            delay(280)
            suggest()
        }
    }

    private suspend fun suggest() {
        val t = query.trim()
        if (t.isEmpty() || key.isEmpty()) return
        try {
            val r = Net.qsearch.find(t)
            if (t != query.trim()) return
            sres = r
            find = FindUi.Suggest(r.take(16))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    /** Enter in the search box (and Retry) */
    fun search() {
        timer?.cancel()
        val t = query.trim()
        if (t.isEmpty()) {
            sres = emptyList()
            find = FindUi.Idle
            return
        }
        if (key.isEmpty()) return
        find = FindUi.Searching
        scope.launch {
            try {
                val r = Net.qsearch.find(t)
                if (t != query.trim()) return@launch
                sres = r
                find = FindUi.Results(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (t != query.trim()) return@launch
                find = FindUi.Error(e.message ?: "")
            }
        }
    }

    /** tap on a suggestion / result */
    fun pickResult(x: QItem) {
        timer?.cancel()
        choose(x.id, curStatus)
    }

    fun existing(id: Long): Movie? = AppData.movies.firstOrNull { it.tmdbId == id }

    fun close() {
        // a person page that was left for a movie comes back FIRST and the movie page closes only after it is on screen
        // (closing both in one frame showed the home screen for a moment between them)
        if (person.hasReturn) {
            person.returnNow()
            scope.launch {
                kotlinx.coroutines.delay(250)
                finishClose()
            }
            return
        }
        finishClose()
    }

    private fun finishClose() {
        subs.close()
        open = false
        busy = false
        placeholder = false
        person.onFormClosed()
    }

    /** a movie row of the person dialog was tapped: same as a Coming Soon card (choose(iid, status, true)) */
    private fun pickFromPerson(id: Long) {
        val d0 = AppData.movies.firstOrNull { it.tmdbId == id }
        val us = if (AppData.statuses.any { it.id == "upcoming" }) "upcoming"
        else if (AppData.statuses.any { it.id == "plan" }) "plan" else AppData.statuses[0].id
        choose(id, d0?.status ?: us, fromUp = true)
    }

    // ======================================================================================================================
    // choose a title -> form
    // ======================================================================================================================

    /** upOk(m): can this title be put in Coming Soon? */
    fun upOk(m: Movie): Boolean {
        if (m.tmdbId == 0L) return true
        if (editId != null && m.status == "upcoming") return true
        if (m.tmdbId > 0 && (m.truthy("direct") || Enrich.ottState(m) != "tba")) return false
        if (m.rel.isEmpty() || Enrich.unrel(m)) return true
        val age = System.currentTimeMillis() - Fmt.dayStartMs(m.rel)
        if (Enrich.ottState(m) != "live") return age <= 365 * 864e5
        val od = m.str("ottDate") ?: ""
        if (od.isNotEmpty() && od <= Fmt.todayL()) return System.currentTimeMillis() - Fmt.dayStartMs(od) <= 7 * 864e5
        return age <= 30 * 864e5
    }

    private fun lang(code: Any?): String {
        val c = if (Js.truthy(code)) code.toString() else return ""
        return try {
            val n = Locale(c).getDisplayLanguage(Locale.ENGLISH)
            if (n.isEmpty()) c else n
        } catch (e: Exception) {
            c
        }
    }

    /**
     * choose(id, status, fromUp, fold): a title was picked.
     * Already in the library = open it. Otherwise the form opens at once with what search knew, the full details load
     * in the background and the dialog stays "busy" for at least minMs.
     */
    fun choose(id: Long, statusIn: String, minMs: Long = 1300L, fromUp: Boolean = false, fold: String? = null, initial: QItem? = null) {
        val ex = existing(id)
        if (ex != null) {
            openLib(ex, true, minOf(minMs, 800L))
            return
        }
        val myTok = ++tok
        val t0 = System.currentTimeMillis()
        var status = statusIn
        val u = initial ?: (if (fromUp) null else sres.firstOrNull { it.id == id }) ?: com.trackerxx.movieverse.trending.Trending.find(id)
        val SL = AppData.SL
        /* an already released title from search: Plan to watch is pre-selected */
        if (!fromUp && SL.containsKey("plan") && u != null && u.releaseDate.isNotEmpty() && !Enrich.unrel(Movie().also { it.rel = u.releaseDate })) status = "plan"
        val p = Movie()
        p.tmdbId = id
        p.title = u?.title ?: ""
        p.year = (u?.releaseDate ?: "").take(4)
        p.poster = u?.poster
        p.rel = u?.releaseDate ?: ""
        p.set("ov", u?.overview ?: "")
        p.status = status
        p.folder = if (fromUp) fold else curFolder
        editId = null
        saveLabel = if (fromUp) "Add" else "Save"
        showForm(p, false, false)
        placeholder = true
        saveEnabled = false
        busy = true
        open = true

        scope.launch {
            try {
                val d = Net.tmdb.get(
                    Tmdb.path(id),
                    mapOf("append_to_response" to (if (id < 0) "external_ids,watch/providers,content_ratings" else "external_ids,release_dates,watch/providers"))
                )
                if (id < 0) {
                    d.put("title", Js.or(d.opt("name"), "")?.toString() ?: "")
                    d.put("release_date", Js.or(d.opt("first_air_date"), "")?.toString() ?: "")
                    d.put("id", id)
                }
                val pc = d.optJSONArray("production_countries")
                val cname = Countries.cName(Countries.effC(d.optJSONArray("origin_country"), pc), pc)
                val language = lang(d.opt("original_language"))
                val imdbId = d.optJSONObject("external_ids")?.opt("imdb_id")
                if (myTok != tok || !open) return@launch

                val pk = Movie()
                pk.tmdbId = d.optLong("id")
                pk.title = if (Js.truthy(d.opt("title"))) d.opt("title").toString() else ""
                pk.year = (if (Js.truthy(d.opt("release_date"))) d.opt("release_date").toString() else "").take(4)
                pk.poster = if (Js.truthy(d.opt("poster_path"))) d.opt("poster_path").toString() else null
                pk.set("country", Fmt.shortC(cname))
                pk.set("lang", language)
                val gs = ArrayList<String>()
                d.optJSONArray("genres")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.opt("name")?.let { n -> gs.add(n.toString()) } }
                pk.set("genres", gs)
                pk.set("imdb", "")
                pk.set("vc", "")
                pk.set("imdbId", if (Js.truthy(imdbId)) imdbId.toString() else "")
                pk.set("chk", 0)
                pk.set("tr", "")
                pk.status = status
                pk.folder = if (fromUp) fold else curFolder
                Enrich.applyRel(pk, d)
                /* from search, already released: only in cinemas (no OTT info) and released within ~4 months -> Coming Soon; otherwise Plan to watch */
                if (!fromUp && SL.containsKey("plan") && pk.rel.isNotEmpty() && !Enrich.unrel(pk)) {
                    val ag0 = System.currentTimeMillis() - Fmt.dayStartMs(pk.rel)
                    pk.status = if (SL.containsKey("upcoming") && Enrich.ottState(pk) == "tba" && ag0 <= 120 * 864e5) "upcoming" else "plan"
                }
                /* TV shows and direct-to-OTT movies never belong in Coming Soon by default */
                if (pk.status == "upcoming" && SL.containsKey("plan") && (id < 0 || pk.truthy("direct"))) pk.status = "plan"
                if (pk.status == "upcoming" && SL.containsKey("plan") && !upOk(pk)) pk.status = "plan"
                if (id < 0) {
                    pk.set("cert", Enrich.tvCert(d))
                    pk.set("runtime", Enrich.tvRt(d))
                    pk.set("seasons", if (Js.truthy(d.opt("number_of_seasons"))) d.opt("number_of_seasons") else 0)
                    pk.set("eps", if (Js.truthy(d.opt("number_of_episodes"))) d.opt("number_of_episodes") else 0)
                    pk.set("sl", Enrich.slOf(d))
                    pk.set("kind", "TV")
                }
                editId = null
                pk.ld = (AppData.apis.omdb.isNotEmpty() || xmKeys()) && !Enrich.unrel(pk)
                showForm(pk, false, false)
                saveLabel = if (fromUp) "Add" else "Save"
                saveEnabled = true
                open = true
                /* castP: read right after showForm started the cast load of this movie */
                val castP = cast.job

                val p0 = pk
                val PL: Deferred<Unit> = async {
                    if ((AppData.apis.omdb.isNotEmpty() || xmKeys()) && !Enrich.unrel(p0)) {
                        try { Enrich.enrich(p0) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                    }
                    p0.ld = false
                    refreshInfo(p0)
                    try {
                        Enrich.enrichMd(p0, true)
                        Enrich.enrichWiki(p0)
                        refreshInfo(p0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    }
                }
                finishLoading(myTok, t0, minMs, PL, castP)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                placeholder = false
                saveEnabled = true
                if (myTok != tok) return@launch
                if (fromUp) {
                    close()
                    toast.show(e.message ?: "")
                } else {
                    phase = "find"
                    find = FindUi.Error(e.message ?: "")
                }
            }
        }
    }

    /** the form stays held until everything has loaded (at most 8 s) and the minimum loading time has passed */
    private suspend fun finishLoading(myTok: Int, t0: Long, minMs: Long, work: Deferred<*>?, castP: Deferred<*>? = null) {
        val jobs = listOfNotNull(work, castP)
        if (jobs.isNotEmpty()) {
            /* Promise.race([Promise.all([work, castP]).catch(noop), 8 s]) */
            withTimeoutOrNull(8000L) {
                try { jobs.awaitAll() } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            }
        }
        val rem = minMs - (System.currentTimeMillis() - t0)
        if (rem > 0) delay(rem)
        if (myTok != tok || !open) return
        busy = false
        saveEnabled = true
    }

    private fun refreshInfo(m: Movie) {
        if (pick === m && open && phase == "form") infoRev++
    }

    /** openLib(m, dup, MS): open a movie that is in the library and refresh it in the background */
    fun openLib(m: Movie, dupFlag: Boolean, minMs: Long) {
        val myTok = ++tok
        val t0 = System.currentTimeMillis()
        editId = m.id
        m.ld = Enrich.needsE(m) && !Enrich.unrel(m)
        showForm(m, true, dupFlag)
        open = true
        saveEnabled = false
        busy = true
        val castP = cast.job
        val bf = Enrich.snapF?.invoke(m)
        val rp: Deferred<Unit> = scope.async {
            try {
                val x = Enrich.openRefresh(m)
                m.ld = false
                val ch = x.getOrNull(0) == true || x.getOrNull(1) == true
                if (ch) {
                    if (bf != null) Enrich.noteChg(m, bf, true)   // glowing tile only for non-quiet fields; no poster pulse from here
                    AppData.db.putMany(listOf(m))
                }
                if (editId == m.id && phase == "form") infoRev++
                if (ch) AppData.render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                m.ld = false
            }
        }
        scope.launch { finishLoading(myTok, t0, minMs, rp, castP) }
    }

    // ======================================================================================================================
    // form
    // ======================================================================================================================

    /** showForm(m, isEdit, dup) */
    private fun showForm(m: Movie, edit: Boolean, dupFlag: Boolean) {
        placeholder = false
        pick = m
        phase = "form"
        isEdit = edit
        dup = dupFlag
        ft = m.title
        fy = m.year
        fc = Fmt.shortC(m.str("country"))
        val SL = AppData.SL
        var st = if (SL.containsKey(m.status)) m.status else firstStatus()
        if (st == "upcoming" && !upOk(m)) st = if (SL.containsKey("plan")) "plan" else firstStatus()
        fstat = st
        fsel = m.folder?.takeIf { it.isNotEmpty() }
        val sc = LibraryLogic.scoreOf(m)
        fr = Js.toFixed(if (sc.isNaN()) 0.0 else sc, 2)
        frKeep = fr
        saveLabel = "Save"
        rsPage = 0
        soOpen = false
        infoRev++
        cast.resetTrailer(m)
        subs.probeFor(m)
        cast.load(m)
    }

    /** tap on a status button: the selected folder stays only when it belongs to that status */
    fun selectStatus(id: String) {
        fstat = id
        val s = fsel
        if (s != null && AppData.folders.none { it.id == s && it.status == fstat }) fsel = null
    }

    fun toggleFolder(id: String) {
        fsel = if (fsel == id) null else id
    }

    // ---- rating box (#fr) ------------------------------------------------------------------------------------------------

    /** the rating as a number 0..10 (updRs) */
    fun ratingValue(): Double {
        val v = Js.parseFloat(fr)
        return if (v.isNaN()) 0.0 else maxOf(0.0, minOf(10.0, v))
    }

    fun onFrFocus() {
        if (fr != "") frKeep = fr
        fr = ""
    }

    fun onFrChange(t: String) {
        val v = Js.parseFloat(t)
        fr = if (v > 10) "10" else if (v < 0) "0" else t
    }

    /** fmtFr() */
    fun fmtFr() {
        if (fr == "" && frKeep != "") fr = frKeep
        val v = Js.parseFloat(fr)
        fr = Js.toFixed(if (v.isNaN()) 0.0 else maxOf(0.0, minOf(10.0, v)), 2)
        frKeep = fr
    }

    // ---- save / delete ------------------------------------------------------------------------------------------------------

    private val COPY = listOf(
        "poster", "tmdbId", "lang", "genres", "imdb", "tr", "rt", "vc", "imdbId", "chk", "rel", "ottDate", "ottC", "ott", "ottChk",
        "direct", "cert", "runtime", "xm", "xvc", "md", "mdc", "tm", "ottS", "ottT", "ottCT", "om", "omvc", "wbud", "wrev", "wcur",
        "wpg", "wkc", "wkv", "wkt", "ov", "seasons", "eps", "sl", "kind"
    )

    /** returns false when nothing was saved and the title box needs the focus */
    fun save(): Boolean {
        val title = ft.trim()
        if (title.isEmpty()) return false
        if (fstat.isEmpty()) {
            toast.show("Select a status first")
            return true
        }
        fmtFr()
        val sv = Js.round(ratingValue() * 10) / 10
        var eid = editId
        var reAdd = false
        val pk = pick
        if (eid == null && pk != null && pk.tmdbId != 0L) {
            val dx = AppData.movies.firstOrNull { it.tmdbId == pk.tmdbId }
            if (dx != null) {
                eid = dx.id
                reAdd = true
            }
        }
        if (fstat == "watching") {
            val wc = AppData.movies.count { it.status == "watching" && it.id != eid }
            if (wc >= 2) {
                close()
                toast.show("Watchlist Full (2/2)\nFinish a movie before adding another.", 4000)
                return true
            }
        }
        val mv: Movie
        if (eid != null) {
            mv = AppData.movies.firstOrNull { it.id == eid } ?: return true
            if (reAdd || mv.status != fstat || (mv.folder ?: "") != (fsel ?: "")) {
                mv.remove("ord")
                mv.remove("ordAll")
                mv.added = System.currentTimeMillis()
            }
            mv.title = title
            mv.year = fy.trim()
            mv.set("country", fc.trim())
            mv.status = fstat
            mv.folder = fsel
            mv.set("score", sv)
            mv.set("rating", sv / 2)
        } else {
            mv = Movie()
            mv.title = title
            mv.year = fy.trim()
            mv.set("country", fc.trim())
            mv.status = fstat
            mv.folder = fsel
            mv.set("score", sv)
            mv.set("rating", sv / 2)
            mv.id = AppData.uid()
            if (pk != null) for (k in COPY) if (pk.has(k)) mv.j.put(k, pk.j.opt(k))
            mv.added = System.currentTimeMillis()
            AppData.movies.add(mv)
        }
        scope.launch { AppData.db.putMany(listOf(mv)) }
        close()
        AppData.render()
        return true
    }

    /** Delete button: ask, then remove the movie */
    fun delete() {
        scope.launch {
            val y = modal.ask(ModalSpec(title = "Delete this movie?", text = "It will be removed from your library.", ok = "Delete", danger = true))
            if (y is ModalResult.Ok) {
                val did = editId ?: return@launch
                AppData.movies = AppData.movies.filter { it.id != did }.toMutableList()
                AppData.db.delOne(did)
                close()
                AppData.render()
            }
        }
    }
}
