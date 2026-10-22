package com.doujinmenu.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.model.*
import com.doujinmenu.android.security.LibraryPreferenceStore
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryScrollRestoreTest {
    @get:Rule val compose = createComposeRule()
    @Test fun delayedListDataRestoresAndKeepsSavingScroll() = checkRestore("LIST")
    @Test fun delayedGridDataRestoresAndKeepsSavingScroll() = checkRestore("GRID")

    private fun checkRestore(mode: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = LibraryPreferenceStore(context)
        val previousMode = preferences.loadLibraryViewMode()
        val previousColumns = preferences.loadLibraryGridColumns()
        val books = List(60) { LibraryBook("fixture-$it", "Fixture $it", locationUri = "", locationName = "", folderUri = "",
            pages = emptyList(), modifiedAt = (60 - it).toLong()) }
        var state by mutableStateOf(MainUiState(isLibraryScanning = true,
            libraryLocations = listOf(StorageLocation("fixture", "Fixture"))))
        val saved = java.util.concurrent.CopyOnWriteArrayList<Triple<String?, Int, Int>>()
        try {
            preferences.saveLibraryViewMode(mode); preferences.saveLibraryGridColumns(2)
            compose.setContent { DoujinMenuTheme {
                LibraryScreen(state = state, tabHeader = { query, _ -> query() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    onSync = {}, onQueryChange = {}, onToggleFavoritesFilter = {}, onExcludeSeriesChange = {},
                    onReadFilterChange = {}, onVisibilityFilterChange = {}, onSortChange = {}, onLocationChange = {}, onRefresh = {},
                    onOpenBook = {}, onToggleFavorite = {}, onToggleSeriesFavorite = {}, onToggleRead = {}, onAddFavorites = {},
                    onMarkRead = {}, onMarkUnread = {}, onAssignSeries = { _, _ -> }, onAppendSeries = { _, _ -> true },
                    onSetBooksHidden = { _, _ -> }, onDeleteBooks = {}, onRenameBook = { _, _ -> }, onRemoveBooksFromSeries = {},
                    onRenameSeries = { _, _ -> }, onMergeSeries = { _, _ -> }, onDeleteSeries = {}, onAutoCreateSeries = {},
                    onSeriesModeChange = {}, onSelectedSeriesChange = {}, onBackToSearch = {}, pageKey = "late-data",
                    initialScrollAnchorKey = "book:fixture-20", initialScrollIndex = 20, initialScrollOffset = 37,
                    onScrollChange = { anchor, index, offset -> saved += Triple(anchor, index, offset) })
            } }
            compose.runOnIdle { state = state.copy(libraryBooks = books, isLibraryScanning = false) }
            try {
                compose.waitUntil(3_000) { saved.any { it == Triple("book:fixture-20", 20, 37) } }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError("Restored positions: $saved\n${compose.onRoot().printToString()}", error)
            }
            compose.onNode(hasScrollAction()).performScrollToIndex(30)
            compose.waitUntil(3_000) { saved.lastOrNull()?.second == 30 }
            compose.runOnIdle { state = state.copy(libraryQuery = "title:Fixture") }
            compose.waitUntil(3_000) { saved.lastOrNull()?.second == 0 }
            assertEquals("book:fixture-0", saved.last().first)
        } finally {
            preferences.saveLibraryViewMode(previousMode); preferences.saveLibraryGridColumns(previousColumns)
        }
    }
}
