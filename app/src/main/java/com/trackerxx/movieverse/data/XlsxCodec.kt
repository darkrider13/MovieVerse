package com.trackerxx.movieverse.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer

/**
 * How one JSON value of the backup is stored in ONE Excel cell, and how it is read back (export and import use the same rules,
 * so export -> import gives exactly the same value, with the same type):
 *
 *  - string   -> text cell. A string that could be mistaken for something else (empty, "null", starts with { or [ , starts with the
 *                invisible mark U+200B) gets one invisible U+200B in front; the import removes it again.
 *  - number   -> number cell (whole numbers come back as Long, the others as Double)
 *  - boolean  -> TRUE / FALSE cell
 *  - null     -> the text  null
 *  - object / array -> its JSON text (starts with { or [ )
 *  - a field that does not exist (JS undefined) -> no cell at all (empty)
 *  - text longer than 30000 characters (Excel allows 32767 per cell) continues in the extra columns "<name>~2", "<name>~3", ...
 *  - characters XML cannot hold (control characters, a lone surrogate, CR) are written as _xHHHH_ (the same way Excel does it)
 */
object XlsxCodec {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** invisible zero width space: the "this text is really a text" mark */
    const val MARK = '\u200B'

    /** longest piece of text that is put in one cell */
    const val CHUNK = 30000

    /** "no value" (the cell was empty) */
    val MISSING = Any()

    /** the text of a cell for a string value */
    fun strText(s: String): String =
        if (s.isEmpty() || s == "null" || s[0] == '{' || s[0] == '[' || s[0] == MARK) "" + MARK + s else s

    /** cell text -> JSON value (MISSING for an empty cell) */
    fun fromText(t: String): Any {
        if (t.isEmpty()) return MISSING
        val c = t[0]
        if (c == MARK) return t.substring(1)
        if (t == "null") return JSONObject.NULL
        if (c == '{') {
            try { return JSONObject(t) } catch (e: Exception) { }
        } else if (c == '[') {
            try { return JSONArray(t) } catch (e: Exception) { }
        }
        return t
    }

    /** the text of a cell that holds a plain text (Settings sheet): the mark is removed, nothing else is interpreted */
    fun plain(cell: Any?): String? = when (cell) {
        null -> null
        is String -> if (cell.isEmpty()) null else if (cell[0] == MARK) cell.substring(1) else cell
        is Number -> jsNumToString(cell.toDouble())
        is Boolean -> cell.toString()
        else -> null
    }

    /** the text of a number cell -> Long (exact) or Double; MISSING when it is not a usable number */
    fun numValue(text: String): Any {
        val t = text.trim()
        if (t.isEmpty()) return MISSING
        val l = t.toLongOrNull()
        if (l != null) return l
        val d = t.toDoubleOrNull() ?: return MISSING
        if (d.isNaN() || d.isInfinite()) return MISSING
        if (d == Math.floor(d) && Math.abs(d) < 1e15) return d.toLong()
        return d
    }

    /** a cell value as the reader gives it (String / Long / Double / Boolean / null) -> JSON value or MISSING */
    fun cellToJson(cell: Any?): Any = when (cell) {
        null -> MISSING
        is String -> fromText(cell)
        is Number -> cell
        is Boolean -> cell
        else -> MISSING
    }

    /** text of a number for the <v> element */
    fun numText(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /** letters of a column: 0 -> A, 25 -> Z, 26 -> AA */
    fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (true) {
            sb.append('A' + n % 26)
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    /** index of the column in a cell reference like "AB12" (A = 0); -1 when there are no letters */
    fun colIndex(ref: String): Int {
        var n = 0
        var any = false
        for (ch in ref) {
            val u = ch.uppercaseChar()
            if (u in 'A'..'Z') {
                n = n * 26 + (u - 'A' + 1)
                any = true
            } else break
        }
        return if (any) n - 1 else -1
    }

    private fun isHex(c: Char) = (c in '0'..'9') || (c in 'A'..'F') || (c in 'a'..'f')

    private fun isEscAt(s: String, i: Int): Boolean =
        i + 6 < s.length && s[i] == '_' && s[i + 1] == 'x' && isHex(s[i + 2]) && isHex(s[i + 3]) && isHex(s[i + 4]) && isHex(s[i + 5]) && s[i + 6] == '_'

    private fun hex(code: Int): String {
        val h = Integer.toHexString(code).uppercase()
        return "_x" + "0000".substring(h.length) + h + "_"
    }

    /** writes text into an XML element: & < > are escaped, characters XML cannot hold become _xHHHH_ */
    fun writeEscaped(w: Writer, s: String) {
        val n = s.length
        var start = 0
        var i = 0
        while (i < n) {
            val c = s[i]
            var rep: String? = null
            if (c == '&') rep = "&amp;"
            else if (c == '<') rep = "&lt;"
            else if (c == '>') rep = "&gt;"
            else if (c == '\t' || c == '\n') rep = null
            else if (c.code < 0x20 || c == '\uFFFE' || c == '\uFFFF') rep = hex(c.code)
            else if (Character.isHighSurrogate(c)) {
                if (i + 1 < n && Character.isLowSurrogate(s[i + 1])) i++ else rep = hex(c.code)
            } else if (Character.isLowSurrogate(c)) rep = hex(c.code)
            else if (c == '_' && isEscAt(s, i)) rep = "_x005F_"
            if (rep != null) {
                if (i > start) w.write(s, start, i - start)
                w.write(rep)
                start = i + 1
            }
            i++
        }
        if (start < n) w.write(s, start, n - start)
    }

    /** the opposite of writeEscaped for text read from a sheet: _xHHHH_ becomes the character */
    fun unescape(s: String): String {
        if (s.indexOf("_x") < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (isEscAt(s, i)) {
                sb.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                i += 7
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}
