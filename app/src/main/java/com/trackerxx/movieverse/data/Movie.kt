package com.trackerxx.movieverse.data

import org.json.JSONArray
import org.json.JSONObject

/** JS String(number): 85.0 -> "85", 7.5 -> "7.5" */
fun jsNumToString(d: Double): String {
    if (!d.isNaN() && !d.isInfinite() && d == Math.floor(d) && Math.abs(d) < 1e15) return d.toLong().toString()
    return d.toString()
}

/**
 * One movie / TV show of the library.
 *
 * The web version keeps every movie as a free-form JS object (fields appear when a source answers, `undefined` means
 * "not looked up yet"). To keep the logic and the backup file 100% identical, a Movie wraps the same JSON object:
 *  - a field that is missing  = JS `undefined`   ->  has(key) == false
 *  - a field that is JSON null = JS `null`       ->  has(key) == true, isNull(key) == true
 *  - unknown fields from old backups are kept and written back unchanged.
 */
class Movie(val j: JSONObject = JSONObject()) {

    /** _ld : this movie is being refreshed right now (info tiles show a shimmer). Never saved. */
    @Volatile
    var ld: Boolean = false

    constructor(json: String) : this(JSONObject(json))

    // ---- generic access -------------------------------------------------
    fun has(key: String): Boolean = j.has(key)

    /** JS truthiness of a field (missing, null, 0, "" and false are falsy) */
    fun truthy(key: String): Boolean = com.trackerxx.movieverse.util.Js.truthy(j.opt(key))
    fun isNull(key: String): Boolean = !j.has(key) || j.isNull(key)

    /** String value, or null when the field is missing / null. Numbers and booleans are converted like JS String(x). */
    fun str(key: String): String? {
        if (isNull(key)) return null
        return when (val v = j.get(key)) {
            is String -> v
            is Number -> jsNumToString(v.toDouble())
            is Boolean -> v.toString()
            else -> v.toString()
        }
    }

    /** Number value, or null. Numeric strings are converted like JS +x. */
    fun num(key: String): Double? {
        if (isNull(key)) return null
        return when (val v = j.get(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            is Boolean -> if (v) 1.0 else 0.0
            else -> null
        }
    }

    fun long(key: String): Long? = num(key)?.takeIf { !it.isNaN() && !it.isInfinite() }?.toLong()
    fun int(key: String): Int? = long(key)?.toInt()

    fun bool(key: String): Boolean? {
        if (isNull(key)) return null
        return when (val v = j.get(key)) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotEmpty()
            else -> true
        }
    }

    fun strList(key: String): List<String>? {
        val a = j.optJSONArray(key) ?: return null
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            if (!a.isNull(i)) out.add(a.get(i).toString())
        }
        return out
    }

    fun obj(key: String): JSONObject? = j.optJSONObject(key)
    fun arr(key: String): JSONArray? = j.optJSONArray(key)

    /**
     * Sets a field. null removes it (= JS `undefined`, not written to the backup).
     * NaN / Infinity become JSON null, like JSON.stringify does.
     */
    fun set(key: String, value: Any?) {
        when (value) {
            null -> j.remove(key)
            is Double -> if (value.isNaN() || value.isInfinite()) setNull(key) else j.put(key, value)
            is Float -> if (value.isNaN() || value.isInfinite()) setNull(key) else j.put(key, value.toDouble())
            is List<*> -> j.put(key, JSONArray(value))
            else -> j.put(key, value)
        }
    }

    /** JS `m.x = null` */
    fun setNull(key: String) {
        j.put(key, JSONObject.NULL)
    }

    /** JS `delete m.x` / `m.x = undefined` */
    fun remove(key: String) {
        j.remove(key)
    }

    /** Deep copy (JSON.parse(JSON.stringify(m))). */
    fun copy(): Movie = Movie(JSONObject(j.toString()))

    /** JSON.stringify(m) */
    override fun toString(): String = j.toString()

    // ---- the fields every screen needs ---------------------------------
    var id: String
        get() = str("id") ?: ""
        set(v) { j.put("id", v) }

    /** TMDB id. TV shows are stored with a NEGATIVE id (same as the web version). */
    var tmdbId: Long
        get() = long("tmdbId") ?: 0L
        set(v) { j.put("tmdbId", v) }

    var title: String
        get() = str("title") ?: ""
        set(v) { j.put("title", v) }

    var year: String
        get() = str("year") ?: ""
        set(v) { j.put("year", v) }

    /** status id: watching / plan / watched / upcoming / favourite / s<uid> */
    var status: String
        get() = str("status") ?: ""
        set(v) { j.put("status", v) }

    /** folder id, or null when the movie is not in a folder */
    var folder: String?
        get() = str("folder")
        set(v) { if (v == null) setNull("folder") else j.put("folder", v) }

    /** time the movie was added (Date.now()) */
    var added: Long
        get() = long("added") ?: 0L
        set(v) { j.put("added", v) }

    var poster: String?
        get() = str("poster")
        set(v) { if (v == null) setNull("poster") else j.put("poster", v) }

    /** release date "YYYY-MM-DD" (or "") */
    var rel: String
        get() = str("rel") ?: ""
        set(v) { j.put("rel", v) }
}
