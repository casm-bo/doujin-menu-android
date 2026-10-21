package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesOptionsEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val books = listOf("A", "B", "C").map { LibraryBook(it, it, locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1) }
    private val assignments = mapOf("A" to CustomSeriesAssignment("Target", 0), "B" to CustomSeriesAssignment("Target", 1), "C" to CustomSeriesAssignment("Other", 0))
    @Test fun largeTitleStartsBelowStatusBarOnIndependentSeriesRoute() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent { DoujinMenuTheme(fontSizeSp = 28f) { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, _ -> true }, onDone = {}, onCancel = {}) } }
        var top = 0
        compose.activityRule.scenario.onActivity {
            top = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        }
        assertTrue("Series title must clear the status bar", compose.onNodeWithText("Target").fetchSemanticsNode().boundsInRoot.top >= top)
    }
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

    @Test fun episodeSearchUsesGalleryFacetsAndPreservesHiddenSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strings = context.resources
        val candidates = books + listOf(
            books[0].copy(id = "D", title = "Find me", metadata = LibraryMetadata(artists = listOf("Alice"), tags = listOf("full color"))),
            books[0].copy(id = "E", title = "Other candidate", metadata = LibraryMetadata(artists = listOf("Bob"))))
        var saved: List<String>? = null
        compose.setContent {
            val themed = remember { android.view.ContextThemeWrapper(context, R.style.Theme_DoujinMenu) }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides themed) {
                DoujinMenuTheme(fontSizeSp = 24f) { SeriesOptionsEditor("Target", candidates, assignments, PaddingValues(24.dp, 0.dp),
                    onSave = { _, order -> saved = order; true }, onDone = {}, onCancel = {}) }
            }
        }
        compose.onNodeWithText(strings.getString(R.string.ui_add_episodes)).performClick()
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_search_gallery)).performClick()
        fun descendants(view: android.view.View): Sequence<android.view.View> = sequence {
            yield(view)
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun field() = android.view.inspector.WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants).filterIsInstance<android.widget.EditText>().singleOrNull()
        compose.waitUntil(5_000) { var ready = false; instrumentation.runOnMainSync { ready = field()?.hasFocus() == true }; ready }
        instrumentation.runOnMainSync { field()!!.setText("artist:Alice, tag:full_color") }
        compose.onNodeWithText("Other candidate").assertDoesNotExist()
        compose.onNodeWithText("C").assertDoesNotExist()
        instrumentation.runOnMainSync { field()!!.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) }
        compose.onNodeWithText("Find me").performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_confirm)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(listOf("A", "B", "C", "D"), saved)
    }
}
