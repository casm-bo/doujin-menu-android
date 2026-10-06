package com.doujinmenu.android.data

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchResultCacheTest {
    private fun gallery(id: Long) = GallerySummary(id, "제목 $id", listOf("artist"), listOf("series"), null,
        listOf(GalleryTag("tag", "full_color", true)), "https://example.invalid/$id.jpg", 42, "korean", "2026-10-05", null)

    @Test fun `pages survive reopening and preserve metadata and unique order`() = runBlocking {
        val directory = Files.createTempDirectory("search-pages").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            val first = listOf(gallery(1), gallery(2))
            val second = listOf(gallery(2), gallery(3))
            val index = SearchResultIndex("tab-session", listOf("tag:full_color")).append(first, true).append(second, false)
            assertTrue(cache.writePage(index, 1, first))
            assertTrue(cache.writePage(index, 2, second))
            assertTrue(cache.writeIndex(index))
            val reopened = SearchResultCache(directory, minimumFreeBytes = 0)
            assertEquals(index, reopened.readIndex(index.sessionId))
            assertEquals(first, reopened.readPage(index, 1))
            assertEquals(listOf(gallery(3)), reopened.readPage(index, 2))
            assertEquals(listOf(1L, 2L, 3L), index.ids)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `missing corrupted and mismatched pages are cache misses`() = runBlocking {
        val directory = Files.createTempDirectory("search-pages").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            val index = SearchResultIndex("session", listOf("a")).append(listOf(gallery(1)), false)
            assertNull(cache.readPage(index, 1))
            cache.writePage(index, 1, listOf(gallery(1)))
            assertNull(cache.readPage(index.copy(pages = listOf(listOf(2L))), 1))
            directory.listFiles()!!.first().writeText("broken")
            assertNull(cache.readPage(index, 1))
        } finally { directory.deleteRecursively() }
    }

    @Test fun `window includes neighbors while slots stay stable across thousands of results`() {
        var index = SearchResultIndex("session", listOf("a"))
        repeat(200) { page -> index = index.append(List(30) { gallery((page * 30 + it + 1).toLong()) }, true) }
        assertEquals(6000, index.ids.size)
        assertEquals(99..101, index.window(2970, 2999))
        assertEquals(1..2, index.window(0, 2))
        assertEquals(199..200, index.window(5999, 5999))
    }

    @Test fun `LRU budget eviction and removed sessions never delete retained pages`() = runBlocking {
        val directory = Files.createTempDirectory("search-pages").toFile()
        try {
            val cache = SearchResultCache(directory, maxBytes = 1200, minimumFreeBytes = 0)
            val first = SearchResultIndex("first", listOf("a")).append(listOf(gallery(1)), false)
            val second = first.copy(sessionId = "second")
            cache.writePage(first, 1, listOf(gallery(1)))
            cache.writePage(second, 1, listOf(gallery(1)))
            cache.retainSessions(setOf(second.sessionId))
            assertNull(cache.readPage(first, 1))
            assertNotNull(cache.readPage(second, 1))
            repeat(10) { cache.writePage(first.copy(sessionId = "other-$it"), 1, listOf(gallery(1))) }
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 1200)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `unavailable storage skips writes`() = runBlocking {
        val directory = Files.createTempDirectory("search-pages").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = Long.MAX_VALUE)
            assertFalse(cache.writeIndex(SearchResultIndex("session", listOf("a"))))
            assertTrue(directory.listFiles().isNullOrEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun `reading an older page protects it from LRU eviction`() = runBlocking {
        val directory = Files.createTempDirectory("search-pages").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            val index = SearchResultIndex("session", listOf("a"))
                .append(listOf(gallery(1)), true).append(listOf(gallery(2)), true).append(listOf(gallery(3)), false)
            cache.writePage(index, 1, listOf(gallery(1)))
            cache.writePage(index, 2, listOf(gallery(2)))
            directory.listFiles()!!.forEach { it.setLastModified(if ("page-1" in it.name) 10 else 20) }
            val budget = directory.listFiles()!!.sumOf { it.length() } + 1
            cache.readPage(index, 1)
            val limited = SearchResultCache(directory, maxBytes = budget, minimumFreeBytes = 0)
            limited.writePage(index, 3, listOf(gallery(3)))
            assertNotNull(limited.readPage(index, 1))
            assertNull(limited.readPage(index, 2))
            assertNotNull(limited.readPage(index, 3))
        } finally { directory.deleteRecursively() }
    }
}
