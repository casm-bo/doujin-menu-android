package com.doujinzip.android.security

import com.doujinzip.android.model.updateLibraryScroll

import com.doujinzip.android.model.BrowserPage
import com.doujinzip.android.model.BrowserWorkspace
import com.doujinzip.android.model.NewTabReturn
import com.doujinzip.android.model.openTab
import com.doujinzip.android.model.openLibraryReader
import com.doujinzip.android.model.openGalleryInBackground
import com.doujinzip.android.model.createGroup
import com.doujinzip.android.model.archiveGroup
import com.doujinzip.android.model.moveOverviewItem
import com.doujinzip.android.model.overviewItems
import com.doujinzip.android.model.pushPage
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.model.updateReaderPosition
import com.doujinzip.android.model.updatePreviewPosition
import com.doujinzip.android.model.goBack
import com.doujinzip.android.model.renameSeriesPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWorkspacePersistenceTest {
    @Test fun automaticTabRootsAndOpenersSurviveRestoreAndArchiving() {
        listOf(BrowserPage.Search(query = "artist:fixture"), BrowserPage.LibraryBook("fixture", "Fixture")).forEach { root ->
            val gallery = root is BrowserPage.LibraryBook
            val opened = BrowserWorkspace.initial(root)
            val returnTo = NewTabReturn(opened.activeTabId, gallery, "fixture-opener", !gallery)
            val workspace = opened.copy(tabs = opened.tabs.map { it.copy(returnTo = returnTo) })
            fun restore(saved: BrowserWorkspace): BrowserWorkspace = browserWorkspaceFromJson(browserWorkspaceToJson(saved))
                .let { if (gallery) it.galleryWorkspace() else it.searchWorkspace() }
            val restored = restore(workspace)
            assertEquals(workspace, restored)
            assertTrue(returnTo.isAtRoot(restored, gallery))
            val archived = workspace.createGroup(setOf(workspace.activeTabId), "Fixture")
                .let { it.archiveGroup(it.groups.single().id, if (gallery) BrowserPage.LibraryHome() else BrowserPage.Search()) }
            assertEquals(returnTo, restore(archived).groups.single().archivedTabs.single().returnTo)
        }
    }

    @Test fun automaticGalleryDetailReturnsToItsSavedRootAfterReaderRestore() {
        val root = BrowserPage.LibraryBook("fixture", "Fixture")
        val opened = BrowserWorkspace.initial(root)
        val returnTo = NewTabReturn(opened.activeTabId, true, "fixture-search", false)
        val workspace = opened.copy(tabs = opened.tabs.map { it.copy(returnTo = returnTo) })
            .openLibraryReader(BrowserPage.LibraryReader("fixture", "Fixture", 0))
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace)).galleryWorkspace()
        assertEquals(workspace, restored)
        assertTrue(returnTo.isAtRoot(restored.goBack(), true))
        assertEquals(root, restored.goBack().activeTab.currentPage)
    }

    @Test fun seriesPagesKeepIndependentDetailsEpisodesAndNamesAfterRestore() {
        val first = BrowserPage.LibrarySeries("First / series", episodeList = true, previewPosition = ReaderPosition(8, 19), episodePosition = ReaderPosition(12, 27))
        val second = BrowserPage.LibrarySeries("Second")
        val workspace = BrowserWorkspace.initial(BrowserPage.LibraryHome()).pushPage(first).openTab(second)
            .renameSeriesPages("First / series", "Renamed")
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace)).galleryWorkspace()
        assertEquals(first.copy(name = "Renamed"), restored.tabs.first().currentPage)
        assertEquals(second, restored.activeTab.currentPage)
    }
    @Test fun galleryQuerySurvivesScrollAndProcessRestorePerTab() {
        val page = BrowserPage.LibraryHome(query = "tag:hi")
        val workspace = BrowserWorkspace.initial(page).openTab(BrowserPage.LibraryHome(query = "artist:other"))
            .updateLibraryScroll(page.key, "book:20", 19, 30)
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))
        assertEquals("tag:hi", (restored.tabs.first().currentPage as BrowserPage.LibraryHome).query)
        assertEquals(19, (restored.tabs.first().currentPage as BrowserPage.LibraryHome).scrollIndex)
        assertEquals("artist:other", (restored.activeTab.currentPage as BrowserPage.LibraryHome).query)
    }
    @Test fun finalReaderAndPreviewPositionsFollowArchivedTabs() {
        val detail = BrowserPage.LibraryBook("pc:1", "Book")
        val reader = BrowserPage.LibraryReader("pc:1", "Book", 3)
        val workspace = BrowserWorkspace.initial(detail).pushPage(reader)
            .let { it.createGroup(setOf(it.activeTabId), "Archive") }
            .let { it.archiveGroup(it.groups.single().id, BrowserPage.LibraryHome()) }
            .updateReaderPosition(reader.key, ReaderPosition(49, 5000))
            .updatePreviewPosition(detail.key, ReaderPosition(40, 20))
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))
        val history = restored.groups.single().archivedTabs.single().history
        assertEquals(ReaderPosition(49, 5000), (history.last() as BrowserPage.LibraryReader).position)
        assertEquals(ReaderPosition(40, 20), (history.first() as BrowserPage.LibraryBook).previewPosition)
    }
    @Test fun previewAnchorSurvivesReaderNavigationAndProcessRestore() {
        listOf(BrowserPage.OnlineGallery(1, "Book"), BrowserPage.LibraryBook("pc:1", "Book")).forEach { detail ->
            val original = BrowserWorkspace.initial(detail)
            val saved = original.pushPage(BrowserPage.OnlineReader(1, "Book", 49))
                .updatePreviewPosition(detail.key, ReaderPosition(43, 80))
            val restored = browserWorkspaceFromJson(browserWorkspaceToJson(saved)).goBack().activeTab.currentPage
            val position = when (restored) {
                is BrowserPage.OnlineGallery -> restored.previewPosition
                is BrowserPage.LibraryBook -> restored.previewPosition
                else -> error("Detail lost")
            }
            assertEquals(ReaderPosition(43, 80), position)
            assertEquals(original, original.updatePreviewPosition("another-tab", ReaderPosition(1)))
        }
    }
    @Test fun readerSessionRestoresLatestPositionWithoutChangingExplicitEntryPage() {
        val reader = BrowserPage.OnlineReader(42, "Book", 3)
        val original = BrowserWorkspace.initial(reader)
        val advanced = original.updateReaderPosition(reader.key, ReaderPosition(49, 125))
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(advanced))
        assertEquals(reader.copy(position = ReaderPosition(49, 125)), restored.activeTab.currentPage)
        val next = restored.pushPage(BrowserPage.OnlineReader(42, "Book", 8))
        assertEquals(null, (next.activeTab.currentPage as BrowserPage.OnlineReader).position)
        assertEquals(8, (next.activeTab.currentPage as BrowserPage.OnlineReader).startPage)
        assertEquals(original, original.updateReaderPosition("stale-key", ReaderPosition(0)))
    }

    @Test fun localReaderPositionIsIsolatedAcrossTabs() {
        val reader = BrowserPage.LibraryReader("local", "Book", 3)
        val other = BrowserPage.LibraryReader("local", "Book", 8)
        val workspace = BrowserWorkspace.initial(reader).openTab(other)
            .updateReaderPosition(reader.key, ReaderPosition(49))
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))
        assertEquals(ReaderPosition(49), (restored.tabs.first().currentPage as BrowserPage.LibraryReader).position)
        assertEquals(null, (restored.activeTab.currentPage as BrowserPage.LibraryReader).position)
    }
    @Test
    fun cachedSearchSessionSurvivesHistoryAndGroupArchivingWithoutResultPayloads() {
        val page = BrowserPage.Search(query = "draft", submittedQuery = "tag:full_color",
            submittedQueries = listOf("tag:full_color language:korean"), resultSessionId = "cached-search",
            currentPage = 40, scrollPage = 40, scrollAnchorKey = "gallery:1200", scrollIndex = 1201, scrollOffset = 35)
        val workspace = BrowserWorkspace.initial(page).let {
            it.createGroup(setOf(it.activeTabId), "Saved")
        }.let { it.archiveGroup(it.groups.single().id) }
        val restored = browserWorkspaceFromJson(browserWorkspaceToJson(workspace))
        assertEquals(page, restored.groups.single().archivedTabs.single().currentPage)
    }

    @Test
    fun activeAndArchivedGroupsRoundTripInBothWorkspaces() {
        listOf(BrowserPage.Search(query = "saved"), BrowserPage.LibraryHome()).forEach { page ->
            val first = BrowserWorkspace.initial(page).let { it.createGroup(setOf(it.activeTabId), "Archived", 1) }
            val archived = first.archiveGroup(first.groups.single().id, page)
            val active = archived.createGroup(setOf(archived.activeTabId), "Active", 3)
                .openTab(page, groupId = null)
            val ordered = active.moveOverviewItem(active.overviewItems.first().key, active.overviewItems.last().key)
            val restored = browserWorkspaceFromJson(browserWorkspaceToJson(ordered))
            assertEquals(ordered, restored)
            if (page is BrowserPage.Search) assertEquals(ordered, restored.searchWorkspace())
            else assertEquals(ordered, restored.galleryWorkspace())
        }
    }

    @Test
    fun legacyTabsAndInvalidGroupReferencesRemainUsable() {
        val restored = browserWorkspaceFromJson("""{
            "version":1,"activeTabId":"old","tabs":[
                {"id":"old","groupId":"missing","history":[{"type":"search","query":"legacy"}]}
            ]
        }""")
        assertEquals("old", restored.activeTabId)
        assertEquals(null, restored.activeTab.groupId)
        assertEquals("legacy", (restored.activeTab.currentPage as BrowserPage.Search).query)
        assertTrue(restored.groups.isEmpty())
    }

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
