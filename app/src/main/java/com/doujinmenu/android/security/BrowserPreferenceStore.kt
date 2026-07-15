package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.SearchFavorite
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class BrowserPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadFavorites(): List<SearchFavorite> {
        val raw = preferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val query = item.optString("query").trim()
                    if (query.isEmpty()) return@repeat
                    add(
                        SearchFavorite(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name").ifBlank { query },
                            query = query,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(favorites: List<SearchFavorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            array.put(
                JSONObject()
                    .put("id", favorite.id)
                    .put("name", favorite.name)
                    .put("query", favorite.query),
            )
        }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun loadPreferredLanguages(): Set<String> =
        preferences.getStringSet(KEY_LANGUAGES, emptySet()).orEmpty()
            .mapTo(linkedSetOf()) { it.trim().lowercase() }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }

    fun savePreferredLanguages(languages: Set<String>) {
        preferences.edit().putStringSet(KEY_LANGUAGES, languages).apply()
    }

    fun loadKnownFilterTokens(): Set<String> =
        preferences.getStringSet(KEY_KNOWN_FILTERS, emptySet()).orEmpty().toSet()

    fun saveKnownFilterTokens(tokens: Set<String>) {
        preferences.edit().putStringSet(KEY_KNOWN_FILTERS, tokens).apply()
    }

    fun loadViewedGalleryIds(): Set<Long> =
        preferences.getStringSet(KEY_VIEWED_GALLERIES, emptySet()).orEmpty()
            .mapNotNullTo(linkedSetOf(), String::toLongOrNull)

    fun saveViewedGalleryIds(ids: Set<Long>) {
        preferences.edit().putStringSet(
            KEY_VIEWED_GALLERIES,
            ids.mapTo(linkedSetOf(), Long::toString),
        ).apply()
    }

    private companion object {
        const val FILE_NAME = "browser_preferences"
        const val KEY_FAVORITES = "search_favorites"
        const val KEY_LANGUAGES = "preferred_languages"
        const val KEY_KNOWN_FILTERS = "known_filter_tokens"
        const val KEY_VIEWED_GALLERIES = "viewed_gallery_ids"
    }
}
