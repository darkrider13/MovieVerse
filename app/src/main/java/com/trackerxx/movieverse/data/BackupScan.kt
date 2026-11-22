package com.trackerxx.movieverse.data

import org.json.JSONTokener

/**
 * `mkScan(onMovie, onExtra)` of the web version: reads a backup file piece by piece (feed) and hands out
 *  - every movie object of the "movies" array (root array, root.movies or root.data.movies) as JSON text (onMovie),
 *  - the root-level values other than "movies" / "data" (folders, statuses, settings) as JSON text (onExtra).
 * So a 100 MB+ backup never has to be in memory as one string.
 */
class BackupScan(
    private val onMovie: (String) -> Unit,
    private val onExtra: (String, String) -> Unit
) {
    private class Ctx(val t: Int, var key: String?, var ek: Boolean, var mv: Boolean)

    private val st = ArrayList<Ctx>()
    private var inStr = false
    private var esc = false
    private var isKey = false
    private val kb = StringBuilder()
    private var cap: StringBuilder? = null
    private var cs = -1
    private var capD = 0
    private var capKind = 0
    private var capKey = ""
    private var foundMovies = false

    /** true as soon as a movies array was seen */
    fun found(): Boolean = foundMovies

    private fun top(): Ctx? = if (st.isEmpty()) null else st[st.size - 1]

    fun feed(s: String) {
        val n = s.length
        var i = 0
        cs = if (cap != null) 0 else -1
        while (i < n) {
            val ch = s[i]
            val c = ch.code
            if (inStr) {
                if (esc) {
                    esc = false
                    if (isKey) kb.append(ch)
                    i++; continue
                }
                if (c == 92) {
                    esc = true
                    if (isKey) kb.append('\\')
                    i++; continue
                }
                if (c == 34) {
                    inStr = false
                    if (isKey) {
                        val tp = top()
                        if (tp != null) {
                            tp.key = try {
                                JSONTokener("\"" + kb.toString() + "\"").nextValue() as? String ?: kb.toString()
                            } catch (e: Exception) {
                                kb.toString()
                            }
                            tp.ek = false
                        }
                    }
                    i++; continue
                }
                if (isKey) kb.append(ch)
                i++; continue
            }
            if (c == 34) {
                inStr = true
                val tp = top()
                isKey = tp != null && tp.t == 1 && tp.ek
                kb.setLength(0)
                i++; continue
            }
            if (c == 123 || c == 91) {
                val tp = top()
                val ctx = Ctx(if (c == 123) 1 else 2, null, c == 123, false)
                if (c == 91) {
                    if (tp == null) ctx.mv = true
                    else if (tp.t == 1 && tp.key == "movies" && st.size == 1) ctx.mv = true
                    else if (tp.t == 1 && tp.key == "movies" && st.size == 2 && st[0].key == "data") ctx.mv = true
                    if (ctx.mv) foundMovies = true
                }
                if (cap == null) {
                    if (c == 123 && tp != null && tp.mv) {
                        cap = StringBuilder(); cs = i; capKind = 1; capD = st.size + 1
                    } else if (tp != null && st.size == 1 && tp.t == 1 && !tp.key.isNullOrEmpty() && tp.key != "movies" && tp.key != "data") {
                        cap = StringBuilder(); cs = i; capKind = 2; capD = st.size + 1; capKey = tp.key!!
                    }
                }
                st.add(ctx)
                i++; continue
            }
            if (c == 125 || c == 93) {
                if (st.isNotEmpty()) st.removeAt(st.size - 1)
                val cp = cap
                if (cp != null && st.size == capD - 1) {
                    cp.append(s, if (cs < 0) 0 else cs, i + 1)
                    val t = cp.toString()
                    cap = null
                    cs = -1
                    if (capKind == 1) onMovie(t) else onExtra(capKey, t)
                }
                i++; continue
            }
            if (c == 44) {
                val tp = top()
                if (tp != null && tp.t == 1) tp.ek = true
            }
            i++
        }
        val cp = cap
        if (cp != null) cp.append(s, if (cs < 0) 0 else cs, n)
    }
}
