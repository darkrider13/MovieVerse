package com.trackerxx.movieverse.data

import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.ui.library.LibraryLogic
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The backup as an Excel file (.xlsx), written without any library: an .xlsx is a zip of small XML files.
 *
 * Sheets:
 *  - One sheet per status (tab), in this order: Watched, Coming Soon, Plan to watch, then the other statuses (a sheet is named like its
 *               status). Movies whose status no longer exists go to an "Other" sheet. Each sheet has one row per movie. Columns A-E are for reading: Title, Year, Status, My Rating (= LibraryLogic.scoreOf, the movie's own
 *               score), IMDb Rating (= Enrich.imdbRating, IMDb only). Title / Year / Status are the real fields. After them every other
 *               field of the movies has its own column (named like the field); objects and arrays are JSON text in one cell.
 *  - Folders, Statuses : one row each, one column per field.
 *  - Settings : name, theme and the API keys.
 * See XlsxCodec for how one value is put in a cell. The file is streamed through a ZipOutputStream, a movie at a time, so a library of
 * 10000+ movies never has to be in memory as one text.
 */
object XlsxBackup {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    /** one column of fields: the field name and how many cells its longest text needs */
    private class Col(val key: String, var chunks: Int)

    private val LEAD = arrayOf("Title", "Year", "Status", "My Rating", "IMDb Rating")
    private val LEAD_KEYS = setOf("title", "year", "status")

    /** writes the whole library; onProgress gets the number of movies written so far. Does not close out. */
    fun write(out: OutputStream, onProgress: ((Int) -> Unit)? = null) {
        val movies = AppData.movies.toTypedArray()
        val folders = AppData.folders.map { it.j }
        val statuses = AppData.statuses.map { it.j }

        // one sheet per status: Watched, Coming Soon, Plan to watch first, then the others in their own order
        val sts = AppData.statuses.toList()
        val first = listOf("watched", "upcoming", "plan").mapNotNull { id -> sts.firstOrNull { it.id == id } }
        val ordered = first + sts.filter { x -> first.none { it.id == x.id } }
        val used = HashSet<String>()
        for (n in FIXED) used.add(n.lowercase())
        val groups = ArrayList<Pair<String, List<Movie>>>()
        for (st in ordered) {
            groups.add(cleanName(st.name, used) to movies.filter { it.status == st.id })
        }
        val known = HashSet<String>()
        for (st in sts) known.add(st.id)
        val orphans = movies.filter { !known.contains(it.status) }
        if (orphans.isNotEmpty()) groups.add(cleanName("Other", used) to orphans)
        val names = ArrayList<String>()
        for (g in groups) names.add(g.first)
        for (n in FIXED) names.add(n)

        val zip = ZipOutputStream(BufferedOutputStream(out, 1 shl 16))
        zip.setLevel(Deflater.BEST_SPEED)
        val w = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8), 1 shl 16)

        fun part(name: String, body: () -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            body()
            w.flush()
            zip.closeEntry()
        }

        part("[Content_Types].xml") { w.write(contentTypes(names.size)) }
        part("_rels/.rels") { w.write(rootRels()) }
        part("xl/workbook.xml") { w.write(workbook(names)) }
        part("xl/_rels/workbook.xml.rels") { w.write(workbookRels(names.size)) }
        part("xl/styles.xml") { w.write(styles()) }
        var done = 0
        for ((i, g) in groups.withIndex()) {
            val base = done
            part("xl/worksheets/sheet" + (i + 1) + ".xml") { writeMovies(w, g.second, onProgress, base) }
            done += g.second.size
        }
        val n = groups.size
        part("xl/worksheets/sheet" + (n + 1) + ".xml") { writeRecords(w, folders, listOf("id", "name", "status", "color")) }
        part("xl/worksheets/sheet" + (n + 2) + ".xml") { writeRecords(w, statuses, listOf("id", "name")) }
        part("xl/worksheets/sheet" + (n + 3) + ".xml") { writeSettings(w) }

        zip.finish()
        zip.flush()
    }

    /** an Excel sheet name: at most 31 characters, none of [ ] : * ? / \, not used twice (any case), not one of the fixed sheets */
    private fun cleanName(raw: String, used: HashSet<String>): String {
        var s = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().trim('\'').trim()
        if (s.isEmpty()) s = "Movies"
        s = s.take(31).trim()
        var cand = s
        var k = 2
        while (!used.add(cand.lowercase())) {
            val suf = " ($k)"
            cand = s.take(31 - suf.length).trim() + suf
            k++
        }
        return cand
    }

    private fun xml(t: String): String = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---- the fixed parts ------------------------------------------------------------------------------------------------------

    /** the sheets after the movie sheets */
    private val FIXED = arrayOf("Folders", "Statuses", "Settings")

    private fun contentTypes(count: Int): String {
        val sb = StringBuilder(DECL)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 0 until count) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sb.append("</Types>")
        return sb.toString()
    }

    private fun rootRels(): String =
        DECL + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    private fun workbook(names: List<String>): String {
        val sb = StringBuilder(DECL)
        sb.append("<workbook xmlns=\"$NS\" xmlns:r=\"$REL\"><sheets>")
        for (i in names.indices) {
            sb.append("<sheet name=\"").append(xml(names[i])).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(count: Int): String {
        val sb = StringBuilder(DECL)
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 0 until count) {
            sb.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"$REL/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>")
        }
        sb.append("<Relationship Id=\"rId").append(count + 1).append("\" Type=\"$REL/styles\" Target=\"styles.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    /** style 0 = normal, style 1 = bold (the header row) */
    private fun styles(): String =
        DECL + "<styleSheet xmlns=\"$NS\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"

    /** start of a sheet: frozen header row, column widths (width of the first columns, 0 = default) */
    private fun sheetStart(w: Writer, widths: IntArray) {
        w.write(DECL)
        w.write("<worksheet xmlns=\"$NS\"><sheetViews><sheetView workbookViewId=\"0\">")
        w.write("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\"/></sheetView></sheetViews>")
        w.write("<sheetFormatPr defaultRowHeight=\"15\"/>")
        if (widths.isNotEmpty()) {
            w.write("<cols>")
            for (i in widths.indices) {
                w.write("<col min=\"" + (i + 1) + "\" max=\"" + (i + 1) + "\" width=\"" + widths[i] + "\" customWidth=\"1\"/>")
            }
            w.write("</cols>")
        }
        w.write("<sheetData>")
    }

    private fun sheetEnd(w: Writer) {
        w.write("</sheetData></worksheet>")
    }

    // ---- cells ------------------------------------------------------------------------------------------------------------------

    private val NAMES = ArrayList<String>()

    private fun cn(c: Int): String {
        while (NAMES.size <= c) NAMES.add(XlsxCodec.colName(NAMES.size))
        return NAMES[c]
    }

    private fun open(w: Writer, c: Int, r: Int, type: String?, style: Int) {
        w.write("<c r=\"")
        w.write(cn(c))
        w.write(r.toString())
        w.write("\"")
        if (style != 0) w.write(" s=\"$style\"")
        if (type != null) w.write(" t=\"$type\"")
        w.write(">")
    }

    private fun textCell(w: Writer, c: Int, r: Int, text: String, style: Int = 0) {
        open(w, c, r, "inlineStr", style)
        w.write("<is><t xml:space=\"preserve\">")
        XlsxCodec.writeEscaped(w, text)
        w.write("</t></is></c>")
    }

    private fun numCell(w: Writer, c: Int, r: Int, text: String) {
        open(w, c, r, null, 0)
        w.write("<v>")
        w.write(text)
        w.write("</v></c>")
    }

    private fun boolCell(w: Writer, c: Int, r: Int, b: Boolean) {
        open(w, c, r, "b", 0)
        w.write(if (b) "<v>1</v></c>" else "<v>0</v></c>")
    }

    /** a JSON value in one cell (null = the field does not exist = no cell) */
    private fun valueCell(w: Writer, c: Int, r: Int, v: Any?) {
        if (v == null) return
        when {
            v === JSONObject.NULL -> textCell(w, c, r, "null")
            v is String -> textCell(w, c, r, XlsxCodec.strText(v))
            v is Boolean -> boolCell(w, c, r, v)
            v is Int || v is Long || v is Short || v is Byte -> numCell(w, c, r, v.toString())
            v is Double -> if (v.isNaN() || v.isInfinite()) textCell(w, c, r, "null") else numCell(w, c, r, XlsxCodec.numText(v))
            v is Float -> if (v.isNaN() || v.isInfinite()) textCell(w, c, r, "null") else numCell(w, c, r, XlsxCodec.numText(v.toDouble()))
            v is Number -> numCell(w, c, r, v.toString())
            v is JSONObject || v is JSONArray -> textCell(w, c, r, txt(v))
            else -> textCell(w, c, r, XlsxCodec.strText(v.toString()))
        }
    }

    /** a value that may need several cells (text longer than CHUNK): c is its first column */
    private fun keyedCells(w: Writer, c: Int, r: Int, v: Any?, chunks: Int) {
        if (v == null) return
        if (chunks <= 1 || !(v is String || v is JSONObject || v is JSONArray)) {
            valueCell(w, c, r, v)
            return
        }
        val text = if (v is String) XlsxCodec.strText(v) else txt(v)
        var pos = 0
        var k = 0
        while (pos < text.length && k < chunks) {
            var end = minOf(pos + XlsxCodec.CHUNK, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--   // never cut a surrogate pair in two
            textCell(w, c + k, r, text.substring(pos, end))
            pos = end
            k++
        }
    }

    // ---- reading the movies safely while the app may still change them -------------------------------------------------------

    /** toString() of a JSON object / array; tries again when the app changed it at that very moment */
    private fun txt(v: Any): String {
        var k = 0
        while (true) {
            try {
                return v.toString()
            } catch (e: java.util.ConcurrentModificationException) {
                if (++k >= 5) throw e
                Thread.sleep(5)
            }
        }
    }

    private fun keysOf(o: JSONObject): List<String> {
        var k = 0
        while (true) {
            try {
                val a = o.names() ?: return emptyList()
                val out = ArrayList<String>(a.length())
                for (i in 0 until a.length()) out.add(a.optString(i))
                return out
            } catch (e: java.util.ConcurrentModificationException) {
                if (++k >= 5) throw e
                Thread.sleep(5)
            }
        }
    }

    /** how many cells the text of this value needs */
    private fun chunksOf(v: Any?): Int {
        if (v == null) return 1
        val len: Int
        if (v is String) {
            if (v.length < XlsxCodec.CHUNK) return 1
            len = v.length + 1
        } else if (v is JSONObject || v is JSONArray) {
            len = txt(v).length
        } else return 1
        return (len + XlsxCodec.CHUNK - 1) / XlsxCodec.CHUNK
    }

    /** every field name that appears in any of the objects (first seen first) and the cells its longest text needs */
    private fun scan(items: List<JSONObject>, skip: Set<String>, first: List<String>): ArrayList<Col> {
        val map = LinkedHashMap<String, Col>()
        for (k in first) if (k !in skip) map[k] = Col(k, 1)
        for (o in items) {
            for (key in keysOf(o)) {
                if (key in skip) continue
                val n = chunksOf(o.opt(key))
                val ex = map[key]
                if (ex == null) map[key] = Col(key, n) else if (n > ex.chunks) ex.chunks = n
            }
        }
        return ArrayList(map.values)
    }

    private fun headerCells(w: Writer, r: Int, start: Int, cols: List<Col>): Int {
        var c = start
        for (col in cols) {
            textCell(w, c, r, col.key, 1)
            c++
            for (k in 2..col.chunks) {
                textCell(w, c, r, col.key + "~" + k, 1)
                c++
            }
        }
        return c
    }

    // ---- sheets ---------------------------------------------------------------------------------------------------------------------

    private fun writeMovies(w: Writer, movies: List<Movie>, onProgress: ((Int) -> Unit)?, done: Int) {
        val others = scan(movies.map { it.j }, LEAD_KEYS, listOf("id"))
        sheetStart(w, intArrayOf(32, 8, 14, 11, 13))
        w.write("<row r=\"1\">")
        for (i in LEAD.indices) textCell(w, i, 1, LEAD[i], 1)
        headerCells(w, 1, LEAD.size, others)
        w.write("</row>")

        for (k in movies.indices) {
            val mv = movies[k]
            val o = mv.j
            val r = k + 2
            w.write("<row r=\"$r\">")
            valueCell(w, 0, r, o.opt("title"))
            valueCell(w, 1, r, o.opt("year"))
            valueCell(w, 2, r, o.opt("status"))
            // My Rating: the movie's own score (score, or stars x 2); empty when there is none
            val sc = LibraryLogic.scoreOf(mv)
            if (sc != 0.0 && !sc.isNaN() && !sc.isInfinite()) numCell(w, 3, r, XlsxCodec.numText(sc))
            // IMDb Rating: only the IMDb rating (MDBList / XMDb / OMDb value of IMDb), never another source
            val im = Enrich.imdbRating(mv)?.trim()
            if (!im.isNullOrEmpty()) {
                val d = im.toDoubleOrNull()
                if (d != null && !d.isNaN() && !d.isInfinite()) numCell(w, 4, r, XlsxCodec.numText(d)) else textCell(w, 4, r, XlsxCodec.strText(im))
            }
            var c = LEAD.size
            for (col in others) {
                keyedCells(w, c, r, o.opt(col.key), col.chunks)
                c += col.chunks
            }
            w.write("</row>")
            if ((k + 1) % 500 == 0 && k + 1 < movies.size) onProgress?.invoke(done + k + 1)
        }
        sheetEnd(w)
        onProgress?.invoke(done + movies.size)
    }

    /** Folders / Statuses: one row per object, one column per field */
    private fun writeRecords(w: Writer, items: List<JSONObject>, defaults: List<String>) {
        val cols = scan(items, emptySet(), defaults)
        sheetStart(w, IntArray(0))
        w.write("<row r=\"1\">")
        headerCells(w, 1, 0, cols)
        w.write("</row>")
        for (k in items.indices) {
            val r = k + 2
            val o = items[k]
            w.write("<row r=\"$r\">")
            var c = 0
            for (col in cols) {
                keyedCells(w, c, r, o.opt(col.key), col.chunks)
                c += col.chunks
            }
            w.write("</row>")
        }
        sheetEnd(w)
    }

    /**
     * Settings: "name" and "theme" rows (value in column B) and one "api" row per API:
     * B = id, C = API name, D = 1 when it is a custom API, E.. = the keys.
     */
    private fun writeSettings(w: Writer) {
        val apis = AppData.apis.apis.toList()
        var maxKeys = 3
        for (a in apis) if (a.keys.size > maxKeys) maxKeys = a.keys.size
        sheetStart(w, intArrayOf(12, 36, 16, 8))
        w.write("<row r=\"1\">")
        val head = arrayOf("Setting", "Value", "API name", "Custom")
        for (i in head.indices) textCell(w, i, 1, head[i], 1)
        for (i in 1..maxKeys) textCell(w, 3 + i, 1, "Key $i", 1)
        w.write("</row>")
        var r = 2
        w.write("<row r=\"$r\">")
        textCell(w, 0, r, "name")
        textCell(w, 1, r, XlsxCodec.strText(AppData.uname))
        w.write("</row>")
        r++
        w.write("<row r=\"$r\">")
        textCell(w, 0, r, "theme")
        textCell(w, 1, r, XlsxCodec.strText(Ls.getItem("library.theme") ?: "dark"))
        w.write("</row>")
        for (a in apis) {
            r++
            w.write("<row r=\"$r\">")
            textCell(w, 0, r, "api")
            textCell(w, 1, r, XlsxCodec.strText(a.id))
            textCell(w, 2, r, XlsxCodec.strText(a.name))
            if (a.custom) numCell(w, 3, r, "1")
            for (i in a.keys.indices) textCell(w, 4 + i, r, XlsxCodec.strText(a.keys[i]))
            w.write("</row>")
        }
        sheetEnd(w)
    }
}
