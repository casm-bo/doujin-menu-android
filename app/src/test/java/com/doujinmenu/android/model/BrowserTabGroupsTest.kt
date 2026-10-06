package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class BrowserTabGroupsTest {
    @Test
    fun groupingOrderingAndUngroupingPreserveHistoryAndSelection() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
            .pushPage(BrowserPage.OnlineGallery(1, "One"))
            .openTab(BrowserPage.Search(query = "second"))
            .openTab(BrowserPage.Search(query = "third"))
        val grouped = original.createGroup(original.tabs.take(2).mapTo(mutableSetOf()) { it.id }, "Samples", 2)
        val group = grouped.groups.single()
        assertEquals(original.activeTabId, grouped.activeTabId)
        assertEquals(original.tabs.map { it.history }, grouped.tabs.map { it.history })
        assertEquals(2, grouped.overviewItems.size)
        val moved = grouped.moveOverviewItem("group:${group.id}", "tab:${original.activeTabId}")
        assertEquals("group:${group.id}", moved.overviewItems.last().key)
        val ungrouped = moved.ungroup(group.id)
        assertTrue(ungrouped.groups.isEmpty())
        assertTrue(ungrouped.tabs.all { it.groupId == null })
        assertEquals(original.tabs.map { it.history }, ungrouped.tabs.map { it.history })
        assertEquals("tab:${original.activeTabId}", ungrouped.overviewItems.first().key)
    }

    @Test
    fun archiveAndRestoreKeepTabIdsHistoryAndScrollWithoutResults() {
        val search = BrowserPage.Search(query = "example", currentPage = 3, resultIds = listOf(1),
            scrollAnchorKey = "gallery:1", scrollIndex = 20, scrollOffset = 30)
        val grouped = BrowserWorkspace.initial(search).openTab(BrowserPage.OnlineGallery(1, "One"))
            .let { it.createGroup(it.tabs.mapTo(mutableSetOf()) { tab -> tab.id }) }
        val groupId = grouped.groups.single().id
        val archived = grouped.archiveGroup(groupId)
        assertEquals(1, archived.tabs.size)
        assertTrue(archived.groups.single().isArchived)
        assertTrue(archived.overviewItems.single() is BrowserOverviewItem.Tab)
        val snapshot = archived.groups.single().archivedTabs.first().currentPage as BrowserPage.Search
        assertEquals(search.copy(resultIds = emptyList()), snapshot)
        val restored = archived.restoreGroup(groupId)
        assertEquals(grouped.tabs.map { it.id }, restored.tabs.drop(1).map { it.id })
        assertFalse(restored.groups.single().isArchived)
        assertEquals(grouped.tabs.first().id, restored.activeTabId)
        assertEquals(3, restored.tabs.size)
        assertEquals(restored, restored.restoreGroup(groupId))
    }

    @Test
    fun newTabsInheritGroupAndClosingLastTabKeepsArchivedGroups() {
        val original = BrowserWorkspace.initial().let { it.createGroup(setOf(it.activeTabId)) }
        val groupId = original.groups.single().id
        val expanded = original.openTab()
        assertEquals(groupId, expanded.activeTab.groupId)
        val archived = expanded.archiveGroup(groupId, BrowserPage.LibraryHome())
        val closed = archived.closeTab(archived.activeTabId, BrowserPage.LibraryHome())
        assertEquals(archived.groups, closed.groups)
        assertTrue(closed.activeTab.currentPage is BrowserPage.LibraryHome)
        assertTrue(closed.deleteArchivedGroup(groupId).groups.isEmpty())
    }

    @Test
    fun movingMembersAndClosingGroupCleansEmptyGroups() {
        val original = BrowserWorkspace.initial().openTab().openTab()
        val grouped = original.createGroup(original.tabs.take(2).mapTo(mutableSetOf()) { it.id })
        val groupId = grouped.groups.single().id
        val reordered = grouped.moveTabTo(grouped.tabs.first().id, grouped.tabs[1].id)
        assertEquals(grouped.tabs[1].id, reordered.tabs.first().id)
        val moved = reordered.assignTabToGroup(original.tabs.last().id, groupId)
        assertEquals(1, moved.overviewItems.size)
        val detached = moved.tabs.fold(moved) { workspace, tab -> workspace.assignTabToGroup(tab.id, null) }
        assertTrue(detached.groups.isEmpty())
        assertEquals(3, detached.overviewItems.size)
    }
}
