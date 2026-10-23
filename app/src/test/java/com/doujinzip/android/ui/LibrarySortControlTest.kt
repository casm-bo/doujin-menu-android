package com.doujinzip.android.ui

import com.doujinzip.android.model.LibrarySort
import org.junit.Assert.*
import org.junit.Test

class LibrarySortControlTest {
    @Test fun `changing field preserves the chosen direction`() {
        assertEquals(LibrarySort.TITLE_DESC, LibrarySort.TITLE_ASC.withDirection(LibrarySort.NEWEST.isAscending()))
        assertEquals(LibrarySort.ARTIST_ASC, LibrarySort.ARTIST_ASC.withDirection(LibrarySort.OLDEST.isAscending()))
        assertEquals(LibrarySort.NEWEST, LibrarySort.OLDEST.withDirection(LibrarySort.ARTIST_DESC.isAscending()))
    }

    @Test fun `direction toggle is reversible for every field`() {
        LibrarySort.entries.forEach { sort ->
            val reversed = sort.withDirection(!sort.isAscending())
            assertNotEquals(sort, reversed)
            assertEquals(sort, reversed.withDirection(!reversed.isAscending()))
        }
    }
}
