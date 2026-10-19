package com.doujinmenu.android.security

import com.doujinmenu.android.model.*
import org.junit.Assert.*
import org.junit.Test

class RecentHistoryStoreTest {
    private val entry = RecentHistoryEntry(key = "gallery:123", title = "제목", viewedAt = 42,
        source = HistorySource.DEVICE, galleryId = 123, bookId = "book", syncId = "sync", artist = "작가",
        thumbnail = "content://cover", pageCount = 100, page = 0, action = HistoryAction.READING)

    @Test fun `all history fields round trip including first reading page`() {
        assertEquals(listOf(entry), decodeRecentHistory(encodeRecentHistory(listOf(entry))))
        assertEquals(listOf(entry.copy(page = null)), decodeRecentHistory(encodeRecentHistory(listOf(entry.copy(page = null)))))
    }

    @Test fun `invalid records do not discard healthy neighbors`() {
        val json = encodeRecentHistory(listOf(entry)).removeSuffix("]") + ", {\"key\":\"invalid\"}]"
        assertEquals(listOf(entry), decodeRecentHistory(json))
        assertTrue(decodeRecentHistory("bad json").isEmpty())
        assertTrue(decodeRecentHistory(null).isEmpty())
    }

    @Test fun `entries with missing identity cannot be reopened and are discarded`() {
        assertTrue(decodeRecentHistory("[{\"key\":\"orphan\",\"title\":\"X\",\"viewedAt\":1,\"source\":\"DEVICE\"}]").isEmpty())
    }
}
