package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.CustomSeriesAssignment
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

    fun loadCustomSeries(): Map<String, CustomSeriesAssignment> {
        val json = runCatching { JSONObject(preferences.getString(KEY_CUSTOM_SERIES, "{}").orEmpty()) }
            .getOrNull() ?: return emptyMap()
        val legacyOrders = mutableMapOf<String, Int>()
        return buildMap<String, CustomSeriesAssignment> {
            json.keys().forEach { id ->
                val value = json.opt(id)
                if (value is JSONObject) {
                    val name = value.optString("name").trim()
                    if (name.isNotEmpty()) put(id, CustomSeriesAssignment(name, value.optInt("order", 0)))
                } else {
                    val name = value?.toString().orEmpty().trim()
                    if (name.isNotEmpty()) {
                        val order = legacyOrders.getOrDefault(name, 0)
                        put(id, CustomSeriesAssignment(name, order))
                        legacyOrders[name] = order + 1
                    }
                }
            }
        }
    }

    fun saveCustomSeries(series: Map<String, CustomSeriesAssignment>) {
        val json = JSONObject()
        series.forEach { (id, assignment) ->
            json.put(id, JSONObject().put("name", assignment.name).put("order", assignment.order))
        }
        preferences.edit().putString(KEY_CUSTOM_SERIES, json.toString()).apply()
    }

    fun loadHiddenIds(): Set<String> =
        preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun saveHiddenIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_HIDDEN, ids).apply()

    fun loadCustomTitles(): Map<String, String> {
        val json = runCatching { JSONObject(preferences.getString(KEY_CUSTOM_TITLES, "{}").orEmpty()) }
            .getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { id ->
                json.optString(id).trim().takeIf(String::isNotEmpty)?.let { put(id, it) }
            }
        }
    }

    fun saveCustomTitles(titles: Map<String, String>) {
        val json = JSONObject()
        titles.forEach { (id, title) -> json.put(id, title) }
        preferences.edit().putString(KEY_CUSTOM_TITLES, json.toString()).apply()
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
        const val KEY_CUSTOM_SERIES = "custom_book_series"
        const val KEY_HIDDEN = "hidden_book_ids"
        const val KEY_CUSTOM_TITLES = "custom_book_titles"
        const val KEY_SCALE = "viewer_scale"
        const val KEY_PAGE_NUMBER = "viewer_page_number"
        const val KEY_KEEP_SCREEN_ON = "viewer_keep_screen_on"
    }
}
