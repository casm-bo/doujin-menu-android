package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.BrowserPage
import com.doujinmenu.android.model.BrowserTab
import com.doujinmenu.android.model.BrowserWorkspace
import com.doujinmenu.android.model.SearchFavorite
import com.doujinmenu.android.model.StorageLocation
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

    fun loadCustomLanguages(): Set<String> =
        preferences.getStringSet(KEY_CUSTOM_LANGUAGES, emptySet()).orEmpty()
            .mapTo(linkedSetOf()) { it.trim().lowercase() }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }

    fun saveCustomLanguages(languages: Set<String>) {
        preferences.edit().putStringSet(KEY_CUSTOM_LANGUAGES, languages).apply()
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

    fun loadBrowserWorkspace(): BrowserWorkspace {
        val raw = preferences.getString(KEY_BROWSER_WORKSPACE, null) ?: return BrowserWorkspace.initial()
        return browserWorkspaceFromJson(raw)
    }

    fun saveBrowserWorkspace(workspace: BrowserWorkspace) {
        preferences.edit().putString(KEY_BROWSER_WORKSPACE, browserWorkspaceToJson(workspace)).apply()
    }

    fun loadLibraryLocations(): List<StorageLocation> =
        loadStorageLocations(KEY_LIBRARY_LOCATIONS)

    fun saveLibraryLocations(locations: List<StorageLocation>) {
        saveStorageLocations(KEY_LIBRARY_LOCATIONS, locations)
    }

    fun loadDownloadLocation(): StorageLocation? =
        loadStorageLocations(KEY_DOWNLOAD_LOCATION).firstOrNull()

    fun saveDownloadLocation(location: StorageLocation?) {
        saveStorageLocations(KEY_DOWNLOAD_LOCATION, listOfNotNull(location))
    }

    private fun loadStorageLocations(key: String): List<StorageLocation> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val uri = item.optString("uri").trim()
                    if (uri.isEmpty()) return@repeat
                    add(
                        StorageLocation(
                            uri = uri,
                            displayName = item.optString("displayName").ifBlank { uri },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveStorageLocations(key: String, locations: List<StorageLocation>) {
        val array = JSONArray()
        locations.forEach { location ->
            array.put(
                JSONObject()
                    .put("uri", location.uri)
                    .put("displayName", location.displayName),
            )
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "browser_preferences"
        const val KEY_FAVORITES = "search_favorites"
        const val KEY_LANGUAGES = "preferred_languages"
        const val KEY_CUSTOM_LANGUAGES = "custom_languages"
        const val KEY_KNOWN_FILTERS = "known_filter_tokens"
        const val KEY_VIEWED_GALLERIES = "viewed_gallery_ids"
        const val KEY_BROWSER_WORKSPACE = "browser_workspace"
        const val KEY_LIBRARY_LOCATIONS = "library_locations"
        const val KEY_DOWNLOAD_LOCATION = "download_location"
    }
}

internal fun browserWorkspaceToJson(workspace: BrowserWorkspace): String = JSONObject()
    .put("version", 1)
    .put("activeTabId", workspace.activeTabId)
    .put("tabs", JSONArray().apply {
        workspace.tabs.forEach { tab ->
            put(JSONObject()
                .put("id", tab.id)
                .put("currentIndex", tab.currentIndex)
                .put("history", JSONArray().apply {
                    tab.history.forEach { page -> put(page.toJson()) }
                }))
        }
    })
    .toString()

internal fun browserWorkspaceFromJson(raw: String): BrowserWorkspace = runCatching {
    val root = JSONObject(raw)
    val tabsJson = root.optJSONArray("tabs") ?: return@runCatching BrowserWorkspace.initial()
    val tabs = buildList {
        repeat(tabsJson.length()) { index ->
            val item = tabsJson.optJSONObject(index) ?: return@repeat
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
            val historyJson = item.optJSONArray("history") ?: return@repeat
            val history = buildList {
                repeat(historyJson.length()) { pageIndex ->
                    historyJson.optJSONObject(pageIndex)?.toBrowserPage()?.let(::add)
                }
            }
            if (history.isEmpty()) return@repeat
            add(BrowserTab(
                id = id,
                history = history,
                currentIndex = item.optInt("currentIndex", 0).coerceIn(history.indices),
            ))
        }
    }
    if (tabs.isEmpty()) return@runCatching BrowserWorkspace.initial()
    val requestedActiveId = root.optString("activeTabId")
    BrowserWorkspace(
        tabs = tabs,
        activeTabId = requestedActiveId.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
    )
}.getOrElse { BrowserWorkspace.initial() }

private fun BrowserPage.toJson(): JSONObject = when (this) {
    is BrowserPage.Search -> JSONObject()
        .put("type", "search")
        .put("key", key)
        .put("query", query)
        .put("preferredLanguages", JSONArray(preferredLanguages.toList()))
        .put("submittedQuery", submittedQuery)
        .put("submittedQueries", JSONArray(submittedQueries))
        .put("currentPage", currentPage)
        .put("scrollIndex", scrollIndex)
        .put("scrollOffset", scrollOffset)
    is BrowserPage.OnlineGallery -> JSONObject()
        .put("type", "online")
        .put("key", key)
        .put("galleryId", galleryId)
        .put("title", title)
    is BrowserPage.LibraryBook -> JSONObject()
        .put("type", "library")
        .put("key", key)
        .put("bookId", bookId)
        .put("title", title)
    is BrowserPage.OnlineReader -> JSONObject()
        .put("type", "onlineReader")
        .put("key", key)
        .put("galleryId", galleryId)
        .put("title", title)
        .put("startPage", startPage)
    is BrowserPage.LibraryReader -> JSONObject()
        .put("type", "libraryReader")
        .put("key", key)
        .put("bookId", bookId)
        .put("title", title)
        .put("startPage", startPage)
}

private fun JSONObject.toBrowserPage(): BrowserPage? {
    val key = optString("key").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
    return when (optString("type")) {
        "search" -> BrowserPage.Search(
            key = key,
            query = optString("query"),
            preferredLanguages = optJSONArray("preferredLanguages").stringSet(),
            submittedQuery = optString("submittedQuery"),
            submittedQueries = optJSONArray("submittedQueries").stringList(),
            currentPage = optInt("currentPage", 0).coerceAtLeast(0),
            scrollIndex = optInt("scrollIndex", 0).coerceAtLeast(0),
            scrollOffset = optInt("scrollOffset", 0).coerceAtLeast(0),
        )
        "online" -> optLong("galleryId").takeIf { it > 0 }?.let { galleryId ->
            BrowserPage.OnlineGallery(galleryId, optString("title"), key)
        }
        "library" -> optString("bookId").takeIf(String::isNotBlank)?.let { bookId ->
            BrowserPage.LibraryBook(bookId, optString("title"), key)
        }
        "onlineReader" -> optLong("galleryId").takeIf { it > 0 }?.let { galleryId ->
            BrowserPage.OnlineReader(
                galleryId = galleryId,
                title = optString("title"),
                startPage = optInt("startPage", 0).coerceAtLeast(0),
                key = key,
            )
        }
        "libraryReader" -> optString("bookId").takeIf(String::isNotBlank)?.let { bookId ->
            BrowserPage.LibraryReader(
                bookId = bookId,
                title = optString("title"),
                startPage = optInt("startPage", 0).coerceAtLeast(0),
                key = key,
            )
        }
        else -> null
    }
}

private fun JSONArray?.stringList(): List<String> = buildList {
    val array = this@stringList ?: return@buildList
    repeat(array.length()) { index ->
        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONArray?.stringSet(): Set<String> = stringList().toCollection(linkedSetOf())
