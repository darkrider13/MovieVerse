package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.AppData

/** The API clients, all sharing the same key state (AppData.apis). */
object Net {
    val tmdb: Tmdb by lazy { Tmdb(AppData.apis) }
    val omdb: Omdb by lazy { Omdb(AppData.apis) }
    val mdblist: MdbList by lazy { MdbList(AppData.apis) }
    val xmdb: Xmdb by lazy { Xmdb(AppData.apis) }
}
