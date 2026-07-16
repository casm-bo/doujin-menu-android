package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.LibraryMetadata
import com.doujinmenu.android.model.CustomSeriesAssignment
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val books = listOf(
        book("a", "Zeta", "one", 10),
        book("b", "Alpha", "two", 30),
        book("c", "Beta", "one", 20),
    )

    @Test
    fun combinesLocationFavoriteAndReadFilters() {
        val state = MainUiState(
            libraryBooks = books,
            selectedLibraryLocationUri = "one",
            libraryFavoritesOnly = true,
            libraryFavoriteIds = setOf("a", "c"),
            libraryReadFilter = LibraryReadFilter.UNREAD,
            libraryReadIds = setOf("a"),
        )

        assertEquals(listOf("c"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun queryIsCaseInsensitiveAndNewestSortWorks() {
        val state = MainUiState(
            libraryBooks = books,
            libraryQuery = "A",
            librarySort = LibrarySort.NEWEST,
        )

        assertEquals(listOf("b", "c", "a"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun querySupportsBrowserStyleFieldsAndQuotedValues() {
        val tagged = books[1].copy(
            metadata = LibraryMetadata(
                artists = listOf("Sample Artist"),
                tags = listOf("full color"),
                series = listOf("Example Series"),
            ),
        )
        val state = MainUiState(
            libraryBooks = books + tagged.copy(id = "match"),
            libraryQuery = "artist:\"sample artist\" tag:\"full color\" -series:other",
        )

        assertEquals(listOf("match"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun multipleLibraryLocationsCanBeSelectedTogether() {
        val state = MainUiState(
            libraryBooks = books,
            selectedLibraryLocationUris = setOf("one"),
        )

        assertEquals(listOf("c", "a"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun queryIncludesCustomSeriesTitle() {
        val state = MainUiState(
            libraryBooks = books,
            libraryQuery = "series:내시리즈",
            customSeriesByBookId = mapOf("b" to CustomSeriesAssignment("내시리즈", 0)),
        )

        assertEquals(listOf("b"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun titleDescendingSortIsApplied() {
        val state = MainUiState(libraryBooks = books, librarySort = LibrarySort.TITLE_DESC)

        assertEquals(listOf("a", "c", "b"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun seriesFavoriteIsIndependentFromEpisodeFavorites() {
        val state = MainUiState(
            libraryBooks = books,
            libraryFavoritesOnly = true,
            libraryFavoriteIds = emptySet(),
            libraryFavoriteSeriesNames = setOf("Series 1"),
            customSeriesByBookId = mapOf(
                "a" to CustomSeriesAssignment("Series 1", 0),
                "b" to CustomSeriesAssignment("Series 1", 1),
            ),
        )

        assertEquals(listOf("b", "a"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    @Test
    fun searchMatchesMetadataFromAnySeriesEpisode() {
        val seriesBooks = listOf(
            books[0].copy(metadata = LibraryMetadata(artists = listOf("ABC"))),
            books[1].copy(metadata = LibraryMetadata(artists = listOf("ABC", "bcd"))),
        )
        val state = MainUiState(
            libraryBooks = seriesBooks,
            libraryQuery = "artist:bcd",
            customSeriesByBookId = mapOf(
                "a" to CustomSeriesAssignment("Series 1", 0),
                "b" to CustomSeriesAssignment("Series 1", 1),
            ),
        )

        assertEquals(listOf("b"), visibleLibraryBooks(state).map(LibraryBook::id))
    }

    private fun book(id: String, title: String, location: String, modifiedAt: Long) = LibraryBook(
        id = id,
        title = title,
        locationUri = location,
        locationName = location,
        folderUri = id,
        pages = listOf(LibraryPage("content://page/$id", "1.jpg")),
        modifiedAt = modifiedAt,
    )
}
