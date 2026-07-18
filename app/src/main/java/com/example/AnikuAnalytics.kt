package com.example

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnikuAnalytics {

    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
    }

    // Track halaman yang dibuka
    fun trackScreen(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        }
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    // Track anime yang dibuka
    fun trackAnimeOpened(animeSlug: String, animeTitle: String) {
        val bundle = Bundle().apply {
            putString("anime_slug", animeSlug)
            putString("anime_title", animeTitle)
        }
        analytics?.logEvent("anime_opened", bundle)
    }

    // Track episode yang ditonton
    fun trackEpisodeWatched(animeTitle: String, episodeSlug: String) {
        val bundle = Bundle().apply {
            putString("anime_title", animeTitle)
            putString("episode_slug", episodeSlug)
        }
        analytics?.logEvent("episode_watched", bundle)
    }

    // Track pencarian
    fun trackSearch(query: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, query)
        }
        analytics?.logEvent(FirebaseAnalytics.Event.SEARCH, bundle)
    }

    // Track bookmark
    fun trackBookmark(animeTitle: String) {
        val bundle = Bundle().apply {
            putString("anime_title", animeTitle)
        }
        analytics?.logEvent("anime_bookmarked", bundle)
    }

    // Track server yang dipilih saat nonton
    fun trackServerSelected(serverName: String, animeTitle: String) {
        val bundle = Bundle().apply {
            putString("server_name", serverName)
            putString("anime_title", animeTitle)
        }
        analytics?.logEvent("server_selected", bundle)
    }

    // Track share anime ke feed
    fun trackSharedToFeed(animeTitle: String) {
        val bundle = Bundle().apply {
            putString("anime_title", animeTitle)
        }
        analytics?.logEvent("anime_shared_to_feed", bundle)
    }
}
