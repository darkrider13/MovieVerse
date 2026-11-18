package com.trackerxx.movieverse.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Same as the web version's window.localStorage: string key -> string value, same key names
 * ("library.theme", "library.apis", "library.folders" ...), synchronous reads and writes.
 */
object Ls {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("movieverse_ls", Context.MODE_PRIVATE)
    }

    /** null when the key does not exist (like localStorage.getItem). */
    fun getItem(key: String): String? = sp.getString(key, null)

    fun setItem(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun removeItem(key: String) {
        sp.edit().remove(key).apply()
    }
}
