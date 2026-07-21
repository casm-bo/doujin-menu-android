package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReaderScreenTest {
    @Test
    fun archivePlaceholdersAreNotReadyForReader() {
        val book = bookWithPages(
            LibraryPage("file:///cover.jpg", "001.jpg"),
            LibraryPage("", "002.jpg"),
        )

        assertFalse(book.hasReadablePages())
    }

    @Test
    fun materializedPagesAreReadyForReader() {
        val book = bookWithPages(
            LibraryPage("file:///001.jpg", "001.jpg"),
            LibraryPage("file:///002.jpg", "002.jpg"),
        )

        assertTrue(book.hasReadablePages())
    }

    private fun bookWithPages(vararg pages: LibraryPage) = LibraryBook(
        id = "book",
        title = "Book",
        locationUri = "content://library",
        locationName = "Library",
        folderUri = "content://library/book",
        pages = pages.toList(),
        modifiedAt = 0L,
    )
}
