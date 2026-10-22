package com.doujinmenu.android.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.model.*
import com.doujinmenu.android.security.BrowserPreferenceStore
import com.doujinmenu.android.security.SecureProfileStore
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NewTabNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun descendantsReturnToSearchAndRepeatedFacetsOpenIndependentChildren() = withModel { model ->
        instrumentation.runOnMainSync {
            val origin = model.activeBrowserPage()
            // An unsupported synthetic namespace exercises tab creation without contacting a search server.
            model.searchFromFacet("fixture:artist")
            val root = model.uiState.browserWorkspace
            val parentId = root.activeTabId
            assertTrue(model.canReturnFromNewTab(false))
            val detail = root.pushPage(BrowserPage.OnlineGallery(42, "Fixture"))
            model.updateTabWorkspace(detail, false)
            assertFalse(model.canReturnFromNewTab(false))
            model.searchFromFacet("fixture:artist")
            val childId = model.uiState.browserWorkspace.activeTabId
            assertNotEquals(parentId, childId)
            assertEquals(parentId, model.uiState.browserWorkspace.activeTab.returnTo!!.originTabId)
            assertTrue(model.goBackInBrowserTab())
            assertEquals(detail.activeTab.currentPage, model.activeBrowserPage())
            assertEquals(parentId, model.uiState.browserWorkspace.activeTabId)
            assertTrue(model.goBackInBrowserTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.Search)
            assertEquals(parentId, model.uiState.browserWorkspace.activeTabId)
            assertTrue(model.goBackInBrowserTab())
            assertEquals(origin, model.activeBrowserPage())
            assertFalse(model.uiState.browserWorkspace.tabs.any { it.id == childId || it.id == parentId })
        }
    }

    @Test fun galleryTabBackTraversesDescendantsBeforeClosingAtHome() = withModel { model ->
        instrumentation.runOnMainSync {
            val origin = model.uiState.galleryWorkspace
            model.newGalleryTab()
            val opened = model.uiState.galleryWorkspace
            model.updateTabWorkspace(opened.pushPage(BrowserPage.LibraryBook("fixture", "Fixture"))
                .pushPage(BrowserPage.LibraryReader("fixture", "Fixture", 0)), true)
            assertTrue(model.goBackInGalleryTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.LibraryBook)
            assertEquals(opened.activeTabId, model.uiState.galleryWorkspace.activeTabId)
            assertTrue(model.goBackInGalleryTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.LibraryHome)
            assertEquals(opened.activeTabId, model.uiState.galleryWorkspace.activeTabId)
            assertTrue(model.goBackInGalleryTab())
            assertEquals(origin, model.uiState.galleryWorkspace)
        }
    }

    @Test fun systemBackAtSearchRootClosesTabAndReturnsToGalleryOpener() = withModel { model ->
        lateinit var back: OnBackPressedDispatcher
        lateinit var origin: BrowserPage
        instrumentation.runOnMainSync {
            origin = model.activeBrowserPage()
            model.searchFromFacet("fixture:artist")
        }
        compose.setContent { DoujinMenuTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DoujinMenuApp(model)
        } }
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle {
            assertEquals(origin, model.activeBrowserPage())
            assertEquals(1, model.uiState.browserWorkspace.tabs.size)
        }
    }

    private fun withModel(check: (MainViewModel) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = BrowserPreferenceStore(context)
        val profiles = SecureProfileStore(context)
        val oldProfiles = profiles.load()
        val oldBrowser = preferences.loadBrowserWorkspace()
        val oldGallery = preferences.loadGalleryWorkspace()
        val oldActive = preferences.loadActiveGalleryWorkspace()
        val oldLanguages = preferences.loadPreferredLanguages()
        val oldLocations = preferences.loadLibraryLocations()
        val oldHistory = preferences.loadSearchQueryHistory()
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            profiles.save(emptyList())
            preferences.savePreferredLanguages(emptySet())
            preferences.saveLibraryLocations(emptyList())
            preferences.saveBrowserWorkspace(BrowserWorkspace.initial())
            preferences.saveGalleryWorkspace(BrowserWorkspace.initial(BrowserPage.LibraryHome()))
            preferences.saveActiveGalleryWorkspace(true)
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            check(model)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            profiles.save(oldProfiles)
            preferences.saveBrowserWorkspace(oldBrowser)
            preferences.saveGalleryWorkspace(oldGallery)
            preferences.saveActiveGalleryWorkspace(oldActive)
            preferences.savePreferredLanguages(oldLanguages)
            preferences.saveLibraryLocations(oldLocations)
            preferences.saveSearchQueryHistory(oldHistory)
        }
    }
}
