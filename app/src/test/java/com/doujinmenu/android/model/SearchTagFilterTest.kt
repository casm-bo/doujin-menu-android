package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class SearchTagFilterTest {
    @Test fun exclusionsRemainEvenWhenTheSameTagIsExplicitlyRequested() {
        val query = applySearchTagFilters("tag:yaoi, artist:sample sort:oldest", setOf(SearchTagFilter.BL))
        assertEquals("tag:yaoi artist:sample sort:oldest -tag:yaoi", query)
        assertEquals(query, applySearchTagFilters(query, setOf(SearchTagFilter.BL)))
    }
    @Test fun allPresetsApplyToEveryLanguageAndIdLookups() {
        val filters = SearchTagFilter.entries.toSet()
        for (base in queriesWithPreferredLanguages("id:123", setOf("korean", "english"))) {
            val query = applySearchTagFilters(base, filters)
            assertTrue(query.contains("id:123"))
            filters.flatMap { it.tags }.forEach { assertTrue(query.contains("-$it")) }
        }
        assertEquals("tag:sample", applySearchTagFilters("tag:sample", emptySet()))
    }
    @Test fun changingFiltersInvalidatesResultsAcrossHistoryAndArchivedTabs() {
        val search = BrowserPage.Search(query="tag:sample", submittedQueries=listOf("tag:sample"), currentPage=7,
            resultSessionId="synthetic-session", resultIds=listOf(1), scrollIndex=50)
        val tab = BrowserTab(history=listOf(search))
        val workspace = BrowserWorkspace(listOf(tab), tab.id,
            listOf(BrowserTabGroup(name="Synthetic group", archivedTabs=listOf(tab.copy(id="archived")))))
        val cleared = workspace.invalidateSearchResults()
        val pages = (cleared.tabs + cleared.groups.flatMap { it.archivedTabs }).flatMap { it.history }.filterIsInstance<BrowserPage.Search>()
        assertEquals(2, pages.size)
        pages.forEach { assertEquals(search.query,it.query); assertNull(it.resultSessionId); assertTrue(it.submittedQueries.isEmpty()); assertTrue(it.resultIds.isEmpty()); assertEquals(1,it.currentPage); assertEquals(0,it.scrollIndex) }
    }
}
