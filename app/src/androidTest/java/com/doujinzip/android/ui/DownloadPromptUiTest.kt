package com.doujinzip.android.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.security.BrowserPreferenceStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DownloadPromptUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun downloadChooserOpensSettingsWithBothStoragePaths() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val strings = context.resources
        val preferences = BrowserPreferenceStore(context)
        val destination = preferences.loadDownloadDestination()
        val previous = preferences.loadBrowserWorkspace()
        val main = preferences.loadMainDestination()
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            preferences.saveDownloadDestination(DownloadDestination.ASK)
            preferences.saveBrowserWorkspace(BrowserWorkspace.initial())
            instrumentation.runOnMainSync { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java] }
            model.awaitStarted()
            compose.setContent { DoujinZipTheme { DoujinZipApp(model) } }
            compose.runOnIdle { model.downloadGallery(GallerySummary(9_900_002, "Chooser fixture", emptyList(), emptyList(), null, emptyList(), null, 1, null)) }
            compose.onNodeWithText(strings.getString(R.string.ui_choose_download_destination)).assertExists()
            compose.onNodeWithText(strings.getString(R.string.ui_download_settings)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(strings.getString(R.string.ui_download_destination)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(strings.getString(R.string.ui_always_ask)).assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(strings.getString(R.string.ui_local_download_folder)))
            compose.onNodeWithText(strings.getString(R.string.ui_local_download_folder)).assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(strings.getString(R.string.ui_desktop_download_folder)))
            compose.onNodeWithText(strings.getString(R.string.ui_desktop_download_folder)).assertExists()
            compose.runOnIdle { assertNull(model.uiState.settingsSectionRequest) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            preferences.saveDownloadDestination(destination); preferences.saveBrowserWorkspace(previous); preferences.saveMainDestination(main)
        }
    }
}
