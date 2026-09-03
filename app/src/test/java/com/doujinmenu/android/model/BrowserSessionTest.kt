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

    @Test
    fun historyIsBounded() {
        val workspace = (1..60).fold(BrowserWorkspace.initial()) { current, id ->
            current.pushPage(BrowserPage.OnlineGallery(id.toLong(), "Gallery $id"))
        }

        assertEquals(50, workspace.activeTab.history.size)
        assertEquals(60L, (workspace.activeTab.currentPage as BrowserPage.OnlineGallery).galleryId)
    }

    @Test
    fun searchTabIsReusedRegardlessOfQueryOrder() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:full_color artist:sample"))
        val second = first.openTab(BrowserPage.Search(query = "tag:other"))

        val selected = second.selectSearchTab("ARTIST:sample   tag:full_color")

        assertEquals(first.activeTabId, selected?.activeTabId)
        assertEquals("tag:full_color artist:sample", (selected?.activeTab?.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun missingSearchCreatesStandaloneTabWithoutBackHistory() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:first"))
        val query = "tag:second"
        val selected = workspace.selectSearchTab(query)
            ?: workspace.openTab(BrowserPage.Search(query = query))

        assertEquals(2, selected.tabs.size)
        assertEquals(query, (selected.activeTab.currentPage as BrowserPage.Search).query)
        assertFalse(selected.activeTab.canGoBack)
    }

    @Test
    fun galleryOnlyOpensNewWindowAfterManualTabCreation() {
        val first = BrowserWorkspace.initial(BrowserPage.LibraryHome())
            .pushPage(BrowserPage.LibraryBook("book-a", "Gallery A"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.LibraryHome())
            .pushPage(BrowserPage.LibraryBook("book-b", "Gallery B"))

        assertEquals(2, second.tabs.size)
        assertEquals("book-a", (second.tabs.first { it.id == firstId }.currentPage as BrowserPage.LibraryBook).bookId)
        assertEquals("book-b", (second.activeTab.currentPage as BrowserPage.LibraryBook).bookId)
        assertTrue(second.activeTab.canGoBack)
    }
}
