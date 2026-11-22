package com.trackerxx.movieverse.data

import java.io.Writer
import java.util.Calendar

/**
 * Backup files.
 *  - The export is an Excel file (XlsxBackup), named by fileName().
 *  - write() is the JSON layout the web version writes ({"movies":[..],"folders":[..],"statuses":[..],"settings":{..}}). It is still
 *    used for the safety copy "before-last-import.json" that is saved before every import (and an old .json backup can still be imported).
 */
object Backup {

    /** movieverse-backup-YYYY-MM-DD_HH-mm-ss.xlsx */
    fun fileName(c: Calendar = Calendar.getInstance()): String {
        fun z(n: Int) = if (n < 10) "0$n" else "$n"
        return "movieverse-backup-" + c.get(Calendar.YEAR) + "-" + z(c.get(Calendar.MONTH) + 1) + "-" + z(c.get(Calendar.DAY_OF_MONTH)) +
            "_" + z(c.get(Calendar.HOUR_OF_DAY)) + "-" + z(c.get(Calendar.MINUTE)) + "-" + z(c.get(Calendar.SECOND)) + ".xlsx"
    }

    /** writes the whole library in pieces of 1000 movies; onProgress gets the number of movies written so far */
    fun write(out: Writer, onProgress: ((Int) -> Unit)? = null) {
        val movies = AppData.movies.toTypedArray()
        out.write("{\"movies\":[")
        var i = 0
        while (i < movies.size) {
            val end = minOf(i + 1000, movies.size)
            val sb = StringBuilder()
            for (k in i until end) {
                if (k > 0) sb.append(',')
                sb.append(movies[k].j.toString())
            }
            out.write(sb.toString())
            i = end
            onProgress?.invoke(i)
        }
        out.write("],\"folders\":" + AppData.foldersJson() + ",\"statuses\":" + AppData.statusesJson() + ",\"settings\":" + AppData.settingsJson() + "}")
        out.flush()
    }
}
