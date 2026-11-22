package com.trackerxx.movieverse.ui.settings

import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.AppScope
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.releases.Releases
import com.trackerxx.movieverse.trending.Trending
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What happens after Settings > Save has stored the keys (the rest of `$('#ksave').onclick`). */
object KeySave {

    /**
     * keyLoad(): checks the TMDB key (`tmdb('/configuration')`, only when a key exists) and then loads Trending when it is on screen
     * (trendReady). Returns the error text (the web version returns the Error object) or null when everything is fine.
     * trendOn = Trending is the open page (home is Trending and Releases is not open).
     */
    suspend fun keyLoad(trendOn: Boolean): String? {
        var err: String? = null
        if (AppData.apis.key.isNotEmpty()) {
            try {
                Net.tmdb.get("/configuration")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e.message ?: e.toString()
            }
        }
        if (trendOn) {
            try {
                Trending.load(true)
                Trending.waitImgs(6000L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return e.message ?: e.toString()
            }
        }
        return err
    }

    /**
     * after(): old failures are forgotten, the Releases list is dropped (reloaded now when Releases is open, otherwise the next
     * time it opens) and the library is checked again in the background.
     */
    suspend fun after(releasesOpen: Boolean) {
        Enrich.resetAfterKeys()
        Releases.items = emptyList()
        if (releasesOpen) Releases.loadUp(true)
        AppScope.launch { Enrich.backfill() }
    }
}
