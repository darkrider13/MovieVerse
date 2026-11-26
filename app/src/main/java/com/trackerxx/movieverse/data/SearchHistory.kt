package com.trackerxx.movieverse.data

import org.json.JSONArray

/**
 * Recent searches of the "Add to Library" dialog. Newest first, no duplicates (case-insensitive),
 * capped at [MAX] entries and kept in [Ls] so it survives app restarts.
 */
object SearchHistory {
    private const val KEY = "library.searchHistory"
    const val MAX = 5

    fun load(): List<String> = try {
        val a = JSONArray(Ls.getItem(KEY) ?: "[]")
        (0 until a.length()).mapNotNull { a.optString(it, "").trim().ifEmpty { null } }.take(MAX)
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(l: List<String>) {
        try {
            if (l.isEmpty()) Ls.removeItem(KEY) else Ls.setItem(KEY, JSONArray(l).toString())
        } catch (e: Exception) {
        }
    }

    /** puts [q] on top; returns the new list */
    fun add(q: String): List<String> {
        val t = q.trim()
        if (t.length < 2) return load()
        val l = ArrayList<String>()
        l.add(t)
        load().filterTo(l) { !it.equals(t, ignoreCase = true) }
        val r = l.take(MAX)
        save(r)
        return r
    }

    fun remove(q: String): List<String> {
        val r = load().filter { !it.equals(q, ignoreCase = true) }
        save(r)
        return r
    }

    fun clear(): List<String> {
        save(emptyList())
        return emptyList()
    }
}
