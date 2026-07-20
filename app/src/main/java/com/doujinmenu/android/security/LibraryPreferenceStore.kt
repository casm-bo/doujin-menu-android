package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.model.ViewerPageTurnMode
import com.doujinmenu.android.model.ViewerReadingDirection
import com.doujinmenu.android.model.ViewerScale
import com.doujinmenu.android.model.ViewerTapAction
import com.doujinmenu.android.model.ViewerTapZones
import org.json.JSONObject

class LibraryPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadFavoriteIds(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    fun saveFavoriteIds(ids: Set<String>) = preferences.edit().putStringSet(KEY_FAVORITES, ids).apply()

    fun loadFavoriteSeriesNames(): Set<String> =
        preferences.getStringSet(KEY_SERIES_FAVORITES, emptySet()).orEmpty()

    fun saveFavoriteSeriesNames(names: Set<String>) =
        preferences.edit().putStringSet(KEY_SERIES_FAVORITES, names).apply()

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
                    val name = value.takeUnless { it.isNull("name") }
                        ?.optString("name")
                        ?.trim()
                        .orEmpty()
                    if (name.isNotEmpty() && !name.equals("null", ignoreCase = true)) put(
                        id,
                        CustomSeriesAssignment(
                            name = name,
                            order = value.optInt("order", 0),
                            modifiedAt = value.optLong("modifiedAt", 0L),
                        ),
                    )
                } else {
                    val name = value?.toString().orEmpty().trim()
                    if (name.isNotEmpty() && !name.equals("null", ignoreCase = true)) {
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
            json.put(
                id,
                JSONObject()
                    .put("name", assignment.name)
                    .put("order", assignment.order)
                    .put("modifiedAt", assignment.modifiedAt),
            )
        }
        preferences.edit().putString(KEY_CUSTOM_SERIES, json.toString()).apply()
    }

    fun loadSeriesRemovalTimes(): Map<String, Long> {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_SERIES_REMOVAL_TIMES, "{}").orEmpty())
        }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { id -> put(id, json.optLong(id, 0L)) }
        }
    }

    fun saveSeriesRemovalTimes(times: Map<String, Long>) {
        val json = JSONObject()
        times.forEach { (id, modifiedAt) -> json.put(id, modifiedAt) }
        preferences.edit().putString(KEY_SERIES_REMOVAL_TIMES, json.toString()).apply()
    }

    fun loadPendingSeriesSyncIds(): Set<String> =
        preferences.getStringSet(KEY_PENDING_SERIES_SYNC, emptySet()).orEmpty().toSet()

    fun savePendingSeriesSyncIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_PENDING_SERIES_SYNC, ids).apply()

    fun loadPendingBookStateSyncIds(): Set<String> =
        preferences.getStringSet(KEY_PENDING_BOOK_STATE_SYNC, emptySet()).orEmpty().toSet()

    fun savePendingBookStateSyncIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_PENDING_BOOK_STATE_SYNC, ids).apply()

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
        readingDirection = enumPreference(KEY_READING_DIRECTION, ViewerReadingDirection.LEFT_TO_RIGHT),
        pageTurnMode = enumPreference(KEY_PAGE_TURN_MODE, ViewerPageTurnMode.SWIPE_AND_TAP),
        customTapZonesEnabled = preferences.getBoolean(KEY_CUSTOM_TAP_ZONES_ENABLED, false),
        tapZones = ViewerTapZones(
            preferences.getString(KEY_TAP_ZONES, null)
                ?.split(',')
                ?.mapNotNull { value -> runCatching { ViewerTapAction.valueOf(value) }.getOrNull() }
                ?.takeIf { it.size == 9 }
                ?: ViewerTapZones.DEFAULT_ACTIONS,
        ),
    )

    fun saveViewerPreferences(value: ViewerPreferences) {
        preferences.edit()
            .putString(KEY_SCALE, value.scale.name)
            .putBoolean(KEY_PAGE_NUMBER, value.showPageNumber)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOn)
            .putString(KEY_READING_DIRECTION, value.readingDirection.name)
            .putString(KEY_PAGE_TURN_MODE, value.pageTurnMode.name)
            .putBoolean(KEY_CUSTOM_TAP_ZONES_ENABLED, value.customTapZonesEnabled)
            .putString(KEY_TAP_ZONES, value.tapZones.actions.joinToString(",") { it.name })
            .apply()
    }

    fun loadOnlineProgress(): Map<Long, Int> {
        val json = runCatching { JSONObject(preferences.getString(KEY_ONLINE_PROGRESS, "{}").orEmpty()) }
            .getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { key -> key.toLongOrNull()?.let { put(it, json.optInt(key, 0)) } }
        }
    }

    fun saveOnlineProgress(progress: Map<Long, Int>) {
        val json = JSONObject()
        progress.forEach { (id, page) -> json.put(id.toString(), page) }
        preferences.edit().putString(KEY_ONLINE_PROGRESS, json.toString()).apply()
    }

    fun loadActiveBookId(): String? =
        preferences.getString(KEY_ACTIVE_BOOK_ID, null)?.takeIf(String::isNotBlank)

    fun saveActiveBookId(bookId: String?) {
        preferences.edit().apply {
            if (bookId == null) remove(KEY_ACTIVE_BOOK_ID) else putString(KEY_ACTIVE_BOOK_ID, bookId)
        }.apply()
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, null).orEmpty()) }.getOrDefault(fallback)

    fun loadLibraryViewMode(): String = preferences.getString(KEY_LIBRARY_VIEW_MODE, "GRID") ?: "GRID"

    fun saveLibraryViewMode(value: String) =
        preferences.edit().putString(KEY_LIBRARY_VIEW_MODE, value).apply()

    fun loadLibraryGridColumns(): Int = preferences.getInt(KEY_LIBRARY_GRID_COLUMNS, 3).coerceIn(2, 4)

    fun saveLibraryGridColumns(value: Int) =
        preferences.edit().putInt(KEY_LIBRARY_GRID_COLUMNS, value.coerceIn(2, 4)).apply()

    private companion object {
        const val FILE_NAME = "library_preferences"
        const val KEY_FAVORITES = "favorite_book_ids"
        const val KEY_SERIES_FAVORITES = "favorite_series_names"
        const val KEY_READ = "read_book_ids"
        const val KEY_PROGRESS = "book_progress"
        const val KEY_ONLINE_PROGRESS = "online_gallery_progress"
        const val KEY_ACTIVE_BOOK_ID = "active_library_book_id"
        const val KEY_CUSTOM_SERIES = "custom_book_series"
        const val KEY_SERIES_REMOVAL_TIMES = "series_removal_times"
        const val KEY_PENDING_SERIES_SYNC = "pending_series_sync_ids"
        const val KEY_PENDING_BOOK_STATE_SYNC = "pending_book_state_sync_ids"
        const val KEY_HIDDEN = "hidden_book_ids"
        const val KEY_CUSTOM_TITLES = "custom_book_titles"
        const val KEY_SCALE = "viewer_scale"
        const val KEY_PAGE_NUMBER = "viewer_page_number"
        const val KEY_KEEP_SCREEN_ON = "viewer_keep_screen_on"
        const val KEY_READING_DIRECTION = "viewer_reading_direction"
        const val KEY_PAGE_TURN_MODE = "viewer_page_turn_mode"
        const val KEY_CUSTOM_TAP_ZONES_ENABLED = "viewer_custom_tap_zones_enabled"
        const val KEY_TAP_ZONES = "viewer_tap_zones"
        const val KEY_LIBRARY_VIEW_MODE = "library_view_mode"
        const val KEY_LIBRARY_GRID_COLUMNS = "library_grid_columns"
    }
}
