package com.doujinmenu.android.ui

import com.doujinmenu.android.model.*
import org.junit.Assert.*
import org.junit.Test

class BrowserTabsOverviewTest {
    @Test
    fun committingDragOrderRetainsDataLoadedDuringGesture() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .openTab(BrowserPage.Search(query = "two"))
        val draft = original.moveOverviewItem("tab:${original.tabs.first().id}", "tab:${original.tabs.last().id}")
        val latest = original.updateActiveSearch { it.copy(query = "edited during drag", currentPage = 3) }
        val committed = latest.commitTabDragOrder(draft)
        assertEquals(latest.activeTab, committed.activeTab)
        assertEquals(draft.overviewItems.map { it.key }, committed.overviewItems.map { it.key })
    }

    @Test
    fun droppingOnTabCreatesGroupAndDroppingOnGroupKeepsExistingHistory() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .openTab(BrowserPage.Search(query = "two"))
            .openTab(BrowserPage.Search(query = "three"))
        val grouped = original.mergeTabDrop("tab:${original.tabs[0].id}", "tab:${original.tabs[1].id}")
        assertEquals(1, grouped.groups.size)
        assertEquals(2, grouped.overviewItems.size)
        val merged = grouped.mergeTabDrop("tab:${original.tabs[2].id}", "group:${grouped.groups.single().id}")
        assertEquals(1, merged.overviewItems.size)
        assertEquals(original.tabs.map { it.history }, merged.tabs.map { it.history })
        assertEquals(original.activeTabId, merged.activeTabId)
        assertEquals(merged, merged.mergeTabDrop("group:${merged.groups.single().id}", "tab:${original.tabs[0].id}"))
    }

    @Test
    fun cardEdgesReorderAndCenterAllowsGrouping() {
        assertTrue(isTabDropCenter(100f, 140f, 200f, 280f))
        assertFalse(isTabDropCenter(10f, 140f, 200f, 280f))
        assertFalse(isTabDropCenter(100f, 270f, 200f, 280f))
    }

    @Test
    fun toolbarUsesSubmittedQueryRatherThanUnsubmittedEdits() {
        assertEquals("tag:full_color", BrowserPage.Search(query = "draft", submittedQuery = "tag:full_color", currentPage = 1).toolbarTitle)
        assertEquals("language:korean", BrowserPage.Search(query = "draft", submittedQueries = listOf("language:korean"), currentPage = 1).toolbarTitle)
        assertEquals("korean", BrowserPage.Search(preferredLanguages = setOf("korean")).toolbarTitle)
        assertEquals("새 검색", BrowserPage.Search().toolbarTitle)
    }
}
