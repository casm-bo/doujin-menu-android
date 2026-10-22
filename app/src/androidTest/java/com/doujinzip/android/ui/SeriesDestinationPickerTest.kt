package com.doujinzip.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.CustomSeriesAssignment
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesDestinationPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingACardRequiresConfirmationAndFailureKeepsTheSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = MainUiState(customSeriesByBookId = mapOf("a" to CustomSeriesAssignment("Target A", 0), "b" to CustomSeriesAssignment("Target B", 0)))
        var calls = 0
        var done = false
        var accept = false
        var picked: String? = null
        compose.setContent { DoujinZipTheme { SeriesDestinationPicker(state, 2, false, 2, PaddingValues(0.dp),
            onAppend = { picked = it; calls++; accept }, onDone = { done = true }, onCancel = {}) } }
        compose.onNodeWithText("Target A").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText(context.getString(R.string.ui_confirm_add_to_series, 2, "Target A")).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.ui_cancel)).onLast().performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Target B").performClick()
        compose.onNodeWithText(context.getString(R.string.ui_confirm)).performClick()
        assertEquals(1, calls); assertFalse(done)
        compose.onNodeWithText(context.getString(R.string.ui_confirm)).assertExists()
        compose.runOnIdle { accept = true }
        compose.onNodeWithText(context.getString(R.string.ui_confirm)).performClick()
        assertTrue(done); assertEquals("Target B", picked)
    }
}
