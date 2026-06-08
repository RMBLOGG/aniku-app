package com.example.network

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

data class BookmarkedAnime(
    val slug: String,
    val title: String,
    val poster: String,
    val type: String? = null,
    val episode: String? = null
)

class BookmarkManager(context: Context) {
    private val prefs = context.getSharedPreferences("aniku_bookmarks", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(List::class.java, BookmarkedAnime::class.java)
    private val adapter = moshi.adapter<List<BookmarkedAnime>>(type)

    fun getBookmarks(): List<BookmarkedAnime> {
        val json = prefs.getString("bookmarks_list", null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isBookmarked(slug: String): Boolean {
        return getBookmarks().any { it.slug == slug }
    }

    fun addBookmark(anime: BookmarkedAnime) {
        val current = getBookmarks().toMutableList()
        if (current.none { it.slug == anime.slug }) {
            current.add(anime)
            saveBookmarks(current)
        }
    }

    fun removeBookmark(slug: String) {
        val current = getBookmarks().filterNot { it.slug == slug }
        saveBookmarks(current)
    }

    private fun saveBookmarks(list: List<BookmarkedAnime>) {
        val json = adapter.toJson(list)
        prefs.edit().putString("bookmarks_list", json).apply()
    }
}
