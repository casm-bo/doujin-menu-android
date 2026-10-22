package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class RecentHistoryTest {
    @Test fun indexedLookupPreservesTheOriginalDeviceAndListOrderRules() {
        val cloud = LibraryBook("pc", "PC", locationUri = "pc", locationName = "PC", folderUri = "pc", pages = emptyList(), modifiedAt = 1,
            isCloud = true, syncId = "shared", metadata = LibraryMetadata(hitomiId = "42"))
        val localA = cloud.copy(id = "a", isCloud = false, syncId = "other")
        val localB = cloud.copy(id = "b", isCloud = false)
        val books = listOf(cloud, localA, localB)
        val index = HistoryBookIndex(books)
        val cases = listOf(event(bookId = "b", syncId = "shared"), event(galleryId = null, syncId = "shared"),
            event(galleryId = null, bookId = "pc"), event(galleryId = 999))
        cases.forEach { assertEquals(it.findBook(books), index.find(it)) }
        assertEquals(localA, index.find(cases.first()))
        assertNull(HistoryBookIndex(emptyList()).find(cases.first()))
    }
    private fun event(time: Long = 1, galleryId: Long? = 42, bookId: String? = null, syncId: String? = null,
        page: Int? = null, completed: Boolean = false) = RecentHistoryEntry(
        key = galleryId?.let { "gallery:$it" } ?: "book:$bookId", title = "Title", viewedAt = time,
        source = if (bookId == null) HistorySource.SEARCH else HistorySource.DEVICE,
        galleryId = galleryId, bookId = bookId, syncId = syncId, page = page, completed = completed,
        action = if (page == null) HistoryAction.DETAIL else if (completed) HistoryAction.COMPLETED else HistoryAction.READING,
    )

    @Test fun `online and saved copies merge while detail preserves reading progress`() {
        val entries = emptyList<RecentHistoryEntry>().recordHistory(event(page = 12))
            .recordHistory(event(time = 2, bookId = "local", syncId = "shared"))
        assertEquals(1, entries.size)
        assertEquals(12, entries.single().page)
        assertEquals("local", entries.single().bookId)
        assertEquals(HistoryAction.DETAIL, entries.single().action)
        assertFalse(entries.single().completed)
    }

    @Test fun `stable sync identity survives book id changes and completion remains sticky`() {
        val original = event(galleryId = null, bookId = "old", syncId = "stable", page = 20, completed = true)
        val entries = listOf(original).recordHistory(event(time = 2, galleryId = null, bookId = "new", syncId = "stable", page = 3))
        assertEquals(1, entries.size)
        assertTrue(entries.single().completed)
        assertEquals(3, entries.single().page)
    }

    @Test fun `adding gallery identity consolidates previously separate records`() {
        val online = event(time = 1)
        val local = event(time = 2, galleryId = null, bookId = "local", syncId = "shared")
        assertEquals(1, listOf(local, online).recordHistory(event(time = 3, bookId = "local", syncId = "shared")).size)
    }

    @Test fun `detail alone never becomes read or creates a reading page`() {
        val entry = emptyList<RecentHistoryEntry>().recordHistory(event()).single()
        assertNull(entry.page)
        assertFalse(entry.completed)
        assertEquals(HistoryAction.DETAIL, entry.action)
    }

    @Test fun `history is newest first and bounded`() {
        val entries = (1L..RECENT_HISTORY_LIMIT + 10L).map { event(time = it, galleryId = it) }
        val result = entries.recordHistory(event(time = 10000, galleryId = 10000))
        assertEquals(RECENT_HISTORY_LIMIT, result.size)
        assertEquals(10000L, result.first().galleryId)
    }

    @Test fun `opening history prefers local copy over cloud and matches sync identity`() {
        val cloud = LibraryBook("pc", "Title", locationUri = "pc", locationName = "PC", folderUri = "pc", pages = emptyList(), modifiedAt = 1, isCloud = true, syncId = "shared")
        val local = cloud.copy(id = "local", isCloud = false)
        val entry = event(galleryId = null, bookId = "gone", syncId = "shared")
        assertEquals(local, entry.findBook(listOf(cloud, local)))
        assertNull(entry.findBook(emptyList()))
    }
}
