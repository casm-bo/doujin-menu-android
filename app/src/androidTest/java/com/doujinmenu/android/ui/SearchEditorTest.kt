package com.doujinmenu.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.network.FilterSuggestion
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fullscreenEditorRestoresHistoryOnClearAndMovesAutofillCursorToEnd() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var query by mutableStateOf("tag:first, artist:sa")
        var history by mutableStateOf(listOf(SearchQueryEntry("tag:full_color tag:multi-work_series")))
        var submitted = false
        compose.setContent { DoujinMenuTheme { UnifiedSearchField(query, { query = it }, "Fixture search", {}, { submitted = true },
            history, if (query.endsWith("sa")) listOf(FilterSuggestion("artist:sample", "sample")) else emptyList(), false,
            {}, { history = emptyList() }, { query = applySearchSuggestion(query, it.token) }) } }
        compose.onNodeWithContentDescription("Fixture search").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("artist:sample").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("tag:first, artist:sample, ")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(query.length)))
        compose.onNodeWithText(strings.getString(R.string.ui_clear_search)).performClick()
        compose.onNodeWithText("tag:full_color, tag:multi-work_series").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_clear_history)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_no_recent_searches)).assertExists()
        compose.onNode(hasContentDescription(strings.getString(R.string.ui_run_search)) and hasAnyAncestor(isDialog())).performClick()
        assertTrue(submitted)
    }
}
