package com.trackerxx.movieverse.ui.add

import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.Fx
import com.trackerxx.movieverse.ui.library.LibraryLogic
import com.trackerxx.movieverse.util.Fmt
import com.trackerxx.movieverse.util.Js
import com.trackerxx.movieverse.util.RateClass

/** colour of a number: green / amber / red (the .cg .cy .cr classes) or plain */
enum class Tone { Good, Mid, Bad, None }

/** one page of the Rating tile (IMDb, Letterboxd, Tomatoes ...) */
class RatePage(
    val label: String,
    val value: String,
    val tone: Tone,
    val suffix: String,
    val votes: String,
    val voteLabel: String
)

/** what a tile shows */
sealed class TileBody {
    class Text(val text: String, val muted: Boolean = false) : TileBody()
    object Shimmer : TileBody()
    class Rating(val pages: List<RatePage>) : TileBody()
}

class TileSpec(
    val label: String,
    val body: TileBody,
    val wide: Boolean = false,
    val inPair: Boolean = false,
    val toggleSeasons: Boolean = false,
    val chg: Boolean = false
)

/** a row of the info grid: one wide tile, a locked pair of tiles, or a half tile (two half tiles share a row) */
sealed class InfoItem {
    class Wide(val t: TileSpec) : InfoItem()
    class Pair(val a: TileSpec, val b: TileSpec) : InfoItem()
    class Half(val t: TileSpec) : InfoItem()
}

/** budget / collection and the verdict (Blockbuster, Hit ...) */
class BoxInfo(val b: Double, val r: Double, val c: String, val src: String, val bo: Boolean, val go: Boolean) {
    var x = 0.0
    var verdict = ""
    var tone: VerdictTone = VerdictTone.Good
}

enum class VerdictTone { Running, Good, Average, Flop, Disaster }

object InfoModel {

    private fun now() = System.currentTimeMillis().toDouble()

    /** moneyF(n, c): $160M, ₹1.2 Cr, €1.5B ... */
    fun moneyF(n: Double, c: String): String {
        val syMap = mapOf(
            "USD" to "$", "EUR" to "€", "GBP" to "£", "INR" to "₹", "BDT" to "৳", "AUD" to "A$", "CAD" to "C$",
            "JPY" to "¥", "KRW" to "₩", "CNY" to "CN¥", "NZD" to "NZ$"
        )
        val sy = syMap[c] ?: (if (c.isNotEmpty()) "$c " else "")
        val dotZero = Regex("\\.0$")
        if (c == "INR" || c == "BDT") {
            if (n >= 1e7) return sy + Js.toFixed(n / 1e7, if (n >= 1e9) 0 else 1).replace(dotZero, "") + " Cr"
            if (n >= 1e5) return sy + Js.toFixed(n / 1e5, 1).replace(dotZero, "") + " L"
        }
        if (n >= 1e9) return sy + Js.toFixed(n / 1e9, 2).replace(Regex("\\.?0+$"), "") + "B"
        if (n >= 1e6) return sy + Js.toFixed(n / 1e6, if (n >= 1e8) 0 else 1).replace(dotZero, "") + "M"
        if (n >= 1e3) return sy + Js.round(n / 1e3).toLong() + "K"
        return sy + Js.round(n).toLong()
    }

    private fun pos(v: Any?): Double {
        val d = Js.parseFloat(v)
        return if (d > 0) d else 0.0
    }

    /** boxOf(m): budget + collection. MDBList first (USD converted to the country's currency), then Wikipedia; never mixed. */
    fun boxOf(m: Movie?): BoxInfo? {
        if (m == null || m.tmdbId == 0L || m.tmdbId < 0 || Enrich.unrel(m)) return null
        val md = m.obj("md")
        val mB = if (md != null) pos(md.opt("bud")) else 0.0
        val mR = if (md != null) pos(md.opt("rev")) else 0.0
        val wB = pos(m.j.opt("wbud"))
        val wR = pos(m.j.opt("wrev"))
        val B: Double
        val R: Double
        val C: String
        val src: String
        if (mB != 0.0 || mR != 0.0) {
            C = Fx.currencyOf(m.str("country"))
            val fr = Fx.rate(C)
            B = mB * fr
            R = mR * fr
            src = "MDBList"
        } else if (wB != 0.0 || wR != 0.0) {
            B = wB
            R = wR
            C = m.str("wcur") ?: ""
            src = "Wikipedia"
        } else return null
        if (B > 0 && !(R > 0)) return BoxInfo(B, 0.0, C, src, true, false)
        if (R > 0 && !(B > 0)) return BoxInfo(0.0, R, C, src, false, true)
        val p = BoxInfo(B, R, C, src, false, false)
        val x = p.r / p.b
        val age = if (m.rel.isNotEmpty()) (now() - Fmt.dayStartMs(m.rel)) / 864e5 else 999.0
        p.x = x
        if (age < 21) { p.verdict = "Running"; p.tone = VerdictTone.Running }
        else if (x >= 3) { p.verdict = "Blockbuster"; p.tone = VerdictTone.Good }
        else if (x >= 2) { p.verdict = "Hit"; p.tone = VerdictTone.Good }
        else if (x >= 1.3) { p.verdict = "Average"; p.tone = VerdictTone.Average }
        else if (x >= 0.7) { p.verdict = "Flop"; p.tone = VerdictTone.Flop }
        else { p.verdict = "Disaster"; p.tone = VerdictTone.Disaster }
        return p
    }

    private fun tone(v: Double, rt: Boolean = false): Tone = when (Fmt.rcls(v, rt)) {
        RateClass.Good -> Tone.Good
        RateClass.Mid -> Tone.Mid
        RateClass.Bad -> Tone.Bad
        RateClass.None -> Tone.None
    }

    private fun s(v: Any?): String = if (Js.truthy(v)) v.toString() else ""

    /** ratePages(m): one page per rating source that has a value */
    fun ratePages(m: Movie): List<RatePage> {
        val p = ArrayList<RatePage>()
        val md = m.obj("md")
        val vt = md?.optJSONObject("vt")
        fun add(l: String, v: String, t: Tone, suf: String, vv: String?, vl: String = "Votes") {
            p.add(RatePage(l, v, t, suf, vv ?: "", vl))
        }
        fun mdv(k: String): String = if (md != null) s(md.opt(k)) else ""
        fun vts(k: String): String = if (vt != null) s(vt.opt(k)) else ""

        val rv = LibraryLogic.rr(m)
        if (rv != null) {
            val votes = if (md != null && Js.truthy(md.opt("i")) && Js.truthy(md.opt("v"))) md.opt("v").toString() else s(m.j.opt("vc"))
            add("IMDb", rv, tone(Js.parseFloat(rv) * 10), "", votes)
        }
        if (mdv("l").isNotEmpty()) add("Letterboxd", mdv("l"), tone(Js.parseFloat(mdv("l")) * 20), "/5", vts("l"))
        val rtc = if (mdv("rt").isNotEmpty()) mdv("rt") else (if (m.truthy("rt")) Js.parseInt(m.str("rt")).let { if (it.isNaN()) "NaN" else com.trackerxx.movieverse.data.jsNumToString(it) } else "")
        if (rtc.isNotEmpty() && rtc != "NaN") add("Tomatoes", "$rtc%", tone(Js.parseInt(rtc), true), "", vts("rt"), "Reviews")
        if (mdv("ra").isNotEmpty()) add("Popcorn", mdv("ra") + "%", tone(Js.parseInt(mdv("ra")), true), "", vts("ra"))
        if (mdv("mc").isNotEmpty()) add("Metacritic", mdv("mc"), tone(Js.parseInt(mdv("mc")), true), "", vts("mc"), "Reviews")
        if (mdv("mu").isNotEmpty()) add("Metacritic Users", mdv("mu"), tone(Js.parseFloat(mdv("mu")) * 10), "", vts("mu"))
        if (m.truthy("tm")) {
            val tmv = m.num("tmv") ?: 0.0
            add("TMDb", m.str("tm") ?: "", tone(Js.parseFloat(m.str("tm")) * 10), "", if (tmv > 0) Fmt.fmtV(tmv) else "")
        }
        if (mdv("tr").isNotEmpty()) add("Trakt", mdv("tr"), tone(Js.parseFloat(mdv("tr")) * 10), "", vts("tr"))
        if (mdv("eb").isNotEmpty()) add("RogerEbert", mdv("eb"), tone(Js.parseFloat(mdv("eb")) * 25), "/4", "", "Reviews")
        if (mdv("ma").isNotEmpty()) add("MyAnimeList", mdv("ma"), tone(Js.parseFloat(mdv("ma")) * 10), "", vts("ma"))
        return p
    }

    private fun chg(m: Movie, label: String): Boolean = Enrich.chgF[m.id]?.get(label.split(" · ")[0]) == true

    /** infoH(m): the tiles of the info grid, in order, packed like a dense 2-column grid */
    fun build(m: Movie, rsPage: Int, soOpen: Boolean): List<InfoItem> {
        val unrel = Enrich.unrel(m)
        val ld = m.ld && !unrel
        val tiles = ArrayList<TileSpec>()
        val lang = m.str("lang")
        if (!lang.isNullOrEmpty()) tiles.add(TileSpec("Language", TileBody.Text(lang), chg = chg(m, "Language")))
        val sl = m.arr("sl")
        val hasSl = sl != null && sl.length() > 0
        if (m.tmdbId < 0 && !m.isNull("seasons")) {
            tiles.add(
                TileSpec(
                    "Seasons", TileBody.Text((m.str("seasons") ?: "") + (if (hasSl) (if (soOpen) " ▴" else " ▾") else "")),
                    toggleSeasons = hasSl, chg = chg(m, "Seasons")
                )
            )
            tiles.add(TileSpec("Episodes", TileBody.Text(m.str("eps") ?: ""), chg = chg(m, "Episodes")))
        }
        if (ld) {
            tiles.add(TileSpec("Rating", TileBody.Shimmer))
            tiles.add(TileSpec("Votes", TileBody.Shimmer))
        }
        val RP = if (unrel || ld) emptyList() else ratePages(m)
        if (RP.isNotEmpty()) tiles.add(TileSpec("Rating", TileBody.Rating(RP), chg = chg(m, "Rating")))
        else if (!ld) tiles.add(TileSpec("Rating", TileBody.Text("N/A", muted = true), chg = chg(m, "Rating")))
        if (RP.isNotEmpty() && RP.any { it.votes.isNotEmpty() }) {
            val vi = minOf(rsPage, RP.size - 1)
            tiles.add(TileSpec(RP[vi].voteLabel, TileBody.Text(RP[vi].votes.ifEmpty { "N/A" }), chg = chg(m, RP[vi].voteLabel)))
        }
        if (m.tmdbId < 0 && m.truthy("ottChk")) {
            val od1 = if (m.rel.isNotEmpty()) LibraryLogic.fmtDate(m.rel) + (if (m.rel > Fmt.todayL()) " (coming soon)" else "") else "N/A"
            tiles.add(TileSpec("OTT release", TileBody.Text(od1), wide = tiles.size % 2 == 0, chg = chg(m, "OTT release")))
        }
        val items = ArrayList<InfoItem>()
        val halves = ArrayList<TileSpec>()
        val all = ArrayList<Any>()   // TileSpec or InfoItem.Pair in order
        all.addAll(tiles)
        if (soOpen && m.tmdbId < 0 && hasSl) {
            val lines = (0 until sl!!.length()).joinToString("\n") { i ->
                val x = sl.optJSONArray(i)
                val e = x?.opt(1)
                val n = if (e is Number) e.toDouble() else Double.NaN
                "Season " + (x?.opt(0)?.toString() ?: "") + " · " + (x?.opt(1)?.toString() ?: "") + " episode" + (if (n == 1.0) "" else "s")
            }
            all.add(TileSpec("Episodes per season", TileBody.Text(lines), wide = true))
        }
        val gs = m.strList("genres")
        if (!gs.isNullOrEmpty()) all.add(TileSpec("Genre", TileBody.Text(gs.joinToString(", ")), wide = true))
        all.addAll(relTiles(m))
        all.addAll(boxTiles(m))

        // fillGaps: when the number of half tiles is odd, the last one spans the whole row
        val halfIdx = all.indices.filter { val t = all[it]; t is TileSpec && !t.wide && !t.inPair }
        val lastHalf = if (halfIdx.size % 2 == 1) halfIdx.last() else -1
        for ((i, x) in all.withIndex()) {
            when (x) {
                is TileSpec -> {
                    val wide = x.wide || i == lastHalf
                    if (wide) items.add(InfoItem.Wide(x)) else items.add(InfoItem.Half(x))
                }
                is InfoItem.Pair -> items.add(x)
            }
        }
        return items
    }

    private fun relTiles(m: Movie): List<Any> {
        if (m.tmdbId < 0) return emptyList()
        if (m.tmdbId == 0L || !m.truthy("ottChk")) return emptyList()
        val st = Enrich.ottState(m)
        val direct = m.truthy("direct")
        val cin = if (m.rel.isNotEmpty() && !direct) LibraryLogic.fmtDate(m.rel) + (if (m.rel > Fmt.todayL()) " (coming soon)" else "") else "N/A"
        val od = if (st == "tba") "N/A" else (if (!m.str("ottDate").isNullOrEmpty()) LibraryLogic.fmtDate(m.str("ottDate")) else "Streaming now")
        return if (direct) listOf(TileSpec("OTT release", TileBody.Text(od), chg = chg(m, "OTT release")))
        else listOf(
            InfoItem.Pair(
                TileSpec("In cinemas", TileBody.Text(cin), inPair = true, chg = chg(m, "In cinemas")),
                TileSpec("OTT release", TileBody.Text(od), inPair = true, chg = chg(m, "OTT release"))
            )
        )
    }

    private fun boxTiles(m: Movie): List<Any> {
        val p = boxOf(m) ?: return emptyList()
        if (p.go) return listOf(TileSpec("Collection · " + p.src, TileBody.Text(moneyF(p.r, p.c)), chg = chg(m, "Collection")))
        val bt = TileSpec("Budget", TileBody.Text(moneyF(p.b, p.c)), inPair = !p.bo, chg = chg(m, "Budget"))
        if (p.bo) return listOf(bt)
        return listOf(
            InfoItem.Pair(bt, TileSpec("Collection · " + p.src, TileBody.Text(moneyF(p.r, p.c)), inPair = true, chg = chg(m, "Collection")))
        )
    }
}
