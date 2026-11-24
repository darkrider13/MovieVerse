package com.trackerxx.movieverse.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.trackerxx.movieverse.data.Folder
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.data.Status
import com.trackerxx.movieverse.data.jsNumToString
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import java.text.Collator
import java.util.Locale

/** ff : the genre / rating / year / sort filters of the Library page (reset whenever the tab or folder changes) */
class FilterState(
    val g: List<String> = emptyList(),
    val r: String = "",
    val y: String = "",
    val s: String = "new"
) {
    val any: Boolean get() = g.isNotEmpty() || r.isNotEmpty() || y.isNotEmpty() || s != "new"
}

val SORTS = listOf(
    "new" to "My order (new on top)", "old" to "Oldest added", "rh" to "Highest rating", "rl" to "Lowest rating",
    "yn" to "Newest year", "yo" to "Oldest year", "az" to "Title A–Z", "za" to "Title Z–A"
)

/** The Library page's own state: tab, folder, search text, filters, how many cards are loaded (lim). */
@Stable
class LibraryState {
    // no "All" tab any more: the Library opens on the first status (Coming Soon); HomeScreen corrects it when that status does not exist
    var tab by mutableStateOf("upcoming")
    var folder by mutableStateOf<String?>(null)
    var country by mutableStateOf("all")

    /** search text that is applied (the box itself is live, the list follows 250 ms after the last key) */
    var q by mutableStateOf("")
    var lim by mutableIntStateOf(60)
    var ff by mutableStateOf(FilterState())

    /** "tab:folder" the current filters belong to (ffor) */
    var ffor: String? = null

    /** tap on a tab / "Your Library": back to the start of the list */
    fun openTab(t: String) {
        tab = t
        folder = null
        country = "all"
        lim = 60
    }
}

/** everything the Library page draws, worked out from the data */
class LibView(
    val tabs: List<Pair<String, String>>,   // (id, label)
    val fo: List<Folder>,                    // folders of this tab
    val folder: String?,                     // selected folder (null = All)
    val allCount: Int,                       // number on the "All" chip
    val folderCounts: Map<String, Int>,
    val list: List<Movie>,                   // cards to draw (already cut to lim)
    val total: Int,                          // cards before the cut
    val inTabCount: Int,                     // the number box next to the search
    val flat: Boolean,
    val emptyText: String?,                  // text when there is nothing to show
    val showFilters: Boolean,
    private val ord: Lazy<List<String>>?,    // ids of the whole tab in "my order" (null = cards cannot be dragged here); sorted only when needed
    private val full: List<Movie> = list     // the whole sorted list (list = its first lim cards)
) {
    /** true when the cards of this view can be dragged (cheap, no sorting) */
    val canOrder: Boolean get() = ord != null

    /** ids of the whole tab in "my order" (sorted on first use, only when a card is dropped) */
    val ordCtx: List<String>? get() = ord?.value

    /** the same page with only the first [lim] cards: cheap, nothing is filtered or sorted again */
    fun cut(lim: Int): LibView =
        LibView(tabs, fo, folder, allCount, folderCounts, full.take(lim), total, inTabCount, flat, emptyText, showFilters, ord, full)
}

private const val LOADING = "\u0001loading"

object LibraryLogic {
    private val collator: Collator by lazy { Collator.getInstance(Locale.getDefault()) }

    // ---- small helpers (rr, sc, scol, stTag, fmt, fmtK) -------------------------------------------------------------------

    /** sc(m): my own score (stars x 2 when only stars exist) */
    fun scoreOf(m: Movie): Double {
        if (!m.isNull("score")) return m.num("score") ?: 0.0
        val r = m.num("rating") ?: 0.0
        return if (r != 0.0 && !r.isNaN()) r * 2 else 0.0
    }

    /** rr(m): IMDb rating text or null */
    fun rr(m: Movie): String? = Enrich.imdbRating(m)

    /** parseFloat(rr(m)[1]) || 0 */
    fun imdbValue(m: Movie): Double {
        val v = Js.parseFloat(rr(m))
        return if (v.isNaN()) 0.0 else v
    }

    /** fmt(d): "05 Mar 2027" ("TBA" when empty) */
    fun fmtDate(d: String?): String {
        if (d.isNullOrEmpty()) return "TBA"
        val a = d.split("-")
        val mo = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val mi = (a.getOrNull(1)?.toIntOrNull() ?: 0) - 1
        return (a.getOrNull(2) ?: "undefined") + " " + (mo.getOrNull(mi) ?: "undefined") + " " + (a.getOrNull(0) ?: "")
    }

    /** stTag(m): the coloured tag on a card */
    fun stTag(m: Movie, SL: Map<String, String>): String {
        val od = m.str("ottDate")
        return if (m.status == "upcoming" && !od.isNullOrEmpty()) "OTT " + fmtDate(od) else (SL[m.status] ?: m.status)
    }

    /** fmtK(n): 1234 -> "1.234K" */
    fun fmtK(n: Int): String {
        if (n < 1000) return n.toString()
        val m = n >= 1000000
        val d = if (m) 1000000 else 1000
        val l = if (m) 6 else 3
        val w = n / d
        var r = (n % d).toString()
        while (r.length < l) r = "0$r"
        r = r.trimEnd('0')
        return w.toString() + (if (r.isNotEmpty()) ".$r" else "") + (if (m) "M" else "K")
    }

    private val PAL = listOf(0xFF2F9FD6, 0xFFD6A12F, 0xFF8A63D2, 0xFFD65F2F, 0xFF2FB3A6, 0xFF00A000).map { Color(it) }

    /** scol(id): colour of a status */
    fun statusColor(id: String, statuses: List<Status>, w: Color, p: Color, n: Color): Color {
        when (id) {
            "watched" -> return w
            "plan" -> return p
            "watching" -> return n
            "upcoming" -> return Color(0xFF2F9FD6)
            "favourite" -> return Color(0xFFE84FB0)
        }
        var k = 0
        statuses.forEachIndexed { i, x -> if (x.id == id) k = i }
        return PAL[k % PAL.size]
    }

    private val HEX = Regex("^#([0-9a-fA-F]{6})$")

    /** folder colour "#rrggbb" (anything else = null, the caller uses the accent colour) */
    fun parseHex(s: String?): Color? {
        val m = HEX.matchEntire(s ?: "") ?: return null
        return Color(0xFF000000L or m.groupValues[1].toLong(16))
    }

    // ---- Coming Soon order (upGrp / upCmp) ----------------------------------------------------------------------------------

    private fun upGrp(m: Movie): Int {
        val od = m.str("ottDate") ?: ""
        if (Enrich.ottState(m) == "live" && !(od.isNotEmpty() && od > Fmt.todayL())) return 3
        if (m.rel.isNotEmpty() && m.rel <= Fmt.todayL()) return if (od.isNotEmpty()) 0 else 1
        return 2
    }

    val upCmp = Comparator<Movie> { a, b ->
        val ga = upGrp(a)
        val gb = upGrp(b)
        if (ga != gb) return@Comparator ga - gb
        val da: String
        val db: String
        when (ga) {
            0 -> { da = a.str("ottDate") ?: ""; db = b.str("ottDate") ?: "" }
            1 -> { da = b.rel; db = a.rel }
            3 -> { da = b.str("ottDate") ?: ""; db = a.str("ottDate") ?: "" }
            else -> { da = a.rel.ifEmpty { "9999" }; db = b.rel.ifEmpty { "9999" } }
        }
        if (da != db) return@Comparator if (da < db) -1 else 1
        b.added.compareTo(a.added)
    }

    // ---- sorting ----------------------------------------------------------------------------------------------------------------

    private fun sign(d: Double): Int = if (d < 0) -1 else if (d > 0) 1 else 0

    private fun yr(m: Movie): Double {
        val v = Js.parseInt(m.year)
        return if (v.isNaN()) 0.0 else v
    }

    private fun comparatorFor(key: String, tab: String): Comparator<Movie> {
        /* rating sort value: Watched tab = my own rating; every other tab = IMDb rating. Unrated / N/A always go last. */
        fun sv(m: Movie): Double = if (tab == "watched") scoreOf(m) else imdbValue(m)
        return when (key) {
            "old" -> Comparator<Movie> { a, b -> a.added.compareTo(b.added) }
            "rh" -> Comparator<Movie> { a, b ->
                val x = sv(a); val y = sv(b)
                if ((x == 0.0) != (y == 0.0)) return@Comparator if (x != 0.0) -1 else 1
                val d = sign(y - x)
                if (d != 0) d else b.added.compareTo(a.added)
            }
            "rl" -> Comparator<Movie> { a, b ->
                val x = sv(a); val y = sv(b)
                if ((x == 0.0) != (y == 0.0)) return@Comparator if (x != 0.0) -1 else 1
                val d = sign(x - y)
                if (d != 0) d else b.added.compareTo(a.added)
            }
            "yn" -> Comparator<Movie> { a, b ->
                val d = sign(yr(b) - yr(a))
                if (d != 0) d else b.added.compareTo(a.added)
            }
            "yo" -> Comparator<Movie> { a, b ->
                val d = sign(yr(a) - yr(b))
                if (d != 0) d else b.added.compareTo(a.added)
            }
            "az" -> Comparator<Movie> { a, b -> collator.compare(a.title, b.title) }
            "za" -> Comparator<Movie> { a, b -> collator.compare(b.title, a.title) }
            else -> Comparator<Movie> { a, b ->       // "new": my own order (ord) first, new on top
                val ao = a.num("ord")
                val bo = b.num("ord")
                when {
                    ao == null && bo == null -> b.added.compareTo(a.added)
                    ao == null -> -1
                    bo == null -> 1
                    else -> ao.compareTo(bo)
                }
            }
        }
    }

    // ---- the page ------------------------------------------------------------------------------------------------------------------

    /** render() of the web version, as a function: what the Library page shows for this tab / folder / search / filters */
    fun build(
        movies: List<Movie>,
        folders: List<Folder>,
        statuses: List<Status>,
        SL: Map<String, String>,
        ready: Boolean,
        tab: String,
        folderIn: String?,
        ff: FilterState,
        q: String,
        country: String,
        lim: Int
    ): LibView {
        val fo = folders.filter { it.status == tab }
        val folder = if (folderIn != null && fo.none { it.id == folderIn }) null else folderIn
        val anyF = ff.any
        val flat = tab != "all" && folder == null && anyF
        val allw = tab != "all" && folder == null && fo.isNotEmpty()
        val inTab = movies.filter { m ->
            tab == "all" || (m.status == tab && (if (folder != null) m.folder == folder else (if (flat || allw) true else m.folder.isNullOrEmpty())))
        }
        val ql = q.lowercase()
        var list = inTab.filter { m ->
            (country == "all" || (if (m.truthy("country")) m.str("country")!! else "Other") == country) &&
                (ql.isEmpty() || m.title.lowercase().contains(ql))
        }
        if (folder != null || flat || allw) {
            list = list.filter { m ->
                if (ff.g.isNotEmpty()) {
                    val gs = m.strList("genres") ?: emptyList()
                    if (gs.none { ff.g.contains(it) }) return@filter false
                }
                val rv0 = if (tab == "watched") scoreOf(m) else imdbValue(m)
                if (ff.r == "0" && rv0 != 0.0) return@filter false
                if (ff.r.isNotEmpty() && ff.r != "0" && rv0 < (ff.r.toDoubleOrNull() ?: 0.0)) return@filter false
                if (ff.y.isNotEmpty() && m.year != ff.y) return@filter false
                true
            }
        }
        var cmp: Comparator<Movie> = comparatorFor(if (tab != "all") ff.s else "new", tab)
        val upAuto = tab == "upcoming" && ff.s == "new"
        if (upAuto) cmp = upCmp
        if (allw && !upAuto && ff.s == "new") cmp = Comparator<Movie> { a, b -> b.added.compareTo(a.added) }
        // my own order can be changed by dragging, only in a plain tab / folder view
        val cmpF = cmp
        val ordCtx: Lazy<List<String>>? = if (tab != "all" && !upAuto && !flat && !allw && (folder == null || ff.s == "new"))
            lazy(LazyThreadSafetyMode.NONE) { inTab.sortedWith(cmpF).map { it.id } } else null
        if (tab == "all") {
            val wt = list.filter { it.status == "watching" }.sortedWith(cmp).take(2)
            val rkMap = mapOf("watching" to 1.5, "upcoming" to 1.0, "plan" to 2.0, "favourite" to 3.0, "watched" to 4.0)
            fun rk(m: Movie): Double {
                val wi = wt.indexOf(m)
                if (wi >= 0) return (wi - 2).toDouble()
                val r = rkMap[m.status]
                if (r != null) return r
                var k = 0
                statuses.forEachIndexed { i, x -> if (x.id == m.status) k = i }
                return (5 + k).toDouble()
            }
            list = list.sortedWith { a, b ->
                val d = sign(rk(a) - rk(b))
                if (d != 0) d else if (a.status == "upcoming" && b.status == "upcoming") upCmp.compare(a, b) else cmp.compare(a, b)
            }
        } else {
            list = list.sortedWith(cmp)
        }
        val total = list.size
        val shown = list.take(lim)
        val counts = HashMap<String, Int>()
        if (fo.isNotEmpty()) {
            for (f in fo) counts[f.id] = 0
            for (m in movies) {
                val fid = m.folder
                if (fid != null) {
                    val cur = counts[fid]
                    if (cur != null) counts[fid] = cur + 1
                }
            }
        }

        val empty: String? = if (shown.isNotEmpty()) null
        else if (flat) "No movies match these filters."
        else if (folder != null) (if (inTab.isNotEmpty()) "No movies match these filters." else "This folder is empty. Tap Add to Library to put the first film in it.")
        else if (!ready) LOADING
        else if (movies.isNotEmpty()) "No movies match this view."
        else "Your library is empty. Tap Add to Library, or browse Coming Soon."

        return LibView(
            tabs = statuses.map { it.id to (SL[it.id] ?: "") },
            fo = fo,
            folder = folder,
            allCount = movies.count { it.status == tab },
            folderCounts = counts,
            list = shown,
            total = total,
            inTabCount = inTab.size,
            flat = flat,
            emptyText = empty,
            showFilters = tab != "all",
            ord = ordCtx
        )
    }

    fun isLoadingText(t: String?) = t == LOADING

    /** "★ 9" style number text */
    fun numText(d: Double): String = jsNumToString(d)
}
