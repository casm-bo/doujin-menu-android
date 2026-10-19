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
        return seek(sessionId, queries, pageCount, loadDetails = false).index
    }

    suspend fun seek(sessionId: String, queries: List<String>, page: Int, loadDetails: Boolean = true): SearchResultWindow {
        require(page > 0)
        val target = searchPage(queries, page)
        require(page == 1 || target.galleryIds.isNotEmpty()) { "${page}페이지에 검색 결과가 없습니다." }
        val first = (page - 1).coerceAtLeast(1)
        var index = SearchResultIndex(sessionId, queries, firstPage = first)
        if (first < page) index = index.appendIds(searchPage(queries, first).galleryIds, true)
        index = index.appendIds(target.galleryIds, target.hasNextPage)
        val resident = linkedMapOf<Int, List<GallerySummary>>()
        // Fetch the requested page first; never walk through pages 1..target.
        if (loadDetails) (first..page).reversed().forEach { number ->
            val galleries = fetchGalleries(requireNotNull(index.pageIds(number)))
            coroutineContext.ensureActive()
            cache.writePage(index, number, galleries)
            resident[number] = galleries
        }
        cache.writeIndex(index)
        return SearchResultWindow(index, resident)
    }

    suspend fun prepend(index: SearchResultIndex): SearchResultWindow {
        require(index.firstPage > 1)
        val page = index.firstPage - 1
        val result = searchPage(index.queries, page)
        val existing = index.ids.toHashSet()
        val ids = result.galleryIds.filter { existing.add(it) }
        val next = index.copy(firstPage = page, pages = listOf(ids) + index.pages)
        val galleries = fetchGalleries(ids)
        coroutineContext.ensureActive()
        cache.writePage(next, page, galleries)
        cache.writeIndex(next)
        return SearchResultWindow(next, mapOf(page to galleries))
    }

    suspend fun append(index: SearchResultIndex): SearchResultWindow {
        val result = searchPage(index.queries, index.lastPage + 1)
        val next = index.appendIds(result.galleryIds, result.hasNextPage)
        val page = next.lastPage
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
                val galleries = cache.readPage(window.index, page) ?: fetchGalleries(requireNotNull(window.index.pageIds(page))).also {
                    coroutineContext.ensureActive()
                    cache.writePage(window.index, page, it)
                }
                coroutineContext.ensureActive()
                onPage(page, galleries)
            }
        }
    }
}
