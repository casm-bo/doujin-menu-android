package com.doujinmenu.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.data.PresentationRecord
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DetailUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun metadataEditorSavesClearsAndKeepsOriginalSeriesSeparate() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val value = GalleryPresentation("hitomi:42", "Original title", LibraryMetadata(series = listOf("Original IP"), tags = listOf("source")))
        var saved: GalleryPresentation? = null
        compose.setContent { DoujinMenuTheme { MetadataEditor(value, null, {}, { saved = it }) } }
        compose.onNodeWithText("Original title").performTextReplacement("Phone title")
        compose.onNodeWithText("Original IP").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_source_series)).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("source"))
        compose.onNodeWithText("source").performTextClearance()
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals("Phone title", saved!!.title)
        assertTrue(saved!!.metadata.tags.isEmpty())
        assertEquals(listOf("Original IP"), saved!!.metadata.series)
    }
    @Test fun conflictMenuRetainsPhoneEditUntilTheUserChoosesPc() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val value = GalleryPresentation("hitomi:42", "Phone edit", LibraryMetadata())
        var accepted = false
        compose.setContent { DoujinMenuTheme { PresentationActions(value, PresentationRecord(value, dirty = true, conflict = value.copy(title = "PC edit", version = 1)),
            emptyList(), false, null, { _, _ -> }, { _, _ -> }, { accepted = true }, emptyList()) } }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_detail_options)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_metadata_conflict)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_cover_from_page)).assertIsNotEnabled()
        assertFalse(accepted)
        compose.onNodeWithText(strings.getString(R.string.ui_use_pc_edit)).performClick()
        assertTrue(accepted)
    }
}
