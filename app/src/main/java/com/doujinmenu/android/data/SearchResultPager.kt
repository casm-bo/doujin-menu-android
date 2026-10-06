package com.doujinmenu.android.data

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.SearchResult
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class SearchResultWindow(
    val index: SearchResultIndex,
    val residentPages: Map<Int, List<GallerySummary>> = emptyMap(),
) {
    val galleries: List<GallerySummary> get() = residentPages.toSortedMap().values.flatten()
    fun retainPages(pages: IntRange): SearchResultWindow = copy(residentPages = residentPages.filterKeys { it in pages })
}

class SearchResultPager(
    private val cache: SearchResultCache,
    private val searchPage: suspend (List<String>, Int) -> SearchResult,
    private val fetchGalleries: suspend (List<Long>) -> List<GallerySummary>,
) {
    suspend fun restore(sessionId: String, queries: List<String>, pageCount: Int): SearchResultIndex {
        cache.readIndex(sessionId)?.takeIf { it.queries == queries }?.let { return it }
        var index = SearchResultIndex(sessionId, queries)
        // Only rebuild lightweight IDs when the OS or the cache budget removed the index.
        repeat(pageCount) { page ->
            coroutineContext.ensureActive()
            val result = searchPage(queries, page + 1)
            index = index.appendIds(result.galleryIds, result.hasNextPage)
            if (!result.hasNextPage) {
                cache.writeIndex(index)
                return index
            }
        }
        cache.writeIndex(index)
        return index
    }

    suspend fun append(index: SearchResultIndex): SearchResultWindow {
        val result = searchPage(index.queries, index.pages.size + 1)
        val next = index.appendIds(result.galleryIds, result.hasNextPage)
        val page = next.pages.size
        val galleries = fetchGalleries(next.pages.last())
        coroutineContext.ensureActive()
        cache.writePage(next, page, galleries)
        cache.writeIndex(next)
        return SearchResultWindow(next, mapOf(page to galleries))
    }

    suspend fun loadWindow(
        window: SearchResultWindow,
        pages: IntRange,
        firstVisiblePage: Int,
        onPage: (Int, List<GallerySummary>) -> Unit,
    ) {
        pages.sortedBy { kotlin.math.abs(it - firstVisiblePage) }.forEach { page ->
            coroutineContext.ensureActive()
            if (page !in window.residentPages) {
                val galleries = cache.readPage(window.index, page) ?: fetchGalleries(window.index.pages[page - 1]).also {
                    coroutineContext.ensureActive()
                    cache.writePage(window.index, page, it)
                }
                coroutineContext.ensureActive()
                onPage(page, galleries)
            }
        }
    }
}
