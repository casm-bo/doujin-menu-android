package com.doujinmenu.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionTest {
    @Test
    fun backRestoresEachSearchSnapshot() {
        val fullColor = BrowserPage.Search(query = "tag:full_color")
        val gallery = BrowserPage.OnlineGallery(1, "Gallery A")
        val multiSeries = BrowserPage.Search(query = "tag:multi_series")
        val workspace = BrowserWorkspace.initial(fullColor)
            .pushPage(gallery)
            .pushPage(multiSeries)

        val detail = workspace.goBack()
        val originalSearch = detail.goBack()

        assertEquals(gallery, detail.activeTab.currentPage)
        assertEquals(fullColor, originalSearch.activeTab.currentPage)
    }

    @Test
    fun tabsKeepIndependentHistory() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
            .pushPage(BrowserPage.OnlineGallery(1, "One"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.Search(query = "second"))
            .pushPage(BrowserPage.OnlineGallery(2, "Two"))

        val restoredFirst = second.selectTab(firstId)

        assertEquals(BrowserPage.OnlineGallery::class, restoredFirst.activeTab.currentPage::class)
        assertEquals(1L, (restoredFirst.activeTab.currentPage as BrowserPage.OnlineGallery).galleryId)
        assertTrue(restoredFirst.activeTab.canGoBack)
        assertEquals("first", (restoredFirst.goBack().activeTab.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun closingActiveTabSelectsNeighborAndKeepsOneTab() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.Search(query = "second"))
        val afterClose = second.closeTab(second.activeTabId)

        assertEquals(firstId, afterClose.activeTabId)
        assertEquals(1, afterClose.tabs.size)

        val reset = afterClose.closeTab(firstId)
        assertEquals(1, reset.tabs.size)
        assertFalse(reset.activeTab.canGoBack)
        assertTrue(reset.activeTab.currentPage is BrowserPage.Search)
    }

    @Test
    fun pushingAfterBackDropsForwardHistory() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .pushPage(BrowserPage.OnlineGallery(1, "One"))
            .pushPage(BrowserPage.Search(query = "two"))
            .goBack()
            .pushPage(BrowserPage.Search(query = "three"))

        assertEquals(3, workspace.activeTab.history.size)
        assertEquals("three", (workspace.activeTab.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun activeTabCanMoveWithoutChangingSelection() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
        val second = first.openTab(BrowserPage.Search(query = "second"))
        val activeId = second.activeTabId

        val moved = second.moveTab(activeId, -1)

        assertEquals(activeId, moved.activeTabId)
        assertEquals(activeId, moved.tabs.first().id)
    }
}
