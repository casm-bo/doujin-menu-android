package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesOptionsEditorTest {
    @get:Rule val compose = createComposeRule()
    private val books = listOf("A", "B", "C").map { LibraryBook(it, it, locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1) }
    private val assignments = mapOf("A" to CustomSeriesAssignment("Target", 0), "B" to CustomSeriesAssignment("Target", 1), "C" to CustomSeriesAssignment("Other", 0))
    @Test fun addAndExcludeStayInDraftUntilSaveAndKeepFiles() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saved: List<String>? = null
        var done = false
        compose.setContent { DoujinMenuTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { baseline, order -> assertEquals(assignments, baseline); saved = order; true }, onDone = { done = true }, onCancel = {}) } }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_exclude_episodes)).performClick()
        assertNull(saved)
        compose.onNodeWithText(strings.getString(R.string.ui_add_episodes)).performClick()
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_confirm)).performClick()
        assertNull(saved)
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(listOf("B", "C"), saved)
        assertTrue(done)
        assertEquals(3, books.size)
    }
    @Test fun dragReordersOnlyDraftUntilSave() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saves = 0
        compose.setContent { DoujinMenuTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, order -> assertEquals(listOf("B", "A"), order); saves++; true }, onDone = {}, onCancel = {}) } }
        val delta = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.center.y - compose.onNodeWithText("B").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithText("B").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, delta * 1.2f)); up() }
        assertEquals(0, saves)
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(1, saves)
    }
    @Test fun cancellingAnExcludedEpisodeNeverCallsSave() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saves = 0
        var cancelled = false
        compose.setContent { DoujinMenuTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, _ -> saves++; true }, onDone = {}, onCancel = { cancelled = true }) } }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_exclude_episodes)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_cancel)).performClick()
        assertEquals(0, saves); assertTrue(cancelled)
        assertEquals(2, assignments.count { it.value.name == "Target" })
    }
}
