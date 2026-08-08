package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.LibraryVisibilityFilter
import com.doujinmenu.android.model.LibraryMetadata
import com.doujinmenu.android.model.CustomSeriesAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryFilterTest {
    @Test
    fun `default sort is newest modified first`() {
        assertEquals(listOf("b", "c", "a"), visibleLibraryBooks(MainUiState(libraryBooks = books)).map(LibraryBook::id))
    }

    @Test
    fun `filter signature changes only for list conditions`() {
        val base = MainUiState()

        assertNotEquals(libraryFilterSignature(base), libraryFilterSignature(base.copy(libraryQuery = "tag:test")))
        assertNotEquals(libraryFilterSignature(base), libraryFilterSignature(base.copy(libraryFavoritesOnly = true)))
        assertNotEquals(libraryFilterSignature(base), libraryFilterSignature(base.copy(librarySort = LibrarySort.TITLE_ASC)))
        assertEquals(libraryFilterSignature(base), libraryFilterSignature(base.copy(message = "unrelated")))
    }

    private val books = listOf(
        book("a", "Zeta", "one", 10),
        book("b", "Alpha", "two", 30),
        book("c", "Beta", "one", 20),
    )

    @Test
    fun combinesLocationFavoriteAndReadFilters() {
        val state = MainUiState(
            libraryBooks = books,
            selectedLibraryLocationUris = setOf("one"),
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
    fun hiddenBooksCanBeViewedWithVisibilityFilter() {
        val visible = MainUiState(
            libraryBooks = books,
            libraryHiddenIds = setOf("b"),
        )
        val hidden = visible.copy(libraryVisibilityFilter = LibraryVisibilityFilter.HIDDEN)
        val all = visible.copy(libraryVisibilityFilter = LibraryVisibilityFilter.ALL)

        assertEquals(listOf("c", "a"), visibleLibraryBooks(visible).map(LibraryBook::id))
        assertEquals(listOf("b"), visibleLibraryBooks(hidden).map(LibraryBook::id))
        assertEquals(listOf("b", "c", "a"), visibleLibraryBooks(all).map(LibraryBook::id))
    }

    @Test
    fun artistSortUsesTitleAsSecondaryKeyAndPutsUnknownArtistsLast() {
        val artistBooks = listOf(
            book("z2", "Beta", "one", 10).copy(metadata = LibraryMetadata(artists = listOf("Zed"))),
            book("a2", "Zulu", "one", 10).copy(metadata = LibraryMetadata(artists = listOf("Alpha"))),
            book("unknown", "Aardvark", "one", 10),
            book("z1", "Alpha", "one", 10).copy(metadata = LibraryMetadata(artists = listOf("zed"))),
        )

        val ascending = MainUiState(libraryBooks = artistBooks, librarySort = LibrarySort.ARTIST_ASC)
        val descending = MainUiState(libraryBooks = artistBooks, librarySort = LibrarySort.ARTIST_DESC)

        assertEquals(listOf("a2", "z1", "z2", "unknown"), visibleLibraryBooks(ascending).map(LibraryBook::id))
        assertEquals(listOf("z1", "z2", "a2", "unknown"), visibleLibraryBooks(descending).map(LibraryBook::id))
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
