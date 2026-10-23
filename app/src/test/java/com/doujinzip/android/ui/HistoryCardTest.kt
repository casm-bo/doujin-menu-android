package com.doujinzip.android.ui

import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class HistoryCardTest {
    @Test fun tagsPreferSavedMetadataAndDoNotMutateHistory() {
        val entry = RecentHistoryEntry("gallery:1", "Book", 10, HistorySource.SEARCH, galleryId = 1)
        val gallery = GallerySummary(1, "Book", artists = emptyList(), series = emptyList(), galleryType = null, thumbnailUrl = null, pageCount = 0, language = null, tags = listOf(GalleryTag(type = "tag", name = "cached")))
        val book = LibraryBook("device", "Book", locationUri = "local", locationName = "Local", folderUri = "book", pages = emptyList(), modifiedAt = 0,
            metadata = LibraryMetadata(hitomiId = "1", tags = listOf("saved")))
        val state = MainUiState(recentHistory = listOf(entry), galleryCache = mapOf(1L to gallery))
        assertEquals("cached", historyTags(entry, state).single().info.text)
        assertEquals("saved", historyTags(entry, state.copy(libraryBooks = listOf(book))).single().info.text)
        assertEquals(listOf(entry), state.recentHistory)
        assertTrue(historyTags(entry, MainUiState()).isEmpty())
    }
}
