package com.doujinmenu.android.data

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.SearchResult
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchResultPagerTest {
    private fun gallery(id: Long) = GallerySummary(id, "Title $id", emptyList(), emptyList(), null,
        emptyList(), null, 1, null)
    private fun ids(page: Int) = List(30) { (page * 30 + it).toLong() }

    @Test fun `six thousand slots retain only nearby metadata and disk restores avoid network`() = runBlocking {
        val directory = Files.createTempDirectory("search-window").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            var networkSearches = 0
            var networkDetails = 0
            val pager = SearchResultPager(cache, { _, page ->
                networkSearches++
                SearchResult(ids(page), page < 200)
            }, { requested -> networkDetails += requested.size; requested.map(::gallery) })
            var window = SearchResultWindow(SearchResultIndex("session", listOf("a", "b")))
            repeat(200) {
                val appended = pager.append(window.index)
                window = appended.copy(residentPages = window.residentPages + appended.residentPages)
                    .retainPages((appended.index.pages.size - 2).coerceAtLeast(1)..appended.index.pages.size)
                assertTrue(window.galleries.size <= 90)
            }
            assertEquals(6000, window.index.ids.size)
            val restored = pager.restore("session", listOf("a", "b"), 200)
            window = SearchResultWindow(restored)
            pager.loadWindow(window, restored.window(2970, 2999), 100) { page, galleries ->
                window = window.copy(residentPages = window.residentPages + (page to galleries))
            }
            assertEquals(setOf(99, 100, 101), window.residentPages.keys)
            assertEquals(90, window.galleries.size)
            assertEquals(200, networkSearches)
            assertEquals(6000, networkDetails)
            assertEquals(restored.ids[2970], window.residentPages.getValue(100).first().id)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `missing page reloads only its original IDs without repeating search`() = runBlocking {
        val directory = Files.createTempDirectory("search-window").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            val index = SearchResultIndex("session", listOf("a"))
                .appendIds(ids(1), true).appendIds(ids(2), true).appendIds(ids(3), false)
            cache.writeIndex(index)
            cache.writePage(index, 1, ids(1).map(::gallery))
            cache.writePage(index, 3, ids(3).map(::gallery))
            val requestedIds = mutableListOf<List<Long>>()
            val pager = SearchResultPager(cache, { _, _ -> error("cached index must avoid search") }, {
                requestedIds += it; it.map(::gallery)
            })
            val loadedPages = mutableMapOf<Int, List<GallerySummary>>()
            pager.loadWindow(SearchResultWindow(index), 1..3, 2) { page, galleries -> loadedPages[page] = galleries }
            assertEquals(listOf(ids(2)), requestedIds)
            assertEquals(setOf(1, 2, 3), loadedPages.keys)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `deleted index rebuilds IDs without loading all previous details`() = runBlocking {
        val directory = Files.createTempDirectory("search-window").toFile()
        try {
            val pager = SearchResultPager(SearchResultCache(directory, minimumFreeBytes = 0), { _, page ->
                SearchResult(ids(page), true)
            }, { error("restore index must not fetch gallery details") })
            val index = pager.restore("missing", listOf("saved-query"), 100)
            assertEquals(100, index.pages.size)
            assertEquals(3000, index.ids.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `new query cannot reuse a previous session index`() = runBlocking {
        val directory = Files.createTempDirectory("search-window").toFile()
        try {
            val cache = SearchResultCache(directory, minimumFreeBytes = 0)
            cache.writeIndex(SearchResultIndex("session", listOf("old")).appendIds(listOf(1), false))
            val pager = SearchResultPager(cache, { queries, _ ->
                assertEquals(listOf("new"), queries); SearchResult(listOf(2), false)
            }, { error("no details during index restore") })
            assertEquals(listOf(2L), pager.restore("session", listOf("new"), 1).ids)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `cancelled viewport request never publishes late metadata`() = runBlocking {
        val directory = Files.createTempDirectory("search-window").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val pager = SearchResultPager(SearchResultCache(directory, minimumFreeBytes = 0), { _, _ -> error("no search") }, {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                it.map(::gallery)
            })
            val window = SearchResultWindow(SearchResultIndex("session", listOf("a")).appendIds(ids(1), false))
            var published = false
            val job = launch { pager.loadWindow(window, 1..1, 1) { _, _ -> published = true } }
            started.await()
            job.cancel()
            release.complete(Unit)
            job.join()
            assertFalse(published)
        } finally { directory.deleteRecursively() }
    }
}
