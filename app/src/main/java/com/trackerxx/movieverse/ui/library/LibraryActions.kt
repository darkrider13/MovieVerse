package com.trackerxx.movieverse.ui.library

import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Folder
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.data.Status
import com.trackerxx.movieverse.ui.components.FolderPalette
import com.trackerxx.movieverse.ui.components.ModalResult
import com.trackerxx.movieverse.ui.components.ModalSpec
import com.trackerxx.movieverse.ui.components.ModalState
import com.trackerxx.movieverse.ui.components.ToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Everything the Library page changes: statuses (tabs), folders and the order of cards / tabs. */
class LibraryActions(
    private val lib: LibraryState,
    private val modal: ModalState,
    private val toast: ToastState,
    private val scope: CoroutineScope
) {

    /** + tab: New status */
    fun addStatus() {
        scope.launch {
            val r = modal.ask(ModalSpec(title = "New status", input = true, placeholder = "Status name", ok = "Add"))
            if (r is ModalResult.Ok) {
                val id = "s" + AppData.uid()
                AppData.statuses.add(Status.create(id, r.text))
                AppData.saveStatuses()
                AppData.rebuildSL()
                lib.tab = id
                lib.folder = null
                lib.lim = 60
                AppData.render()
            }
        }
    }

    /** tap on the selected tab: rename, or delete with everything in it */
    fun editStatus() {
        val st = AppData.statuses.firstOrNull { it.id == lib.tab } ?: return
        scope.launch {
            val r = modal.ask(ModalSpec(title = "Edit status", input = true, value = st.name, ok = "Save", del = true))
            if (r is ModalResult.Delete) {
                if (AppData.statuses.size < 2) {
                    toast.show("Keep at least one status")
                    return@launch
                }
                val id = st.id
                val mv = AppData.movies.filter { it.status == id }
                val fl = AppData.folders.filter { it.status == id }
                val parts = ArrayList<String>()
                if (fl.isNotEmpty()) parts.add(fl.size.toString() + (if (fl.size > 1) " folders" else " folder"))
                if (mv.isNotEmpty()) parts.add("all " + mv.size + (if (mv.size > 1) " movies" else " movie") + " in it")
                val y = modal.ask(
                    ModalSpec(
                        title = "Delete \"" + st.name + "\"?",
                        text = "Warning: this permanently deletes the status" +
                            (if (parts.isNotEmpty()) " along with its " + parts.joinToString(" and ") else "") +
                            ". This cannot be undone.",
                        ok = "Delete", danger = true
                    )
                )
                if (y !is ModalResult.Ok) return@launch
                for (m in mv) AppData.db.delOne(m.id)
                AppData.movies = AppData.movies.filter { it.status != id }.toMutableList()
                AppData.statuses = AppData.statuses.filter { it.id != id }.toMutableList()
                AppData.folders = AppData.folders.filter { it.status != id }.toMutableList()
                AppData.saveStatuses()
                AppData.saveFolders()
                AppData.rebuildSL()
                lib.tab = AppData.statuses.firstOrNull()?.id ?: "upcoming"
                lib.folder = null
                AppData.render()
                toast.show("Status deleted")
            } else if (r is ModalResult.Ok) {
                st.name = r.text
                AppData.saveStatuses()
                AppData.rebuildSL()
                AppData.render()
            }
        }
    }

    /** + Folder chip */
    fun newFolder() {
        val tab = lib.tab
        scope.launch {
            val r = modal.ask(
                ModalSpec(
                    title = "New folder in " + (AppData.SL[tab] ?: ""), input = true, placeholder = "Folder name",
                    colors = true, color = FolderPalette[AppData.folders.size % FolderPalette.size], ok = "Create"
                )
            )
            if (r is ModalResult.Ok) {
                AppData.folders.add(Folder.create(AppData.uid(), r.text, tab, r.color))
                AppData.saveFolders()
                AppData.render()
            }
        }
    }

    /** tap on the selected folder chip, or long press on any folder chip */
    fun editFolder(id: String) {
        val cf = AppData.folders.firstOrNull { it.id == id } ?: return
        scope.launch {
            val r = modal.ask(
                ModalSpec(title = "Edit folder", input = true, value = cf.name, colors = true, color = cf.color, ok = "Save", del = true)
            )
            if (r is ModalResult.Delete) {
                val y = modal.ask(
                    ModalSpec(
                        title = "Delete \"" + cf.name + "\"?",
                        text = "The movies stay in " + (AppData.SL[cf.status] ?: "this section") + ". Only the folder is removed.",
                        ok = "Delete", danger = true
                    )
                )
                if (y is ModalResult.Ok) {
                    val ch = AppData.movies.filter { it.folder == cf.id }
                    ch.forEach { it.folder = null }
                    AppData.db.putMany(ch)
                    AppData.folders = AppData.folders.filter { it !== cf }.toMutableList()
                    if (lib.folder == cf.id) lib.folder = null
                    AppData.saveFolders()
                    AppData.render()
                }
            } else if (r is ModalResult.Ok) {
                cf.name = r.text
                cf.color = r.color
                AppData.saveFolders()
                AppData.render()
            }
        }
    }

    /** saveOrder(ids): the shown cards were re-ordered; write "ord" for the whole tab so the order survives */
    fun saveOrder(ids: List<String>, ordCtx: List<String>?) {
        if (ordCtx == null) {
            AppData.render()
            return
        }
        val full = ordCtx.toMutableList()
        val set = ids.toHashSet()
        var k = 0
        for (i in full.indices) {
            if (set.contains(full[i]) && k < ids.size) full[i] = ids[k++]
        }
        val by = AppData.movies.associateBy { it.id }
        val ch = ArrayList<Movie>()
        full.forEachIndexed { i, id ->
            val m = by[id]
            if (m != null && m.num("ord") != i.toDouble()) {
                m.set("ord", i)
                ch.add(m)
            }
        }
        if (ch.isNotEmpty()) scope.launch { AppData.db.putMany(ch) }
        AppData.render()
    }

    /** the tabs were re-ordered (ids without "all") */
    fun saveTabOrder(ids: List<String>) {
        val by = AppData.statuses.associateBy { it.id }
        val ns = ids.mapNotNull { by[it] }
        if (ns.size == AppData.statuses.size) {
            AppData.statuses = ns.toMutableList()
            AppData.saveStatuses()
        }
        AppData.render()
    }
}
