package com.trackerxx.movieverse.ui.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackerxx.movieverse.data.ApiEntry
import com.trackerxx.movieverse.data.AppData

/** one API while it is being edited in Settings (an entry of the web version's `sapis`) */
@Stable
class EditApi(val id: String, val name: String, val custom: Boolean, keys: List<String>) {
    val keys = mutableStateListOf<String>().also { it.addAll(keys) }

    /** bumped when the web version redraws the key list (drawL / drawA): every eye goes back to "hidden" */
    var gen by mutableIntStateOf(0)

    /** scroll position of this API's key list (every API panel keeps its own, like the hidden .ak panels) */
    val listState = LazyListState()
}

/**
 * Everything the Settings dialog edits. A fresh copy is made every time the dialog opens
 * (`$('#set').onclick`: sapis = JSON.parse(JSON.stringify(apis)); empty key list -> ['']).
 * Nothing touches the real API keys until Save (see commit()). The name is saved while typing.
 */
@Stable
class SettingsState {
    val apis = mutableStateListOf<EditApi>()

    /** index of the API tab that is open (`cur`) */
    var cur by mutableIntStateOf(0)
        private set

    /** the text of the name field (`#nm`) */
    var name by mutableStateOf(AppData.uname)
        private set

    init {
        for (a in AppData.apis.apis) {
            val ks = a.copy().keys
            if (ks.isEmpty()) ks.add("")
            apis.add(EditApi(a.id, a.name, a.custom, ks))
        }
        cur = 0
    }

    /** `$('#nm').oninput`: uname = value.trim(), saved at once, the hero title follows (maxlength 30) */
    fun onName(v: String) {
        name = if (v.length > 30) v.substring(0, 30) else v
        AppData.setName(name)
    }

    /** setTab(n) */
    fun setTab(n: Int) {
        if (n == cur) return
        cur = n
    }

    /** drawA(): the whole list is drawn again, so every eye goes back to hidden */
    private fun redrawAll() {
        for (a in apis) a.gen++
    }

    /** drawL(i) */
    private fun redrawList(a: EditApi) {
        a.gen++
    }

    /** the "+ Add key" button; returns false when the limit (10) is reached so the caller can show the toast */
    fun addKey(i: Int): Boolean {
        val a = apis.getOrNull(i) ?: return true
        if (a.keys.size >= 10) return false
        a.keys.add("")
        redrawList(a)
        return true
    }

    /** the built-in APIs can never be removed, only their keys */
    fun isBuiltIn(a: EditApi) = a.id == "tmdb" || a.id == "xmdb" || a.id == "omdb" || a.id == "subs"

    /**
     * the trash button of a key row: the row is deleted (a single row is only cleared). The caller asks "Remove API?"
     * first when this is the last key of an API the person added; then it calls removeApi() instead.
     */
    fun deleteKey(i: Int, j: Int) {
        val a = apis.getOrNull(i) ?: return
        if (j in a.keys.indices) a.keys.removeAt(j)
        if (a.keys.isEmpty()) a.keys.add("")
        redrawList(a)
    }

    /** the typed text of one key box */
    fun setKey(i: Int, j: Int, v: String) {
        val a = apis.getOrNull(i) ?: return
        if (j in a.keys.indices) a.keys[j] = v
    }

    /** id for a new API: the built-in ones are recognised by name, any other name gets 'c' + uid() */
    private fun idFor(n: String): String = when (n.lowercase()) {
        "tmdb" -> "tmdb"
        "imdb", "omdb" -> "omdb"
        "xmdb" -> "xmdb"
        "mdblist" -> "mdblist"
        "subtitles", "subtitle", "subs" -> "subs"
        else -> "c" + AppData.uid()
    }

    /**
     * addApi() after the name was typed. Returns an error text for a toast, or null when it was added.
     * (Note: like the web version, an API added here is not marked "custom".)
     */
    fun addApi(rawName: String): String? {
        if (apis.size >= 20) return "Up to 20 APIs"
        val n = if (rawName.length > 24) rawName.substring(0, 24) else rawName
        if (n.isEmpty()) return null
        val id = idFor(n)
        if (apis.any { it.id == id || it.name.lowercase() == n.lowercase() }) return "That API is already added"
        apis.add(EditApi(id, n, false, listOf("")))
        cur = apis.size - 1
        redrawAll()
        return null
    }

    /** removes API number di (after the "Remove ...?" question was answered Remove) */
    fun removeApi(di: Int) {
        if (di !in apis.indices) return
        apis.removeAt(di)
        cur = maxOf(0, minOf(cur, apis.size - 1))
        redrawAll()
    }

    /**
     * Save, first part (`$('#ksave').onclick` up to `$('#sdlg').close()`): the edited list replaces the real one,
     * keys are cleaned and everything is stored. Returns (changed, has):
     * changed = the keys are different from before, has = at least one key exists now.
     */
    fun commit(): Pair<Boolean, Boolean> {
        val prev = AppData.apis.signature()
        AppData.apis.commitEdited(apis.map { ApiEntry(it.id, it.name, it.keys.toMutableList(), it.custom) })
        val changed = AppData.apis.signature() != prev
        val has = AppData.apis.apis.any { it.keys.isNotEmpty() }
        return changed to has
    }
}
