package com.trackerxx.movieverse.data

import java.io.Writer
import java.util.Calendar

/** Backup file, byte-for-byte the same layout the web version writes: {"movies":[..],"folders":[..],"statuses":[..],"settings":{..}} */
object Backup {

    /** movieverse-backup-YYYY-MM-DD_HH-mm-ss.json */
    fun fileName(c: Calendar = Calendar.getInstance()): String {
        fun z(n: Int) = if (n < 10) "0$n" else "$n"
        return "movieverse-backup-" + c.get(Calendar.YEAR) + "-" + z(c.get(Calendar.MONTH) + 1) + "-" + z(c.get(Calendar.DAY_OF_MONTH)) +
            "_" + z(c.get(Calendar.HOUR_OF_DAY)) + "-" + z(c.get(Calendar.MINUTE)) + "-" + z(c.get(Calendar.SECOND)) + ".json"
    }

    /** writes the whole library in pieces of 1000 movies; onProgress gets the number of movies written so far */
    fun write(out: Writer, onProgress: ((Int) -> Unit)? = null) {
        val movies = AppData.movies
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
