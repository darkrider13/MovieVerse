package com.trackerxx.movieverse.util

import java.util.Calendar

/** rcls(): colour class of a rating number */
enum class RateClass { None, Good, Mid, Bad }

object Fmt {

    /** fmtV(n): 1234 -> "1K", 2500000 -> "2.5M" ("" when empty / NaN) */
    fun fmtV(n: Double?): String {
        if (n == null || n.isNaN() || n == 0.0) return ""
        return if (n >= 1e6) {
            Js.toFixed(n / 1e6, 1).replace(Regex("\\.0$"), "") + "M"
        } else if (n >= 1e3) {
            Js.round(n / 1e3).toLong().toString() + "K"
        } else {
            com.trackerxx.movieverse.data.jsNumToString(n)
        }
    }

    /** rcls(n, rt): cg / cy / cr colour of a rating. rt = true for Rotten-Tomatoes style percentages. */
    fun rcls(n: Double, rt: Boolean = false): RateClass {
        if (n.isNaN()) return RateClass.None
        return if (n >= (if (rt) 60 else 70)) RateClass.Good
        else if (n >= (if (rt) 40 else 50)) RateClass.Mid
        else RateClass.Bad
    }

    private val CSH = mapOf(
        "united states of america" to "USA", "united states" to "USA", "usa" to "USA",
        "united kingdom" to "UK", "great britain" to "UK", "united arab emirates" to "UAE",
        "russian federation" to "Russia", "czech republic" to "Czechia", "republic of korea" to "South Korea",
        "korea, republic of" to "South Korea", "democratic republic of the congo" to "DR Congo",
        "bosnia and herzegovina" to "Bosnia", "north macedonia" to "N. Macedonia",
        "dominican republic" to "Dominican Rep.", "trinidad and tobago" to "Trinidad"
    )

    /** shortC(n): short country name */
    fun shortC(n: String?): String {
        val s = (n ?: "").trim()
        return CSH[s.lowercase()] ?: s
    }

    /** stars(n) */
    fun stars(n: Int): String = "★".repeat(n) + "☆".repeat(5 - n)

    /** todayL(): today's date on this phone, YYYY-MM-DD */
    fun todayL(): String {
        val c = Calendar.getInstance()
        fun z(n: Int) = if (n < 10) "0$n" else "$n"
        return c.get(Calendar.YEAR).toString() + "-" + z(c.get(Calendar.MONTH) + 1) + "-" + z(c.get(Calendar.DAY_OF_MONTH))
    }

    private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /** new Date(rel + 'T00:00:00').getTime(): local midnight of a YYYY-MM-DD date in ms, NaN when it is not a date */
    fun dayStartMs(rel: String?): Double {
        val m = DATE_RE.matchEntire(rel ?: "") ?: return Double.NaN
        return try {
            val c = Calendar.getInstance()
            c.isLenient = false
            c.clear()
            c.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), 0, 0, 0)
            c.timeInMillis.toDouble()
        } catch (e: Exception) {
            Double.NaN
        }
    }
}
