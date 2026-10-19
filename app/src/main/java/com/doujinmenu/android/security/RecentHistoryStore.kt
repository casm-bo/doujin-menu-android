package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.HistoryAction
import com.doujinmenu.android.model.HistorySource
import com.doujinmenu.android.model.RECENT_HISTORY_LIMIT
import com.doujinmenu.android.model.RecentHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

class RecentHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("recent_history", Context.MODE_PRIVATE)
    fun load(): List<RecentHistoryEntry> = decodeRecentHistory(preferences.getString("entries", null))
    fun save(entries: List<RecentHistoryEntry>) {
        preferences.edit().putString("entries", encodeRecentHistory(entries)).apply()
    }
    fun isRecording(): Boolean = preferences.getBoolean("recording", true)
    fun setRecording(enabled: Boolean) { preferences.edit().putBoolean("recording", enabled).apply() }
}

internal fun encodeRecentHistory(entries: List<RecentHistoryEntry>): String = JSONArray(entries.take(RECENT_HISTORY_LIMIT).map { entry ->
    JSONObject().put("key", entry.key).put("title", entry.title).put("viewedAt", entry.viewedAt)
        .put("source", entry.source.name).put("galleryId", entry.galleryId).put("bookId", entry.bookId)
        .put("syncId", entry.syncId).put("artist", entry.artist).put("thumbnail", entry.thumbnail)
        .put("pageCount", entry.pageCount).put("page", entry.page).put("action", entry.action.name)
        .put("completed", entry.completed)
}).toString()

internal fun decodeRecentHistory(raw: String?): List<RecentHistoryEntry> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until minOf(array.length(), RECENT_HISTORY_LIMIT)).mapNotNull { index -> runCatching {
        val item = array.getJSONObject(index)
        fun string(key: String): String? = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
        val galleryId = item.optLong("galleryId").takeIf { it > 0 }
        val bookId = string("bookId")
        val syncId = string("syncId")
        require(galleryId != null || bookId != null || syncId != null)
        RecentHistoryEntry(
            key = item.getString("key").also { require(it.isNotBlank()) }, title = item.getString("title"),
            viewedAt = item.getLong("viewedAt"), source = HistorySource.valueOf(item.getString("source")),
            galleryId = galleryId, bookId = bookId, syncId = syncId, artist = string("artist").orEmpty(),
            thumbnail = string("thumbnail"), pageCount = item.optInt("pageCount").coerceAtLeast(0),
            page = if (item.has("page") && !item.isNull("page")) item.getInt("page").coerceAtLeast(0) else null,
            action = HistoryAction.valueOf(item.optString("action", "DETAIL")), completed = item.optBoolean("completed"),
        )
    }.getOrNull() }.distinctBy { it.key }.sortedByDescending { it.viewedAt }
}.getOrDefault(emptyList())
