package com.doujinzip.android.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.security.LibraryPreferenceStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailAndEpisodeListPreserveExplicitFilterAndMainScroll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = LibraryPreferenceStore(context)
        preferences.saveLibraryViewMode("GRID")
        preferences.saveLibraryGridColumns(2)
        val books = (listOf("Episode A", "Episode B", "Standalone") + List(40) { "Other $it" }).mapIndexed { index, title ->
            LibraryBook(title, title, locationUri = "fixture", locationName = "Fixture", folderUri = "", pages = emptyList(), modifiedAt = -index.toLong())
        }
        val baseline = MainUiState(libraryBooks = books, libraryLocations = listOf(StorageLocation("fixture", "Fixture")),
            customSeriesByBookId = mapOf("Episode A" to CustomSeriesAssignment("Fixture series", 0), "Episode B" to CustomSeriesAssignment("Fixture series", 1)))
        var state by mutableStateOf(baseline)
        var seriesPage by mutableStateOf<BrowserPage.LibrarySeries?>(null)
        lateinit var back: OnBackPressedDispatcher
        var filterChanges = 0
        var mainOffset = 0
        compose.setContent { DoujinZipTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            LibraryScreen(state, tabHeader = { query, actions -> Row { Box(Modifier.weight(1f)) { query() }; actions() } },
                contentPadding = PaddingValues(0.dp), onSync = {}, onQueryChange = {}, onToggleFavoritesFilter = {}, onExcludeSeriesChange = {},
                onReadFilterChange = {}, onVisibilityFilterChange = {}, onSortChange = {}, onLocationChange = {}, onRefresh = {}, onOpenBook = {},
                onToggleFavorite = {}, onToggleSeriesFavorite = {}, onToggleRead = {}, onAddFavorites = {}, onMarkRead = {}, onMarkUnread = {},
                onAssignSeries = { _, _ -> }, onAppendSeries = { _, _ -> true }, onSetBooksHidden = { _, _ -> }, onDeleteBooks = {}, onRenameBook = { _, _ -> },
                onRemoveBooksFromSeries = {}, onRenameSeries = { _, _ -> }, onMergeSeries = { _, _ -> }, onDeleteSeries = {}, onAutoCreateSeries = {},
                onSeriesModeChange = { filterChanges++; state = state.copy(librarySeriesMode = it) },
                onSelectedSeriesChange = { seriesPage = it?.let { name -> BrowserPage.LibrarySeries(name) } }, onBackToSearch = {},
                pageKey = "fixture", initialScrollAnchorKey = null, initialScrollIndex = 0, initialScrollOffset = 12,
                onScrollChange = { _, _, offset -> mainOffset = offset }, onSeriesListChange = { seriesPage = seriesPage?.copy(episodeList = it) }, seriesPage = seriesPage)
        } }
        for (seriesOnly in listOf(false, true)) {
            compose.runOnIdle { state = baseline.copy(librarySeriesMode = seriesOnly) }
            compose.onNodeWithText("Fixture series").performClick()
            compose.runOnIdle { assertEquals(seriesOnly, state.librarySeriesMode); assertEquals(0, filterChanges) }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.ui_list)))
            compose.onNodeWithText(context.getString(R.string.ui_list)).performClick()
            compose.onNodeWithText("Episode A").assertExists()
            compose.onNodeWithText("Episode B").assertExists()
            compose.onNodeWithText("Standalone").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.ui_series_details)).assertExists()
            compose.runOnIdle { back.onBackPressed() }
            compose.runOnIdle { assertFalse(seriesPage!!.episodeList); assertEquals(seriesOnly, state.librarySeriesMode) }
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithText("Fixture series").assertExists()
            if (!seriesOnly) compose.onNodeWithText("Standalone").assertExists()
            compose.runOnIdle { assertNull(seriesPage); assertEquals(seriesOnly, state.librarySeriesMode); assertEquals(0, filterChanges) }
            // Opening/closing a series preserves All's offset. Explicitly changing the filter may reset it.
            if (!seriesOnly) assertEquals(12, mainOffset)
        }
        // A reader can open an episode list while the user's home filter still excludes series.
        compose.runOnIdle { state = baseline.copy(libraryExcludeSeries = true); seriesPage = BrowserPage.LibrarySeries("Fixture series", episodeList = true) }
        compose.onNodeWithText("Episode A").assertExists()
        compose.onNodeWithText("Episode B").assertExists()
        compose.onNodeWithText("Standalone").assertDoesNotExist()
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Standalone").assertExists()
        compose.onNodeWithText("Fixture series").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.libraryExcludeSeries); assertFalse(state.librarySeriesMode); assertEquals(0, filterChanges) }
    }
}
