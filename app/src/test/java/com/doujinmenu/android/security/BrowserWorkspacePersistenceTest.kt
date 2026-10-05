package com.doujinmenu.android.security

import com.doujinmenu.android.model.BrowserPage
import com.doujinmenu.android.model.BrowserWorkspace
import com.doujinmenu.android.model.openTab
import com.doujinmenu.android.model.openGalleryInBackground
import com.doujinmenu.android.model.pushPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWorkspacePersistenceTest {
    @Test
    fun roundTripPreservesMoreThanTwentyTabsInBothWorkspaces() {
        val search = (1..60).fold(BrowserWorkspace.initial()) { workspace, id ->
            workspace.openTab(BrowserPage.Search(query = "search $id"))
        }
        val gallery = (1..60).fold(BrowserWorkspace.initial(BrowserPage.LibraryHome())) { workspace, id ->
            workspace.openTab(BrowserPage.LibraryHome())
                .pushPage(BrowserPage.LibraryBook("book-$id", "Book $id"))
        }

        assertEquals(search, browserWorkspaceFromJson(browserWorkspaceToJson(search)).searchWorkspace())
        assertEquals(gallery, browserWorkspaceFromJson(browserWorkspaceToJson(gallery)).galleryWorkspace())
    }

    @Test
    fun backgroundGalleryRoundTripKeepsSearchSelected() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "example", scrollIndex = 12, scrollOffset = 40))
        val opened = original.openGalleryInBackground(42, "Example")
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(opened.workspace))
        assertEquals(original.activeTabId, restored.activeTabId)
        assertEquals(original.activeTab.currentPage, restored.activeTab.currentPage)
        assertEquals(opened.tabId, restored.tabs.last().id)
        assertEquals(42L, (restored.tabs.last().currentPage as BrowserPage.OnlineGallery).galleryId)
    }

    @Test
    fun roundTripPreservesTabsAndHistoryWithoutHeavyResults() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(
            query = "tag:full_color",
            preferredLanguages = setOf("korean"),
            resultIds = listOf(1, 2, 3),
            currentPage = 2,
            scrollAnchorKey = "gallery:2",
            scrollIndex = 12,
            scrollOffset = 40,
        ))
            .pushPage(BrowserPage.OnlineGallery(1, "Gallery A"))
            .openTab(BrowserPage.LibraryBook("file:book", "Downloaded"))

        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))

        assertEquals(2, restored.tabs.size)
        assertEquals(workspace.activeTabId, restored.activeTabId)
        assertEquals(BrowserPage.LibraryBook("file:book", "Downloaded", restored.activeTab.currentPage.key), restored.activeTab.currentPage)
        val search = restored.tabs.first().history.first() as BrowserPage.Search
        assertEquals("tag:full_color", search.query)
        assertEquals(setOf("korean"), search.preferredLanguages)
        assertEquals(2, search.currentPage)
        assertEquals(12, search.scrollIndex)
        assertEquals("gallery:2", search.scrollAnchorKey)
        assertTrue(search.resultIds.isEmpty())
        assertTrue(search.results.isEmpty())
    }


    @Test
    fun roundTripPreservesGalleryScrollAnchor() {
        val workspace = BrowserWorkspace.initial(BrowserPage.LibraryHome(
            scrollAnchorKey = "book:20",
            scrollIndex = 19,
            scrollOffset = 24,
        ))

        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))
        val home = restored.activeTab.currentPage as BrowserPage.LibraryHome

        assertEquals("book:20", home.scrollAnchorKey)
        assertEquals(19, home.scrollIndex)
        assertEquals(24, home.scrollOffset)
    }

    @Test
    fun corruptDataFallsBackToOneSearchTab() {
        val restored = browserWorkspaceFromJson("not json")

        assertEquals(1, restored.tabs.size)
        assertTrue(restored.activeTab.currentPage is BrowserPage.Search)
    }

    @Test
    fun legacyMixedWorkspaceSplitsSearchAndGalleryHistory() {
        val mixed = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:first"))
            .pushPage(BrowserPage.OnlineGallery(1, "Online"))
            .openTab(BrowserPage.LibraryBook("file:book", "Downloaded"))

        val search = mixed.searchWorkspace()
        val gallery = mixed.galleryWorkspace()

        assertEquals(1, search.tabs.size)
        assertTrue(search.activeTab.currentPage is BrowserPage.OnlineGallery)
        assertEquals(1, gallery.tabs.size)
        assertTrue(gallery.activeTab.history.first() is BrowserPage.LibraryHome)
        assertTrue(gallery.activeTab.currentPage is BrowserPage.LibraryBook)
    }
}
