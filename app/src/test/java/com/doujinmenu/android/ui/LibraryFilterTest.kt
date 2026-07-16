package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.LibraryMetadata
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
