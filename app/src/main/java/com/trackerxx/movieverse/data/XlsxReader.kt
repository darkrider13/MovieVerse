package com.trackerxx.movieverse.data

import android.util.Xml
import com.trackerxx.movieverse.ui.library.LibraryLogic
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Reads a backup that XlsxBackup wrote (also after it was opened and saved again by Excel, LibreOffice or Google Sheets).
 *  - the sheets are read with a pull parser, one row at a time (never the whole sheet in memory),
 *  - the shared strings table that Excel writes is kept in a temporary file, not in memory,
 *  - every cell goes through XlsxCodec, so the types come back exactly as they were exported.
 * The zip must be a file (the picked file is copied to the cache folder first).
 */
class XlsxReader(file: File, private val tmpDir: File) : Closeable {
    private val zip = ZipFile(file)
    private val sheetPath = HashMap<String, String>()   // lower-case sheet name -> zip entry
    private var sstPath = "xl/sharedStrings.xml"
    private var sst: Sst? = null

    init {
        try {
            readWorkbook()
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    override fun close() {
        try { sst?.close() } catch (e: Exception) { }
        try { zip.close() } catch (e: Exception) { }
    }

    fun hasMovies(): Boolean = sheetPath.containsKey("movies") && zip.getEntry(sheetPath["movies"]) != null

    // ---- workbook: sheet names -> files ----------------------------------------------------------------------------------------

    private fun local(n: String?): String {
        if (n == null) return ""
        val i = n.lastIndexOf(':')
        return if (i >= 0) n.substring(i + 1) else n
    }

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (local(p.getAttributeName(i)) == name) return p.getAttributeValue(i)
        }
        return null
    }

    private fun target(t: String): String = if (t.startsWith("/")) t.substring(1) else "xl/$t"

    private fun readWorkbook() {
        val sheets = ArrayList<Pair<String, String>>()     // name, r:id
        zip.getEntry("xl/workbook.xml")?.let { e ->
            val p = Xml.newPullParser()
            p.setInput(BufferedInputStream(zip.getInputStream(e), 1 shl 14), null)
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT) break
                if (ev == XmlPullParser.START_TAG && local(p.name) == "sheet") {
                    val nm = attr(p, "name") ?: continue
                    var rid = ""
                    for (i in 0 until p.attributeCount) {
                        // r:id (the prefix can have another name; sheetId is a different attribute)
                        val an = p.getAttributeName(i)
                        if (an.indexOf(':') >= 0 && local(an) == "id") rid = p.getAttributeValue(i)
                    }
                    sheets.add(nm to rid)
                }
            }
        }
        val rels = HashMap<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            val p = Xml.newPullParser()
            p.setInput(BufferedInputStream(zip.getInputStream(e), 1 shl 14), null)
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT) break
                if (ev == XmlPullParser.START_TAG && local(p.name) == "Relationship") {
                    val id = attr(p, "Id") ?: continue
                    val tg = attr(p, "Target") ?: continue
                    rels[id] = target(tg)
                    if ((attr(p, "Type") ?: "").endsWith("/sharedStrings")) sstPath = target(tg)
                }
            }
        }
        for ((i, s) in sheets.withIndex()) {
            val path = rels[s.second] ?: ("xl/worksheets/sheet" + (i + 1) + ".xml")
            sheetPath[s.first.trim().lowercase()] = path
        }
    }

    // ---- shared strings: kept in a temp file ---------------------------------------------------------------------------------

    private class Sst(private val f: File) : Closeable {
        private var offs = LongArray(4096)
        private var lens = IntArray(4096)
        var n = 0
            private set
        private var raf: RandomAccessFile? = null
        private val cache = object : LinkedHashMap<Int, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > 2048
        }

        fun build(input: InputStream) {
            var pos = 0L
            val out = BufferedOutputStream(FileOutputStream(f), 1 shl 16)
            try {
                val p = Xml.newPullParser()
                p.setInput(BufferedInputStream(input, 1 shl 16), null)
                var inSi = false
                var ph = 0
                val sb = StringBuilder()
                while (true) {
                    val ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.START_TAG) {
                        val nm = p.name.substringAfterLast(':')
                        if (nm == "si") {
                            inSi = true
                            sb.setLength(0)
                        } else if (nm == "rPh") ph++
                        else if (nm == "t" && inSi && ph == 0) sb.append(p.nextText())
                    } else if (ev == XmlPullParser.END_TAG) {
                        val nm = p.name.substringAfterLast(':')
                        if (nm == "rPh") ph--
                        else if (nm == "si") {
                            val b = XlsxCodec.unescape(sb.toString()).toByteArray(Charsets.UTF_8)
                            if (n == offs.size) {
                                offs = offs.copyOf(n * 2)
                                lens = lens.copyOf(n * 2)
                            }
                            offs[n] = pos
                            lens[n] = b.size
                            n++
                            out.write(b)
                            pos += b.size
                            inSi = false
                        }
                    }
                }
            } finally {
                out.close()
                try { input.close() } catch (e: Exception) { }
            }
        }

        fun get(i: Int): String? {
            if (i < 0 || i >= n) return null
            cache[i]?.let { return it }
            val r = raf ?: RandomAccessFile(f, "r").also { raf = it }
            val b = ByteArray(lens[i])
            if (b.isNotEmpty()) {
                r.seek(offs[i])
                r.readFully(b)
            }
            val s = String(b, Charsets.UTF_8)
            cache[i] = s
            return s
        }

        override fun close() {
            try { raf?.close() } catch (e: Exception) { }
            f.delete()
        }
    }

    private fun shared(i: Int): String? {
        var s = sst
        if (s == null) {
            val f = File.createTempFile("mv-sst", ".bin", tmpDir)
            s = Sst(f)
            sst = s
            val e = zip.getEntry(sstPath)
            if (e != null) s.build(zip.getInputStream(e))
        }
        return s.get(i)
    }

    // ---- rows of a sheet ------------------------------------------------------------------------------------------------------

    private class CountIn(i: InputStream) : FilterInputStream(i) {
        var bytes = 0L
        override fun read(): Int {
            val r = super.read()
            if (r >= 0) bytes++
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = super.read(b, off, len)
            if (r > 0) bytes += r.toLong()
            return r
        }
    }

    /** the rows of one sheet, one at a time; a row is a list of cell values (String / Long / Double / Boolean / null) */
    inner class Rows(entry: ZipEntry) {
        private val cin = CountIn(zip.getInputStream(entry))
        private val p: XmlPullParser = Xml.newPullParser()

        /** size of the sheet file (unpacked) and how much of it was read so far: for the percentage */
        val total: Long = if (entry.size > 0) entry.size else 1L
        val bytes: Long get() = cin.bytes

        init {
            p.setInput(BufferedInputStream(cin, 1 shl 16), null)
        }

        fun next(): ArrayList<Any?>? {
            var row: ArrayList<Any?>? = null
            var col = -1
            var type = ""
            var v: String? = null
            var inl: StringBuilder? = null
            var inCell = false
            var ph = 0
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT) return null
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name.substringAfterLast(':')) {
                        "row" -> {
                            row = ArrayList()
                            col = -1
                        }
                        "c" -> {
                            inCell = true
                            type = attr(p, "t") ?: ""
                            val ref = attr(p, "r")
                            val ci = if (ref != null) XlsxCodec.colIndex(ref) else -1
                            col = if (ci >= 0) ci else col + 1
                            v = null
                            inl = null
                        }
                        "v" -> if (inCell) v = p.nextText()
                        "is" -> if (inCell) inl = StringBuilder()
                        "rPh" -> ph++
                        "t" -> if (inCell && inl != null && ph == 0) inl.append(p.nextText())
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (p.name.substringAfterLast(':')) {
                        "rPh" -> ph--
                        "c" -> {
                            inCell = false
                            val r = row
                            val x = cellValue(type, v, inl)
                            if (r != null && x != null && col >= 0) {
                                while (r.size <= col) r.add(null)
                                r[col] = x
                            }
                        }
                        "row" -> if (row != null) return row
                    }
                }
            }
        }
    }

    private fun cellValue(type: String, v: String?, inl: StringBuilder?): Any? {
        when (type) {
            "s" -> {
                val i = v?.trim()?.toIntOrNull() ?: return null
                val s = shared(i) ?: return null
                return if (s.isEmpty()) null else s
            }
            "inlineStr" -> {
                val s = XlsxCodec.unescape((inl ?: StringBuilder(v ?: "")).toString())
                return if (s.isEmpty()) null else s
            }
            "str", "d" -> {
                val s = XlsxCodec.unescape(v ?: return null)
                return if (s.isEmpty()) null else s
            }
            "b" -> {
                val t = v?.trim() ?: return null
                return t == "1" || t.equals("true", true)
            }
            "e" -> return null
            else -> {
                if (v == null) {
                    val s = inl?.toString() ?: return null
                    return if (s.isEmpty()) null else XlsxCodec.unescape(s)
                }
                val n = XlsxCodec.numValue(v)
                return if (n === XlsxCodec.MISSING) null else n
            }
        }
    }

    private fun rowsOf(sheet: String): Rows? {
        val path = sheetPath[sheet] ?: return null
        val e = zip.getEntry(path) ?: return null
        return Rows(e)
    }

    // ---- Movies -------------------------------------------------------------------------------------------------------------------

    fun movieRows(): MovieRows = MovieRows(rowsOf("movies") ?: throw IllegalStateException("no Movies sheet"))

    /**
     * The movies one at a time as the JSON objects they had in the app.
     * Columns: Title / Year / Status = the fields title / year / status, "My Rating" = the score the user can change in Excel
     * (the field `score` is only set when the number in the cell is different from the movie's own score, LibraryLogic.scoreOf),
     * "IMDb Rating" = for reading only (not imported), "<field>~2" = the next piece of a long text, every other column = the field
     * with that name.
     */
    class MovieRows internal constructor(private val rows: Rows) {
        private val FIELD = 1
        private val MYR = 2
        private val CONT = 3

        private var kind = IntArray(0)
        private var key = arrayOfNulls<String>(0)
        private var base = IntArray(0)
        private var contOf = HashMap<Int, ArrayList<Int>>()

        val total: Long get() = rows.total
        val bytes: Long get() = rows.bytes

        init {
            var head: ArrayList<Any?>? = null
            while (head == null) {
                val r = rows.next() ?: break
                if (r.any { it != null }) head = r
            }
            if (head != null) setHeader(head)
        }

        private fun setHeader(h: ArrayList<Any?>) {
            kind = IntArray(h.size)
            key = arrayOfNulls(h.size)
            base = IntArray(h.size)
            val colOf = HashMap<String, Int>()
            for (c in h.indices) {
                val raw = h[c]
                val t = (if (raw is String) raw else null)?.trim()
                if (t.isNullOrEmpty()) continue
                when {
                    t == "My Rating" -> kind[c] = MYR
                    t == "IMDb Rating" -> { }
                    else -> {
                        val k = when (t) {
                            "Title" -> "title"
                            "Year" -> "year"
                            "Status" -> "status"
                            else -> t
                        }
                        val tl = t.lastIndexOf('~')
                        val n = if (tl > 0) t.substring(tl + 1).toIntOrNull() else null
                        val b = if (n != null && n >= 2) colOf[t.substring(0, tl)] else null
                        if (b != null) {
                            kind[c] = CONT
                            base[c] = b
                            contOf.getOrPut(b) { ArrayList() }.add(c)
                        } else if (!colOf.containsKey(k)) {
                            kind[c] = FIELD
                            key[c] = k
                            colOf[k] = c
                        }
                    }
                }
            }
        }

        /** the next movie row as a JSON object (rows without any value are skipped); null = end of the sheet */
        fun next(): JSONObject? {
            while (true) {
                val r = rows.next() ?: return null
                if (r.any { it != null }) return build(r)
            }
        }

        private fun build(r: ArrayList<Any?>): JSONObject {
            val o = JSONObject()
            var myCol = -1
            for (c in kind.indices) {
                if (kind[c] == MYR) myCol = c
                if (kind[c] != FIELD) continue
                var cell: Any? = if (c < r.size) r[c] else null
                val cont = contOf[c]
                if (cont != null && cell is String) {
                    val sb = StringBuilder(cell)
                    for (cc in cont) {
                        val x = if (cc < r.size) r[cc] else null
                        if (x is String) sb.append(x)
                    }
                    cell = sb.toString()
                }
                val v = XlsxCodec.cellToJson(cell)
                if (v !== XlsxCodec.MISSING) o.put(key[c]!!, v)
            }
            if (myCol >= 0) applyMyRating(o, if (myCol < r.size) r[myCol] else null)
            return o
        }

        /** "My Rating" changed in Excel -> the field `score` gets the new number */
        private fun applyMyRating(o: JSONObject, cell: Any?) {
            val cur = LibraryLogic.scoreOf(Movie(o))
            val nv: Double = when (cell) {
                null -> 0.0
                is Number -> cell.toDouble()
                is String -> cell.trim().replace(',', '.').toDoubleOrNull() ?: return
                else -> return
            }
            if (nv.isNaN() || nv.isInfinite()) return
            if (Math.abs(nv - cur) <= 1e-9) return
            if (nv == Math.floor(nv) && Math.abs(nv) < 1e15) o.put("score", nv.toLong()) else o.put("score", nv)
        }
    }

    // ---- Folders / Statuses / Settings -------------------------------------------------------------------------------------

    /** the folders / statuses sheet as the JSON array text of the backup; null when the sheet does not exist */
    fun recordsJson(sheet: String): String? {
        val rows = rowsOf(sheet) ?: return null
        val out = JSONArray()
        var found: ArrayList<Any?>? = null
        while (found == null) {
            val r = rows.next() ?: return out.toString()
            if (r.any { it != null }) found = r
        }
        val head: ArrayList<Any?> = found ?: return out.toString()
        val names = arrayOfNulls<String>(head.size)
        val colOf = HashMap<String, Int>()
        val conts = HashMap<Int, ArrayList<Int>>()
        for (c in head.indices) {
            val t = ((head[c] as? String) ?: "").trim()
            if (t.isEmpty()) continue
            val tl = t.lastIndexOf('~')
            val n = if (tl > 0) t.substring(tl + 1).toIntOrNull() else null
            val b = if (n != null && n >= 2) colOf[t.substring(0, tl)] else null
            if (b != null) {
                conts.getOrPut(b) { ArrayList() }.add(c)
            } else if (!colOf.containsKey(t)) {
                names[c] = t
                colOf[t] = c
            }
        }
        while (true) {
            val r = rows.next() ?: break
            if (r.none { it != null }) continue
            val o = JSONObject()
            for (c in names.indices) {
                val k = names[c] ?: continue
                var cell: Any? = if (c < r.size) r[c] else null
                val cont = conts[c]
                if (cont != null && cell is String) {
                    val sb = StringBuilder(cell)
                    for (cc in cont) {
                        val x = if (cc < r.size) r[cc] else null
                        if (x is String) sb.append(x)
                    }
                    cell = sb.toString()
                }
                val v = XlsxCodec.cellToJson(cell)
                if (v !== XlsxCodec.MISSING) o.put(k, v)
            }
            out.put(o)
        }
        return out.toString()
    }

    /** the Settings sheet as the "settings" object of the backup ({name, theme, apis:[{id,name,keys,custom?}]}); null when there is no such sheet */
    fun settingsJson(): String? {
        val rows = rowsOf("settings") ?: return null
        val o = JSONObject()
        val apis = JSONArray()
        var first = true
        while (true) {
            val r = rows.next() ?: break
            if (r.none { it != null }) continue
            if (first) {   // the header row
                first = false
                continue
            }
            val what = XlsxCodec.plain(r.getOrNull(0))?.trim()?.lowercase() ?: continue
            when (what) {
                "name" -> XlsxCodec.plain(r.getOrNull(1))?.let { o.put("name", it) }
                "theme" -> XlsxCodec.plain(r.getOrNull(1))?.let { o.put("theme", it) }
                "api" -> {
                    val id = XlsxCodec.plain(r.getOrNull(1)) ?: continue
                    val a = JSONObject()
                    a.put("id", id)
                    a.put("name", XlsxCodec.plain(r.getOrNull(2)) ?: id)
                    val ks = JSONArray()
                    for (i in 4 until r.size) {
                        val k = XlsxCodec.plain(r[i])
                        if (!k.isNullOrEmpty()) ks.put(k)
                    }
                    a.put("keys", ks)
                    val cu = r.getOrNull(3)
                    val custom = when (cu) {
                        is Number -> cu.toDouble() != 0.0
                        is Boolean -> cu
                        is String -> cu.isNotEmpty() && cu != "0" && !cu.equals("false", true)
                        else -> false
                    }
                    if (custom) a.put("custom", 1)
                    apis.put(a)
                }
            }
        }
        o.put("apis", apis)
        return o.toString()
    }
}
