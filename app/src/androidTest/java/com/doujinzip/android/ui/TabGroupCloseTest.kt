package com.doujinzip.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabGroupCloseTest {
    @Test fun reopeningLargeGroupShowsSelectedTabInsteadOfFirstRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = BrowserTabPreviewCache(context)
        val tabs = (0..79).map { BrowserTab(id = "fixture-tab$it", history = listOf(BrowserPage.Search(query = "fixture$it"))) }
        val workspace = BrowserWorkspace(tabs, tabs[70].id).createGroup(tabs.mapTo(mutableSetOf()) { it.id }, "Large fixture")
        compose.setContent { DoujinZipTheme {
            BrowserTabsOverview(workspace, false, cache, onSelect = {}, onCloseTab = {}, onUpdate = {}, onDismiss = {})
        } }
        repeat(2) {
            compose.onNodeWithText("Large fixture · 80").performClick()
            compose.onAllNodesWithText("fixture70").onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(com.doujinzip.android.R.string.ui_back_to_all_tabs)).performClick()
        }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun closingGroupMembersAndArchivingReplacesCardsWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = BrowserTabPreviewCache(context)
        val tabs = (1..4).map { BrowserTab(history = listOf(BrowserPage.Search(query = "fixture$it"))) }
        val initial = BrowserWorkspace(tabs, tabs.first().id)
            .createGroup(tabs.take(3).mapTo(mutableSetOf()) { it.id }, "Fixture group")
        var workspace by mutableStateOf(initial)
        compose.setContent { DoujinZipTheme {
            BrowserTabsOverview(workspace, false, cache, onSelect = {},
                onCloseTab = { workspace = workspace.closeTab(it) },
                onUpdate = { workspace = it }, onDismiss = {})
        } }
        compose.onNodeWithText("Fixture group · 3").performClick()
        repeat(2) {
            compose.onAllNodesWithContentDescription(context.getString(com.doujinzip.android.R.string.ui_close_tab))
                .onFirst().performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(2, workspace.tabs.size) }
        compose.onNodeWithContentDescription(context.getString(com.doujinzip.android.R.string.ui_back_to_all_tabs)).performClick()
        compose.onNodeWithText("Fixture group · 1").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, workspace.tabs.size)
            assertEquals(1, workspace.groups.single().archivedTabs.size)
            workspace = workspace.restoreGroup(workspace.groups.single().id)
        }
        compose.onNodeWithText("Fixture group · 1").assertExists().performClick()
        compose.onNodeWithContentDescription(context.getString(com.doujinzip.android.R.string.ui_close_tab)).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, workspace.tabs.size)
            assertTrue(workspace.groups.isEmpty())
        }
    }
}
