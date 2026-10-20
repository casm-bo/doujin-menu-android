package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ToolbarGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolbarHidesOnScrollReturnsOnReverseAndStaysPinnedDuringEditing() {
        var editing by mutableStateOf(false)
        lateinit var behavior: TopAppBarScrollBehavior
        compose.setContent { MaterialTheme {
            behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { !editing })
            Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                CollapsingToolbar(behavior) { Text("Tools", Modifier.fillMaxWidth().height(100.dp)) }
                LazyColumn(Modifier.weight(1f).testTag("items")) {
                    items(200) { Text("Book $it", Modifier.fillMaxWidth().height(96.dp)) }
                }
            }
        } }
        compose.onNodeWithTag("items").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(behavior.state.collapsedFraction > .9f) }
        compose.onNodeWithTag("items").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(behavior.state.collapsedFraction < .1f); editing = true }
        compose.onNodeWithTag("items").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(behavior.state.collapsedFraction < .1f) }
    }

    @Test fun toolsUseAlignedRowsOnlyWhenWidthRequiresIt() {
        var width by mutableStateOf(360.dp)
        compose.setContent { MaterialTheme {
            Box(Modifier.width(width)) { GalleryToolsRow(
                modes = { Box(Modifier.width(140.dp).height(48.dp).testTag("modes")) },
                tools = { Box(Modifier.width(180.dp).height(48.dp).testTag("tools")) },
            ) }
        } }
        compose.waitForIdle()
        assertEquals(compose.onNodeWithTag("modes").getUnclippedBoundsInRoot().top.value,
            compose.onNodeWithTag("tools").getUnclippedBoundsInRoot().top.value, .5f)
        compose.runOnIdle { width = 280.dp }
        compose.waitForIdle()
        assertEquals(compose.onNodeWithTag("modes").getUnclippedBoundsInRoot().bottom.value,
            compose.onNodeWithTag("tools").getUnclippedBoundsInRoot().top.value, .5f)
        assertEquals(280f, compose.onNodeWithTag("tools").getUnclippedBoundsInRoot().right.value, .5f)
    }
}
