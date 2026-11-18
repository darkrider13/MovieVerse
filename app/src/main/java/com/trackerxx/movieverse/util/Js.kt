package com.trackerxx.movieverse.util

import com.trackerxx.movieverse.data.jsNumToString
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/** The few JavaScript behaviours the web version relies on, so ported logic gives the same answers. */
object Js {

    /** encodeURIComponent */
    fun encodeURIComponent(s: String): String =
        URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    /** JS truthiness of a JSON value */
    fun truthy(v: Any?): Boolean = when {
        v == null || v == JSONObject.NULL -> false
        v is Boolean -> v
        v is Number -> v.toDouble().let { it != 0.0 && !it.isNaN() }
        v is String -> v.isNotEmpty()
        else -> true
    }

    /** a || b || c : first truthy value, otherwise the last one */
    fun or(vararg v: Any?): Any? {
        for (x in v) if (truthy(x)) return x
        return v.lastOrNull()
    }

    /** JSON null counts as "missing" */
    fun clean(v: Any?): Any? = if (v == JSONObject.NULL) null else v

    private val FLOAT_RE = Regex("^[\\s\u00A0\uFEFF]*([+-]?(?:Infinity|\\d+\\.?\\d*(?:[eE][+-]?\\d+)?|\\.\\d+(?:[eE][+-]?\\d+)?))")
    private val INT_RE = Regex("^[\\s\u00A0\uFEFF]*([+-]?\\d+)")

    /** parseFloat(x): NaN when there is no number at the start */
    fun parseFloat(v: Any?): Double {
        if (v == null || v == JSONObject.NULL) return Double.NaN
        if (v is Number) return v.toDouble()
        val m = FLOAT_RE.find(v.toString()) ?: return Double.NaN
        return m.groupValues[1].toDoubleOrNull() ?: Double.NaN
    }

    /** parseInt(x, 10) as a Double (NaN when there is no integer at the start) */
    fun parseInt(v: Any?): Double {
        if (v == null || v == JSONObject.NULL) return Double.NaN
        val s = if (v is Number) jsNumToString(v.toDouble()) else v.toString()
        val m = INT_RE.find(s) ?: return Double.NaN
        return m.groupValues[1].toDoubleOrNull() ?: Double.NaN
    }

    /** n.toFixed(d): exact decimal value of the double, ties round up (same as JS) */
    fun toFixed(d: Double, digits: Int): String {
        if (d.isNaN()) return "NaN"
        if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
        return BigDecimal(d).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    }

    /** Math.round */
    fun round(x: Double): Double = Math.floor(x + 0.5)

    /** the JS \s class (also covers non-breaking space, BOM and the unicode spaces) */
    val WS = Regex("[\\s\u00A0\uFEFF\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+")
}
