package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.security.BrowserPreferenceStore
import org.junit.Assert.*
import org.junit.Test

class SearchDraftStateTest {
    @Test fun galleryDebouncesLatestQueryWithoutRecordingEveryKeystroke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                model.clearLibrarySearchHistory()
                model.setLibraryQuery("tag:old")
                model.setLibraryQuery("tag:full_color, artist:sample")
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                var ready = false
                instrumentation.runOnMainSync { ready = model.uiState.libraryQuery == "tag:full_color artist:sample" }
                if (ready) break
                android.os.SystemClock.sleep(50)
            }
            instrumentation.runOnMainSync {
                assertEquals("tag:full_color artist:sample", model.uiState.libraryQuery)
                assertTrue(model.uiState.libraryQueryHistory.isEmpty())
                model.submitLibrarySearch()
                assertEquals(1, model.uiState.libraryQueryHistory.size)
            }
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
    @Test fun editsNeverReplaceCommittedQueriesLanguagesOrCacheUntilSubmit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = BrowserPreferenceStore(context)
        val committed = BrowserPage.Search(query = "tag:hi", preferredLanguages = setOf("korean"),
            submittedQuery = "tag:hi", submittedQueries = listOf("tag:hi language:korean"),
            currentPage = 4, resultSessionId = "committed-cache", scrollIndex = 20, scrollOffset = 35)
        preferences.saveBrowserWorkspace(BrowserWorkspace.initial(committed))
        preferences.saveGalleryWorkspace(BrowserWorkspace.initial(BrowserPage.LibraryHome(query = "tag:hi")))
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                model.setSearchQuery("tag:hello")
                model.togglePreferredLanguage("english")
                model.setLibraryQuery("tag:hello")
                model.onAppBackgrounded()
                assertEquals("tag:hello", model.uiState.searchQuery)
                assertEquals("tag:hi", model.uiState.libraryQuery)
                assertEquals(committed, preferences.loadBrowserWorkspace().activeTab.currentPage)
                assertEquals("tag:hi", (preferences.loadGalleryWorkspace().activeTab.currentPage as BrowserPage.LibraryHome).query)
                model.submitLibrarySearch()
                assertEquals("tag:hello", model.uiState.libraryQuery)
                assertEquals("tag:hello", (preferences.loadGalleryWorkspace().activeTab.currentPage as BrowserPage.LibraryHome).query)
                assertEquals("tag:hello", model.uiState.libraryQueryHistory.first().query)
            }
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
