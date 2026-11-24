package com.trackerxx.movieverse.ui.library

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.data.Status
import com.trackerxx.movieverse.net.Tmdb
import com.trackerxx.movieverse.releases.Releases
import com.trackerxx.movieverse.releases.UMsg
import com.trackerxx.movieverse.trending.Trending
import com.trackerxx.movieverse.ui.theme.MvColors
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import com.trackerxx.movieverse.util.RateClass
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The poster grids (Trending, Your Library, Releases) are drawn by a WebView (assets/grid.html): the browser scrolls them on its own
 * thread, which stays smooth on weak phones. Everything else (top bar, buttons, tabs, search, dialogs, data) stays Kotlin.
 *
 * Kotlin -> page : MV.set(json) (the cards), MV.theme(json) (colours), MV.top(px) (room for the Kotlin header above the cards)
 * page -> Kotlin : GridBridge (taps, "more cards please", reorder, scrolling on / off)
 */
class GridHost {
    var web: WebView? = null

    /** scroll position of the page in px: the Kotlin header above the cards moves with it */
    var scrollY by mutableIntStateOf(0)

    /** the finger is scrolling the list right now */
    var scrolling by mutableStateOf(false)

    private var ready = false
    private val pPayload = HashMap<String, String>()      // newest data of each list (lib / trend / up)
    private var pMode: String? = null
    private var pTheme: String? = null
    private var pTop: Int = 0
    private val main = Handler(Looper.getMainLooper())

    // set by the home screen on every recomposition
    var onTapLib: (String) -> Unit = {}
    var onTapTrend: (Long) -> Unit = {}
    var onTapUp: (Long) -> Unit = {}
    var onMore: () -> Unit = {}
    var onReorder: (List<String>) -> Unit = {}
    var onRetryTrend: () -> Unit = {}
    var onRetryUp: () -> Unit = {}
    var onMoreTap: () -> Unit = {}
    var onBuzz: () -> Unit = {}

    fun post(f: () -> Unit) { main.post(f) }

    private fun send(js: String) { web?.evaluateJavascript(js, null) }

    fun markReady() {
        ready = true
        pTheme?.let { send("MV.theme(" + JSONObject.quote(it) + ")") }
        send("MV.top($pTop)")
        pMode?.let { send("MV.mode(" + JSONObject.quote(it) + ")") }
        for ((_, j) in pPayload) send("MV.set(" + JSONObject.quote(j) + ")")
    }

    /** which list is on screen: the page just shows / hides its containers, so the switch is instant (nothing is rebuilt) */
    fun setMode(m: String) {
        if (pMode == m) return
        pMode = m
        if (ready) send("MV.mode(" + JSONObject.quote(m) + ")")
    }

    fun detach() {
        ready = false
        web = null
    }

    /** newest page data (per list) that arrived while the finger was scrolling: it is sent a moment after the scrolling stops */
    private val held = HashMap<String, String>()

    fun scrollState(on: Boolean) {
        scrolling = on
        if (!on) main.postDelayed({ flushHeld() }, 150)
    }

    private fun flushHeld() {
        if (scrolling) return
        if (held.isEmpty()) return
        val all = held.values.toList()
        held.clear()
        if (ready) for (j in all) send("MV.set(" + JSONObject.quote(j) + ")")
    }

    fun setPayload(mode: String, json: String) {
        pPayload[mode] = json
        if (!ready) return
        // ratings / lists that arrive from the internet must never rebuild the page in the middle of a scroll
        if (scrolling) { held[mode] = json; return }
        held.remove(mode)
        send("MV.set(" + JSONObject.quote(json) + ")")
    }

    fun setTheme(json: String) {
        pTheme = json
        if (ready) send("MV.theme(" + JSONObject.quote(json) + ")")
    }

    /** room (in dp / CSS px) the Kotlin header takes above the first card */
    fun setTop(dp: Float) {
        pTop = dp.roundToInt()
        if (ready) send("MV.top($pTop)")
    }

    fun scrollTop() {
        web?.scrollTo(0, 0)
        scrollY = 0
    }
}

/** methods the page can call (window.Android.*) */
class GridBridge(private val h: GridHost) {
    @JavascriptInterface fun ready() { h.post { h.markReady() } }
    @JavascriptInterface fun scrolling(on: Boolean) { h.post { h.scrollState(on) } }
    @JavascriptInterface fun more() { h.post { h.onMore() } }
    @JavascriptInterface fun buzz() { h.post { h.onBuzz() } }
    @JavascriptInterface fun tapLib(id: String) { h.post { h.onTapLib(id) } }
    @JavascriptInterface fun tapTrend(id: String) { id.toLongOrNull()?.let { v -> h.post { h.onTapTrend(v) } } }
    @JavascriptInterface fun tapUp(id: String) { id.toLongOrNull()?.let { v -> h.post { h.onTapUp(v) } } }
    @JavascriptInterface fun retryTrend() { h.post { h.onRetryTrend() } }
    @JavascriptInterface fun retryUp() { h.post { h.onRetryUp() } }
    @JavascriptInterface fun moreTap() { h.post { h.onMoreTap() } }
    @JavascriptInterface fun reorder(idsJson: String) {
        val ids = ArrayList<String>()
        try {
            val a = JSONArray(idsJson)
            for (i in 0 until a.length()) ids.add(a.getString(i))
        } catch (e: Exception) {
            return
        }
        h.post { h.onReorder(ids) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GridWebView(host: GridHost, bg: Color, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(bg.toArgb())
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                isFocusableInTouchMode = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.textZoom = 100
                // no zoom / save-form work, images cached on disk
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.saveFormData = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = true
                addJavascriptInterface(GridBridge(host), "Android")
                webViewClient = WebViewClient()
                setOnScrollChangeListener { _, _, y, _, _ -> host.scrollY = y }
                host.web = this
                loadUrl("file:///android_asset/grid.html")
            }
        },
        onRelease = { w ->
            host.detach()
            w.removeJavascriptInterface("Android")
            w.destroy()
        }
    )
}

/** what the page gets: JSON built from the app's data (runs off the main thread) */
object GridJson {

    private fun hex(v: Float): Int = (v * 255f).roundToInt().coerceIn(0, 255)

    fun css(c: Color): String =
        if (c.alpha >= 0.999f) String.format(Locale.US, "#%02x%02x%02x", hex(c.red), hex(c.green), hex(c.blue))
        else String.format(Locale.US, "rgba(%d,%d,%d,%.2f)", hex(c.red), hex(c.green), hex(c.blue), c.alpha)

    fun theme(c: MvColors): String = JSONObject()
        .put("bg", css(c.bg)).put("s2", css(c.s2)).put("line", css(c.line))
        .put("mut", css(c.mut)).put("ink", css(c.ink)).put("acc", css(c.acc))
        .put("w", css(c.w)).put("p", css(c.p)).put("n", css(c.n))
        .toString()

    private fun rc(cls: RateClass): String = when (cls) {
        RateClass.Good -> "g"
        RateClass.Bad -> "b"
        RateClass.Mid -> "m"
        else -> "n"
    }

    private fun msg(text: String, load: Boolean = false, retry: Boolean = false): JSONObject =
        JSONObject().put("t", text).put("load", load).put("retry", retry)

    /** Your Library */
    fun lib(
        lv: LibView, cards: List<Movie>, statuses: List<Status>, SL: Map<String, String>, c: MvColors,
        upd: Set<String>, chg: Set<String>
    ): String {
        val o = JSONObject().put("mode", "lib").put("order", lv.canOrder)
        val et = lv.emptyText
        if (et != null) {
            o.put("msg", if (LibraryLogic.isLoadingText(et)) msg("Loading your library…", load = true) else msg(et))
            return o.toString()
        }
        val arr = JSONArray()
        for (m in cards) {
            val j = JSONObject()
            j.put("id", m.id)
            j.put("t", m.title)
            j.put("y", m.year + (if (m.tmdbId < 0) " · TV" else ""))
            j.put("p", Tmdb.imgUrl(m.poster, "w342") ?: "")
            j.put("tg", LibraryLogic.stTag(m, SL))
            j.put("tc", css(LibraryLogic.statusColor(m.status, statuses, c.w, c.p, c.n)))
            val r = LibraryLogic.rr(m)
            if (r != null) {
                j.put("r", r)
                j.put("rc", rc(Fmt.rcls(Js.parseFloat(r) * 10)))
            } else j.put("rc", "n")
            val s = LibraryLogic.scoreOf(m)
            if (s != 0.0 && !s.isNaN()) j.put("s", LibraryLogic.numText(s))
            if (upd.contains(m.id)) j.put("u", true)
            if (chg.contains(m.id)) j.put("c", true)
            arr.put(j)
        }
        o.put("cards", arr)
        o.put("dots", lv.total > cards.size)
        return o.toString()
    }

    /** Trending rows */
    fun trend(libIds: Set<Long>): String {
        val o = JSONObject().put("mode", "trend")
        if (AppData.apis.key.isEmpty()) {
            o.put("msg", msg("Add your TMDB key in Settings to see Trending."))
            return o.toString()
        }
        val rows = JSONArray()
        for (tid in Trending.ORDER) {
            val list = Trending.lists[tid]
            val failed = Trending.failed[tid] == true
            if (list.isNullOrEmpty() && !failed) continue
            val row = JSONObject().put("id", tid).put("title", Trending.TITLES[tid] ?: "").put("failed", list.isNullOrEmpty())
            val items = JSONArray()
            if (!list.isNullOrEmpty()) {
                val rk = tid == "m"
                val shown = list.take(if (rk) 10 else 20)
                for ((ri, t) in shown.withIndex()) {
                    val j = JSONObject()
                    j.put("id", t.id.toString())
                    j.put("t", t.t)
                    j.put("p", Tmdb.imgUrl(t.p, "w185") ?: "")
                    j.put("d", t.d.take(4) + (if (t.tv) " · TV" else ""))
                    val x = t.x
                    if (!x.isNullOrEmpty()) {
                        j.put("x", x)
                        j.put("xc", rc(Fmt.rcls(Js.parseFloat(x) * 10)))
                    }
                    if (libIds.contains(t.id)) j.put("lib", true)
                    if (rk) j.put("r", ri + 1)
                    items.put(j)
                }
            }
            row.put("items", items)
            rows.put(row)
        }
        o.put("rows", rows)
        return o.toString()
    }

    /** Releases list */
    fun up(libIds: Set<Long>, lib: Color, tagBg: Color): String {
        val o = JSONObject().put("mode", "up")
        val searching = Releases.usq.isNotEmpty()
        val m = Releases.msg
        if (m !is UMsg.None) {
            o.put(
                "msg",
                when (m) {
                    is UMsg.Loading -> msg("Loading…", load = true)
                    is UMsg.Empty -> msg(m.text)
                    is UMsg.Retry -> msg(m.text, retry = true)
                    else -> msg("")
                }
            )
            return o.toString()
        }
        val list = if (searching) Releases.sres else Releases.items
        val arr = JSONArray()
        for (u in list) {
            val inLib = libIds.contains(u.id)
            val j = JSONObject()
            j.put("id", u.id.toString())
            j.put("t", u.title)
            j.put("y", u.release.take(4))
            j.put("p", Tmdb.imgUrl(u.poster, "w342") ?: "")
            j.put("tg", if (inLib) "In Library" else Releases.upTag(u))
            j.put("tc", css(if (inLib) lib else tagBg))
            arr.put(j)
        }
        o.put("cards", arr)
        if (!searching && Releases.moreVisible) o.put("more", Releases.moreText)
        return o.toString()
    }
}
