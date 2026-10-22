package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class NewTabReturnTest {
    @Test fun `only the opening root closes a tab while descendants navigate back`() {
        val origin = BrowserWorkspace.initial()
        val root = origin.openTab(BrowserPage.Search(query = "artist:fixture"))
        val entry = NewTabReturn(root.activeTabId, false, origin.activeTabId, false)
        assertTrue(entry.isAtRoot(root, false))
        assertFalse(entry.isAtRoot(root, true))
        assertFalse(entry.isAtRoot(origin, false))
        val detail = root.pushPage(BrowserPage.OnlineGallery(42, "Fixture"))
        val reader = detail.pushPage(BrowserPage.OnlineReader(42, "Fixture", 0))
        assertFalse(entry.isAtRoot(detail, false))
        assertFalse(entry.isAtRoot(reader, false))
        assertFalse(entry.isAtRoot(reader.goBack(), false))
        assertTrue(entry.isAtRoot(reader.goBack().goBack(), false))
    }

    @Test fun `deep navigation retains its opening root at the history limit`() {
        val rootPage = BrowserPage.Search(query = "artist:fixture")
        val root = BrowserWorkspace.initial(rootPage)
        val deep = (1L..100L).fold(root) { workspace, id -> workspace.pushPage(BrowserPage.OnlineGallery(id, "Fixture")) }
        assertEquals(50, deep.activeTab.history.size)
        var returned = deep
        while (returned.activeTab.canGoBack) returned = returned.goBack()
        assertEquals(rootPage, returned.activeTab.currentPage)
    }

    @Test fun `nested child returns through each opener in order`() {
        val first = BrowserWorkspace.initial().openTab(BrowserPage.Search(query = "artist:fixture"))
            .pushPage(BrowserPage.OnlineGallery(42, "Fixture"))
        val second = first.openTab(BrowserPage.Search(query = "tag:fixture"))
        val returned = second.closeTabReturningTo(second.activeTabId, first.activeTabId)
        assertEquals(first, returned)
        assertTrue(returned.activeTab.currentPage is BrowserPage.OnlineGallery)
    }

    @Test fun `closing child returns to its opener instead of the first group neighbor`() {
        val first = BrowserWorkspace.initial()
        val origin = first.openTab(BrowserPage.Search(query = "origin"))
        val child = origin.openTab(BrowserPage.Search(query = "child"))
        val returned = child.closeTabReturningTo(child.activeTabId, origin.activeTabId)
        assertEquals(origin.activeTabId, returned.activeTabId)
        assertEquals(origin.tabs, returned.tabs)
    }

    @Test fun `removed opener keeps a usable surviving tab`() {
        val original = BrowserWorkspace.initial()
        val child = original.openTab()
        val withoutOrigin = child.closeTab(original.activeTabId)
        val returned = withoutOrigin.closeTabReturningTo(child.activeTabId, original.activeTabId)
        assertEquals(1, returned.tabs.size)
        assertNotEquals(child.activeTabId, returned.activeTabId)
        assertTrue(returned.activeTab.currentPage is BrowserPage.Search)
    }

    @Test fun `gallery fallback stays in gallery workspace`() {
        val original = BrowserWorkspace.initial(BrowserPage.LibraryHome())
        val returned = original.closeTabReturningTo(original.activeTabId, "missing", BrowserPage.LibraryHome())
        assertTrue(returned.activeTab.currentPage is BrowserPage.LibraryHome)
    }
}
