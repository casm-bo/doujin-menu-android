package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.security.BrowserPreferenceStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchTagFiltersTest {
    @get:Rule val compose=createComposeRule()
    @Test fun customBlockedTagsCanBeAddedAndRemovedWhileInvalidConditionsStayUnsaved() {
        var tags=emptySet<String>()
        compose.setContent { var current by remember { mutableStateOf(tags) };MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SearchTagFilters(emptySet(),current,{}, {current=it;tags=it})
            }
        } }
        compose.onNodeWithTag("custom-blocked-tags").performScrollTo().performTextInput("tag:synthetic_example")
        compose.onNodeWithTag("add-blocked-tags").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf("tag:synthetic_example"),tags) }
        compose.onNodeWithText("tag:synthetic_example").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(tags.isEmpty()) }
        compose.onNodeWithTag("custom-blocked-tags").performScrollTo().performTextInput("artist:example")
        compose.onNodeWithTag("add-blocked-tags").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(tags.isEmpty()) }
    }
    @Test fun customBlockedTagsPersistWithoutChangingPresetFilters() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val isolated=object:android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences("synthetic-custom-blocks",mode)
        }
        try {
            val preferences=BrowserPreferenceStore(isolated)
            preferences.saveSearchTagFilters(setOf(com.doujinmenu.android.model.SearchTagFilter.BL))
            preferences.saveCustomBlockedTags(setOf("tag:synthetic_example"))
            assertEquals(setOf("tag:synthetic_example"),BrowserPreferenceStore(isolated).loadCustomBlockedTags())
            assertEquals(setOf(com.doujinmenu.android.model.SearchTagFilter.BL),BrowserPreferenceStore(isolated).loadSearchTagFilters())
        } finally { context.deleteSharedPreferences("synthetic-custom-blocks") }
    }
}
