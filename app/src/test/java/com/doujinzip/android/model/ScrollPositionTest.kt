package com.doujinzip.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollPositionTest {
    @Test fun departingGallerySavesIntoItsOwnTabAfterTabSwitch() {
        val home = BrowserPage.LibraryHome(key = "home")
        val workspace = BrowserWorkspace.initial(home).openTab(BrowserPage.LibraryHome(key = "other"))
        val saved = workspace.updateLibraryScroll("home", "series:example", 8, 45)
        assertEquals(workspace.activeTab.currentPage, saved.activeTab.currentPage)
        assertEquals(home.copy(scrollAnchorKey = "series:example", scrollIndex = 8, scrollOffset = 45), saved.tabs.first().currentPage)
    }
    @Test fun `departing search saves into history without changing active detail`() {
        val search = BrowserPage.Search(key = "search", resultSessionId = "session")
        val detail = BrowserPage.OnlineGallery(42, "Detail")
        val tab = BrowserTab(history = listOf(search, detail), currentIndex = 1)
        val workspace = BrowserWorkspace(listOf(tab), tab.id)
        val saved = workspace.updateSearchScroll("search", "session", "gallery:10", 12, 35)
        assertEquals(detail, saved.activeTab.currentPage)
        val restored = saved.activeTab.history.first() as BrowserPage.Search
        assertEquals("gallery:10", restored.scrollAnchorKey)
        assertEquals(12, restored.scrollIndex)
        assertEquals(35, restored.scrollOffset)
    }

    @Test fun `old session disposal cannot overwrite a fresh search or another tab`() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(key = "search", resultSessionId = "new"))
        assertEquals(workspace, workspace.updateSearchScroll("search", "old", "gallery:10", 12, 35))
        assertEquals(workspace, workspace.updateSearchScroll("other", "new", "gallery:10", 12, 35))
    }

    @Test fun `archived search keeps its final offset`() {
        val archived = BrowserTab(history = listOf(BrowserPage.Search(key = "saved", resultSessionId = "session")))
        val workspace = BrowserWorkspace.initial().copy(groups = listOf(BrowserTabGroup(archivedTabs = listOf(archived))))
        val result = workspace.updateSearchScroll("saved", "session", "gallery:3", 5, 20)
        assertEquals(20, (result.groups.single().archivedTabs.single().currentPage as BrowserPage.Search).scrollOffset)
    }

    @Test
    fun anchorFollowsItemWhenOrderChanges() {
        val keys = listOf("book:new", "book:a", "book:b", "book:c")

        assertEquals(2, resolveScrollIndex("book:b", fallbackIndex = 1, itemKeys = keys))
    }

    @Test
    fun missingAnchorUsesSafeFallback() {
        assertEquals(2, resolveScrollIndex("book:removed", fallbackIndex = 9, itemKeys = listOf("a", "b", "c")))
    }
}
