package com.trackerxx.movieverse.data

import org.json.JSONArray
import org.json.JSONObject

/** Folder inside a status tab: {id, name, status, color}. Extra fields from old backups are kept. */
class Folder(val j: JSONObject) {
    val id: String get() = j.optString("id", "")
    var name: String
        get() = j.optString("name", "")
        set(v) { j.put("name", v) }
    /** id of the status (tab) the folder lives in */
    var status: String
        get() = j.optString("status", "")
        set(v) { j.put("status", v) }
    var color: String?
        get() = if (j.isNull("color")) null else j.optString("color", "")
        set(v) { if (v == null) j.put("color", JSONObject.NULL) else j.put("color", v) }

    companion object {
        fun create(id: String, name: String, status: String, color: String?): Folder {
            val o = JSONObject()
            o.put("id", id)
            o.put("name", name)
            o.put("status", status)
            if (color == null) o.put("color", JSONObject.NULL) else o.put("color", color)
            return Folder(o)
        }
    }
}

/** Status tab: {id, name}. */
class Status(val j: JSONObject) {
    val id: String get() = j.optString("id", "")
    var name: String
        get() = j.optString("name", "")
        set(v) { j.put("name", v) }

    companion object {
        fun create(id: String, name: String): Status {
            val o = JSONObject()
            o.put("id", id)
            o.put("name", name)
            return Status(o)
        }
    }
}

fun jsonArrayOf(items: List<JSONObject>): JSONArray {
    val a = JSONArray()
    for (o in items) a.put(o)
    return a
}
