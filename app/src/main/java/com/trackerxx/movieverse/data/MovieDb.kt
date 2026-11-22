package com.trackerxx.movieverse.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The library database (IndexedDB "movieverse" / store "m" in the web version): one row per movie,
 * key = movie id, value = the movie as JSON. Rows come back ordered by id, like IndexedDB getAll().
 */
class MovieDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "movieverse.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE m (id TEXT PRIMARY KEY NOT NULL, j TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /**
     * ids deleted in this session: a background refresh that was still running must never write a deleted movie back
     * (same rule as delIds in the web version)
     */
    private val delIds: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    /** `delete delIds[id]` (normM of the import): a movie that comes back from a backup may be saved again */
    fun undelete(id: String) {
        delIds.remove(id)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM m", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /**
     * loadAllP(): loads the library in pieces of 5000 movies; onProgress gets the number loaded so far
     * (called on a background thread). A damaged row is skipped, the rest still loads.
     */
    suspend fun loadAll(onProgress: ((Int) -> Unit)? = null): MutableList<Movie> = withContext(Dispatchers.IO) {
        val out = ArrayList<Movie>()
        val chunk = 5000
        var last: String? = null
        while (true) {
            val sql = if (last == null) "SELECT id, j FROM m ORDER BY id LIMIT $chunk"
            else "SELECT id, j FROM m WHERE id > ? ORDER BY id LIMIT $chunk"
            val args: Array<String>? = last?.let { arrayOf(it) }
            var n = 0
            readableDatabase.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    n++
                    last = c.getString(0)
                    try {
                        out.add(Movie(JSONObject(c.getString(1))))
                    } catch (e: Exception) {
                        // skip a damaged row
                    }
                }
            }
            onProgress?.invoke(out.size)
            if (n < chunk) break
        }
        out
    }

    /**
     * putMany(list): insert or replace, skipping movies deleted in this session.
     * The movies are turned into JSON on the calling thread (the state thread), so a movie is never read while it changes.
     */
    suspend fun putMany(list: List<Movie>) {
        val rows = list.filter { !delIds.contains(it.id) }.map { Pair(it.id, it.j.toString()) }
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val st = db.compileStatement("INSERT OR REPLACE INTO m (id, j) VALUES (?, ?)")
                for ((id, json) in rows) {
                    st.clearBindings()
                    st.bindString(1, id)
                    st.bindString(2, json)
                    st.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** delOne(id) */
    suspend fun delOne(id: String) = withContext(Dispatchers.IO) {
        delIds.add(id)
        writableDatabase.delete("m", "id = ?", arrayOf(id))
        Unit
    }
}
