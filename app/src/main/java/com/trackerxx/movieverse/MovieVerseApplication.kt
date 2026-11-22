package com.trackerxx.movieverse

import android.app.Application
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.net.Fx
import com.trackerxx.movieverse.trending.Trending

class MovieVerseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Ls.init(this)
        AppData.init(this)
        Fx.load()
        Trending.init(this)
    }
}
