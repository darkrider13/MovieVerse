package com.trackerxx.movieverse

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Ls
import com.trackerxx.movieverse.net.Fx
import com.trackerxx.movieverse.trending.Trending

class MovieVerseApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Ls.init(this)
        AppData.init(this)
        Fx.load()
        Trending.init(this)
    }

    /**
     * Poster loader tuned for scrolling: a big memory cache (posters you just scrolled past come back instantly),
     * a disk cache, no fade-in flash and no network re-check of posters that are already cached.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("posters")).maxSizeBytes(300L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
}
