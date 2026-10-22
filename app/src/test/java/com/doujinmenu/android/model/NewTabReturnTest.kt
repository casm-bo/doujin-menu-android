package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class NewTabReturnTest {
    @Test fun `return window starts on first activation and expires at thirty seconds`() {
        val pending = NewTabReturn("new", false, "origin", false)
        assertFalse(pending.isAvailable(100_000))
        val opened = pending.copy(openedAtMillis = 100_000)
        assertFalse(opened.isAvailable(99_999))
        assertTrue(opened.isAvailable(100_000))
        assertTrue(opened.isAvailable(129_999))
        assertFalse(opened.isAvailable(130_000))
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
