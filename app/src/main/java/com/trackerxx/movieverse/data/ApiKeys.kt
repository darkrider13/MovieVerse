package com.trackerxx.movieverse.data

import org.json.JSONArray
import org.json.JSONObject

/** One API in Settings: {id, name, keys[], custom?}. tmdb / mdblist / xmdb / omdb are built in. */
class ApiEntry(
    var id: String,
    var name: String,
    var keys: MutableList<String> = ArrayList(),
    var custom: Boolean = false
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("keys", JSONArray(keys))
        if (custom) o.put("custom", 1)
        return o
    }

    /** JSON.parse(JSON.stringify(a)) */
    fun copy(): ApiEntry = ApiEntry(id, name, ArrayList(keys), custom)

    companion object {
        /** returns null for an element that is not a usable API object */
        fun fromJson(o: JSONObject): ApiEntry? {
            val id = o.opt("id")
            if (id == null || id == JSONObject.NULL || id.toString().isEmpty()) return null
            val ks = ArrayList<String>()
            val a = o.optJSONArray("keys")
            if (a != null) {
                for (i in 0 until a.length()) {
                    if (!a.isNull(i)) ks.add(a.get(i).toString())
                }
            }
            val nm = o.opt("name")
            val name = if (nm == null || nm == JSONObject.NULL || nm.toString().isEmpty()) id.toString() else nm.toString()
            val cu = o.opt("custom")
            val custom = cu != null && cu != JSONObject.NULL && cu != false && cu != 0 && cu.toString() != ""
            return ApiEntry(id.toString(), name, ks, custom)
        }
    }
}

/**
 * All API keys of the app (web version: apis, keys, omdbs, key, omdb, ki, oi, xi, pickKeys, apiKeys, normApis).
 * Stored in the same localStorage keys: library.apis / library.keys / library.omdbs / library.key / library.omdb.
 */
class ApiKeys {
    var apis: MutableList<ApiEntry> = ArrayList()
    var keys: MutableList<String> = ArrayList()    // TMDB keys
    var omdbs: MutableList<String> = ArrayList()   // OMDb keys

    /** key currently in use: TMDB / OMDb (rotates to the next key when a limit is hit) */
    var key: String = ""
    var omdb: String = ""

    /** rotation positions: TMDB, OMDb, XMDb */
    var ki = 0
    var oi = 0
    var xi = 0

    init {
        load()
    }

    private fun readStringList(raw: String?): MutableList<String>? {
        if (raw == null) return null
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<String>()
            for (i in 0 until a.length()) {
                if (!a.isNull(i)) out.add(a.get(i).toString())
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun load() {
        key = Ls.getItem("library.key") ?: ""
        omdb = Ls.getItem("library.omdb") ?: ""
        var k = readStringList(Ls.getItem("library.keys"))
        var o = readStringList(Ls.getItem("library.omdbs"))
        if (k == null) k = if (key.isNotEmpty()) arrayListOf(key) else ArrayList()
        if (o == null) o = if (omdb.isNotEmpty()) arrayListOf(omdb) else ArrayList()

        var list: MutableList<ApiEntry>? = null
        try {
            val raw = Ls.getItem("library.apis")
            if (raw != null) {
                val a = JSONArray(raw)
                val tmp = ArrayList<ApiEntry>()
                for (i in 0 until a.length()) {
                    val e = a.optJSONObject(i)?.let { ApiEntry.fromJson(it) }
                    if (e != null) tmp.add(e)
                }
                list = tmp
            }
        } catch (e: Exception) {
            list = null
        }
        if (list == null) {
            list = arrayListOf(
                ApiEntry("tmdb", "TMDB", ArrayList(k)),
                ApiEntry("omdb", "OMDb", ArrayList(o))
            )
        }
        apis = list
        for ((id, name) in WANT) {
            if (apis.none { it.id == id }) apis.add(ApiEntry(id, name, ArrayList()))
        }
        normApis()
        keys = apiKeys("tmdb").toMutableList()
        omdbs = apiKeys("omdb").toMutableList()
        pickKeys()
    }

    /** the four built-in APIs always come first, in this order, with these names */
    fun normApis() {
        val out = ArrayList<ApiEntry>()
        for ((id, name) in WANT) {
            val a = apis.firstOrNull { it.id == id } ?: ApiEntry(id, name, ArrayList())
            a.name = name
            out.add(a)
        }
        for (a in apis) {
            if (a.id != "tmdb" && a.id != "xmdb" && a.id != "omdb" && a.id != "mdblist") out.add(a)
        }
        apis = out
    }

    /** copy of the keys of one API (empty when the API does not exist) */
    fun apiKeys(id: String): List<String> = apis.firstOrNull { it.id == id }?.keys?.toList() ?: emptyList()

    fun pickKeys() {
        key = keys.getOrNull(ki) ?: ""
        omdb = omdbs.getOrNull(oi) ?: ""
    }

    /** JSON.stringify(apis.map(a => a.keys)): used to see whether the keys changed */
    fun signature(): String {
        val a = JSONArray()
        for (e in apis) a.put(JSONArray(e.keys))
        return a.toString()
    }

    /** writes the key state to storage (the 5 LS writes the web version does after every change) */
    fun save() {
        Ls.setItem("library.apis", apisJson().toString())
        Ls.setItem("library.keys", JSONArray(keys).toString())
        Ls.setItem("library.omdbs", JSONArray(omdbs).toString())
        Ls.setItem("library.key", key)
        Ls.setItem("library.omdb", omdb)
    }

    fun apisJson(): JSONArray {
        val a = JSONArray()
        for (e in apis) a.put(e.toJson())
        return a
    }

    /**
     * Settings > Save: takes the edited list, cleans the keys (TMDB keys lose quotes and spaces, other keys are trimmed,
     * empty keys are dropped), restarts the rotation and stores everything.
     */
    fun commitEdited(edited: List<ApiEntry>) {
        apis = edited.map { a ->
            val cleaned = a.keys.map { v -> if (a.id == "tmdb") v.replace(STRIP, "") else v.trim() }.filter { it.isNotEmpty() }
            ApiEntry(a.id, a.name, cleaned.toMutableList(), a.custom)
        }.toMutableList()
        keys = apiKeys("tmdb").toMutableList()
        omdbs = apiKeys("omdb").toMutableList()
        ki = 0; oi = 0; xi = 0
        pickKeys()
        save()
    }

    /** restoreSet(): adds the APIs and keys of an imported backup to the existing ones (nothing is removed) */
    fun restore(imported: JSONArray) {
        for (i in 0 until imported.length()) {
            val b = imported.optJSONObject(i) ?: continue
            val idv = b.opt("id")
            if (idv == null || idv == JSONObject.NULL || idv.toString().isEmpty()) continue
            val ka = b.optJSONArray("keys") ?: continue
            val id = idv.toString()
            var a = apis.firstOrNull { it.id == id }
            if (a == null) {
                val nm = b.opt("name")
                val name = if (nm == null || nm == JSONObject.NULL || nm.toString().isEmpty()) id else nm.toString()
                val cu = b.opt("custom")
                val custom = cu != null && cu != JSONObject.NULL && cu != false && cu != 0 && cu.toString() != ""
                a = ApiEntry(id, name, ArrayList(), custom)
                apis.add(a)
            }
            for (n in 0 until ka.length()) {
                if (ka.isNull(n)) continue
                val k = ka.get(n).toString()
                if (k.isNotEmpty() && !a.keys.contains(k)) a.keys.add(k)
            }
        }
        normApis()
        keys = apiKeys("tmdb").toMutableList()
        omdbs = apiKeys("omdb").toMutableList()
        ki = 0; oi = 0; xi = 0
        pickKeys()
        save()
    }

    companion object {
        private val WANT = listOf("tmdb" to "TMDB", "mdblist" to "MDBList", "xmdb" to "XMDb", "omdb" to "OMDb")

        /** JS /["'\s]/g (\s in JS also covers non-breaking space, BOM and the unicode spaces) */
        private val STRIP = Regex("[\"'\\s\u00A0\uFEFF\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]")
    }
}
