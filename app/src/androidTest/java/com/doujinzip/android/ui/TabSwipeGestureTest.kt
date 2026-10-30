package com.doujinzip.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabSwipeGestureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun toolbarSwipesSwitchTabsBounceAtEdgesAndKeepButtonsClickable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = BrowserTabPreviewCache(context)
        val tabs = (1..3).map { BrowserTab(id = "fixture$it", history = listOf(BrowserPage.Search(query = "fixture$it"))) }
        var workspace by mutableStateOf(BrowserWorkspace(tabs, tabs[1].id))
        var created = 0
        compose.setContent { DoujinZipTheme { TabSwipeHost { Column {
            BrowserTabStrip(workspace, { created++ }, {}, cache, onSelect = { workspace = workspace.selectTab(it) })
        } } } }
        fun expect(id: String) { compose.runOnIdle { assertEquals(id, workspace.activeTabId) } }
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeLeft() }; expect("fixture3")
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeLeft() }; expect("fixture3")
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeRight() }; expect("fixture2")
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeRight() }; expect("fixture1")
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeRight() }; expect("fixture1")
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipe(Offset(width * .5f, centerY), Offset(width * .48f, centerY), 800) }; expect("fixture1")
        compose.onNodeWithContentDescription(context.getString(com.doujinzip.android.R.string.ui_new_tab)).performClick()
        compose.runOnIdle { assertEquals(1, created) }
    }
    @Test fun expandedSearchAndSingleTabDoNotSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = BrowserTabPreviewCache(context)
        val tabs = (1..2).map { BrowserTab(id = "fixture$it") }
        var workspace by mutableStateOf(BrowserWorkspace(tabs, tabs[0].id))
        val search = InlineSearchState().apply { expanded = true }
        var switches = 0
        compose.setContent { DoujinZipTheme { CompositionLocalProvider(LocalInlineSearch provides search) { TabSwipeHost {
            BrowserTabStrip(workspace, {}, {}, cache, onSelect = { switches++; workspace = workspace.selectTab(it) })
        } } } }
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, switches); search.expanded = false; workspace = BrowserWorkspace.initial() }
        compose.onNodeWithTag("tab-toolbar").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, switches) }
    }
}
