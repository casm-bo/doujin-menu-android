package com.doujinmenu.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScannerTest {
    @Test
    fun naturalKeySortsPageNumbersNumerically() {
        val names = listOf("10.jpg", "2.jpg", "001.jpg", "page3.jpg")

        assertEquals(
            listOf("001.jpg", "2.jpg", "10.jpg", "page3.jpg"),
            names.sortedBy(LibraryScanner::naturalSortKey),
        )
    }
}
