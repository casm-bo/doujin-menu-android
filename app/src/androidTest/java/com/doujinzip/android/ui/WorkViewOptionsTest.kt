package com.doujinzip.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.security.LibraryPreferenceStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkViewOptionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sizesStayOpenAndListSizesChangeCardDimensions() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var columns by mutableIntStateOf(3)
        var menu by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box {
            CompositionLocalProvider(LocalWorkCardSize provides WorkCardSize.fromColumns(columns)) {
                Box(Modifier.testTag("card")) { WorkCard("Fixture", "Artist", "42 pages", emptyList(), "", grid = false, onClick = {}) {} }
            }
            DropdownMenu(menu, { menu = false }) { WorkViewOptions(false, columns, { menu = false }, { columns = it }) }
        } } }
        val medium = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText(strings.getString(R.string.ui_small_4_columns)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_grid_view)).assertExists()
        val small = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText(strings.getString(R.string.ui_large_2_columns)).performClick()
        val large = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(small < medium)
        assertTrue(large > medium)
        compose.onNodeWithText(strings.getString(R.string.ui_grid_view)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_small_4_columns)).assertDoesNotExist()
    }

    @Test fun searchAndGalleryPreferencesAreIndependent() {
        val store = LibraryPreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val gallery = store.loadLibraryGridColumns()
        val search = store.loadSearchGridColumns()
        try {
            store.saveLibraryGridColumns(2); store.saveSearchGridColumns(4)
            assertEquals(2, store.loadLibraryGridColumns()); assertEquals(4, store.loadSearchGridColumns())
        } finally { store.saveLibraryGridColumns(gallery); store.saveSearchGridColumns(search) }
    }
}

