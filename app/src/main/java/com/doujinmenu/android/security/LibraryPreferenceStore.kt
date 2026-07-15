package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.model.ViewerScale
import org.json.JSONObject

class LibraryPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadFavoriteIds(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    fun saveFavoriteIds(ids: Set<String>) = preferences.edit().putStringSet(KEY_FAVORITES, ids).apply()

    fun loadReadIds(): Set<String> = preferences.getStringSet(KEY_READ, emptySet()).orEmpty()
    fun saveReadIds(ids: Set<String>) = preferences.edit().putStringSet(KEY_READ, ids).apply()

    fun loadProgress(): Map<String, Int> {
        val json = runCatching { JSONObject(preferences.getString(KEY_PROGRESS, "{}").orEmpty()) }.getOrNull()
            ?: return emptyMap()
        return buildMap {
            json.keys().forEach { key -> put(key, json.optInt(key, 0)) }
        }
    }

    fun saveProgress(progress: Map<String, Int>) {
        val json = JSONObject()
        progress.forEach { (id, page) -> json.put(id, page) }
        preferences.edit().putString(KEY_PROGRESS, json.toString()).apply()
    }

    fun loadViewerPreferences() = ViewerPreferences(
        scale = runCatching {
            ViewerScale.valueOf(preferences.getString(KEY_SCALE, null).orEmpty())
        }.getOrDefault(ViewerScale.FIT_SCREEN),
        showPageNumber = preferences.getBoolean(KEY_PAGE_NUMBER, true),
        keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true),
    )

    fun saveViewerPreferences(value: ViewerPreferences) {
        preferences.edit()
            .putString(KEY_SCALE, value.scale.name)
            .putBoolean(KEY_PAGE_NUMBER, value.showPageNumber)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOn)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "library_preferences"
        const val KEY_FAVORITES = "favorite_book_ids"
        const val KEY_READ = "read_book_ids"
        const val KEY_PROGRESS = "book_progress"
        const val KEY_SCALE = "viewer_scale"
        const val KEY_PAGE_NUMBER = "viewer_page_number"
        const val KEY_KEEP_SCREEN_ON = "viewer_keep_screen_on"
    }
}
