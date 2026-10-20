package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScrollbarGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thumbStartsImmediatelyAndTrackLongPressReachesListEnd() {
        val state = LazyListState()
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("content")) {
                items(200) { Text("Book $it", Modifier.fillMaxWidth().height(96.dp)) }
            }
            DynamicVerticalScrollbar(state)
        } } }
        compose.onNodeWithTag("dynamic-scrollbar").performTouchInput {
            down(Offset(centerX, 12f)); advanceEventTime(16); moveTo(Offset(centerX, height * .6f)); advanceEventTime(16); up()
        }
        compose.runOnIdle { assertTrue("Thumb must move without waiting for a long press", state.firstVisibleItemIndex > 20) }
        compose.onNodeWithTag("dynamic-scrollbar").performTouchInput {
            down(Offset(centerX, height * .95f)); advanceEventTime(650); moveTo(Offset(centerX, height.toFloat())); up()
        }
        compose.runOnIdle { assertFalse(state.canScrollForward) }
        compose.onNodeWithTag("content").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(state.canScrollForward) }
    }

    @Test fun thumbMovesGridWithoutLongPress() {
        val state = LazyGridState()
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize()) {
                items(200) { Text("Book $it", Modifier.height(160.dp)) }
            }
            DynamicVerticalScrollbar(state)
        } } }
        compose.onNodeWithTag("dynamic-scrollbar").performTouchInput {
            down(Offset(centerX, 12f)); advanceEventTime(16); moveTo(Offset(centerX, height * .7f)); advanceEventTime(16); up()
        }
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 30) }
    }
}
