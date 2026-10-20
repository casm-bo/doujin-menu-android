package com.doujinmenu.android.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesDetailTest {
    @get:Rule val compose = createComposeRule()
    @Test fun resumesLatestEpisodeAndAggregatesPreviewPages() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val books = listOf("First", "Second").map { name -> LibraryBook(name, name, locationUri = "", locationName = "", folderUri = "",
            pages = List(4) { LibraryPage("file:///fixture-$name-$it.png", "$name-page-$it") }, modifiedAt = 1) }
        var opened: Pair<String, Int>? = null
        compose.setContent { DoujinMenuTheme { SeriesDetailContent("Fixture series", books, MainUiState(libraryProgress = mapOf("Second" to 2)),
            {}, {}, {}, { id, page -> opened = id to page }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(strings.getString(R.string.ui_continue_reading)))
        compose.onNodeWithText(strings.getString(R.string.ui_continue_reading)).performClick()
        assertEquals("Second" to 2, opened)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Second-page-3"))
        compose.onNodeWithContentDescription("Second-page-3").assertExists()
    }
}
