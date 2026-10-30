package com.doujinzip.android.ui

import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class TabSwipeTest {
    @Test fun neighborsFollowVisibleOrderAndNeverWrapOrIncludeArchivedTabs() {
        val tabs = (1..4).map { BrowserTab(id = "tab$it") }
        var workspace = BrowserWorkspace(tabs, tabs[0].id).createGroup(setOf("tab2", "tab3"), "Fixture")
        val group = workspace.groups.single().id
        workspace = workspace.copy(overviewOrder = listOf("tab:tab4", "group:$group", "tab:tab1"))
        assertNull(workspace.adjacentSwipeTab(1))
        assertEquals("tab3", workspace.adjacentSwipeTab(-1)?.id)
        assertEquals("tab2", workspace.selectTab("tab4").adjacentSwipeTab(1)?.id)
        val archived = workspace.archiveGroup(group).selectTab("tab4")
        assertEquals("tab1", archived.adjacentSwipeTab(1)?.id)
        assertNull(archived.adjacentSwipeTab(-1))
        assertNull(BrowserWorkspace.initial().adjacentSwipeTab(1))
    }
    @Test fun shortSlowDragsCancelAndDirectionalFlicksOrLongDragsCommit() {
        assertEquals(0, tabSwipeStep(-20f, -100f, 400f, 900f))
        assertEquals(1, tabSwipeStep(-100f, 0f, 400f, 900f))
        assertEquals(-1, tabSwipeStep(100f, 0f, 400f, 900f))
        assertEquals(1, tabSwipeStep(-30f, -1200f, 400f, 900f))
        assertEquals(0, tabSwipeStep(-30f, 1200f, 400f, 900f))
        assertEquals(0, tabSwipeStep(-1f, -2000f, 400f, 900f))
        assertEquals(0, tabSwipeStep(-100f, -2000f, 0f, 900f))
    }
}
