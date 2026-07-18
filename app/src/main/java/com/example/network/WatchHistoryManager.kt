package com.example.network

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

data class WatchHistoryItem(
    val animeSlug: String,
    val animeTitle: String,
    val animePoster: String,
    val episodeSlug: String,
    val episodeTitle: String,
    val watchedAt: Long = System.currentTimeMillis()
)

class WatchHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("aniku_watch_history", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(List::class.java, WatchHistoryItem::class.java)
    private val adapter = moshi.adapter<List<WatchHistoryItem>>(type)
    private val MAX_HISTORY = 50

    fun getHistory(): List<WatchHistoryItem> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistory(item: WatchHistoryItem) {
        val current = getHistory().toMutableList()
        // Hapus entry lama dengan episode yang sama
        current.removeAll { it.episodeSlug == item.episodeSlug }
        // Tambah di depan
        current.add(0, item)
        // Batasi max 50
        val trimmed = if (current.size > MAX_HISTORY) current.take(MAX_HISTORY) else current
        saveHistory(trimmed)
    }

    fun clearHistory() {
        prefs.edit().remove("history_list").apply()
    }

    private fun saveHistory(list: List<WatchHistoryItem>) {
        val json = adapter.toJson(list)
        prefs.edit().putString("history_list", json).apply()
    }
}
