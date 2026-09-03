package com.doujinmenu.android.ui

import com.doujinmenu.android.model.DownloadQueueItem
import com.doujinmenu.android.model.DownloadStatus
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueSortTest {
    @Test
    fun newestDownloadsAppearFirst() {
        val items = listOf(
            item(1, "2026-07-15T12:00:00Z"),
            item(3, "2026-07-17T12:00:00Z"),
            item(2, "2026-07-16T12:00:00Z"),
        )

        assertEquals(listOf(3L, 2L, 1L), sortDownloadQueueNewest(items).map(DownloadQueueItem::id))
    }

    @Test
    fun downloadUsesOriginalBookTitleInsteadOfSeriesDisplayName() {
        val book = LibraryBook(
            id = "book-7",
            title = "Series Name",
            originalTitle = "Original Gallery Title",
            locationUri = "local",
            locationName = "Local",
            folderUri = "folder",
            pages = emptyList(),
            modifiedAt = 0,
            metadata = LibraryMetadata(hitomiId = "7"),
        )

        assertEquals("Original Gallery Title", downloadOriginalTitles(emptyList(), listOf(book))[7L])
    }

    private fun item(id: Long, addedAt: String) = DownloadQueueItem(
        id = id,
        galleryId = id,
        galleryTitle = "Gallery $id",
        galleryArtist = null,
        thumbnailUrl = null,
        status = DownloadStatus.PENDING,
        progress = 0,
        totalFiles = 0,
        downloadedFiles = 0,
        downloadSpeed = 0,
        errorMessage = null,
        addedAt = addedAt,
    )
}
