package com.doujinmenu.android.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.security.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realAppReturnsFromNextEpisodeToIndependentSeriesThenOriginalGallery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val browser = BrowserPreferenceStore(context)
        val library = LibraryPreferenceStore(context)
        val profiles = SecureProfileStore(context)
        val previousProfiles = profiles.load()
        val previousWorkspace = browser.loadGalleryWorkspace()
        val previousLocations = browser.loadLibraryLocations()
        val previousDestination = browser.loadMainDestination()
        val previousActive = browser.loadActiveGalleryWorkspace()
        val previousSeries = library.loadCustomSeries()
        val previousViewer = library.loadViewerPreferences()
        val server = ServerSocket(0)
        val workers = Executors.newCachedThreadPool()
        val base = "http://127.0.0.1:${server.localPort}"
        val name = "Route / series"
        val image = ByteArrayOutputStream().also { output ->
            val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }.toByteArray()
        workers.submit { runCatching { while (!server.isClosed) {
            val socket = server.accept()
            workers.submit { runCatching { socket.use {
                val input = it.getInputStream().bufferedReader()
                val path = input.readLine().substringAfter(' ').substringBefore(' ')
                while (!input.readLine().isNullOrEmpty()) { }
                val png = path.endsWith(".png") || path.endsWith("/cover")
                val data: Any = when {
                    path.startsWith("/v1/library/books?") -> JSONArray().apply { repeat(43) { index ->
                        put(JSONObject().put("id", index + 1).put("title", if (index < 2) "Route episode ${index + 1}" else "Route standalone $index")
                            .put("libraryPath", "fixture").put("pageCount", 1).put("modifiedAt", 43 - index)
                            .put("seriesCollection", if (index < 2) JSONObject().put("name", name).put("order", index).put("modifiedAt", 1) else JSONObject.NULL))
                    } }
                    path.endsWith("/pages") -> JSONArray().put("$base/page.png")
                    path.startsWith("/v1/library/presentations") -> JSONObject().put("values", JSONArray()).put("cursor", 0).put("hasMore", false)
                    else -> JSONObject().put("path", "fixture")
                }
                val bytes = if (png) image else JSONObject().put("success", true).put("data", data).toString().toByteArray()
                val type = if (png) "image/png" else "application/json"
                it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            } } }
        } } }
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        lateinit var back: OnBackPressedDispatcher
        try {
            profiles.save(listOf(DesktopProfile("series-route-fixture", "Fixture", base, "fixture-token")))
            browser.saveLibraryLocations(emptyList())
            browser.saveGalleryWorkspace(BrowserWorkspace.initial(BrowserPage.LibraryHome(scrollOffset = 12)))
            browser.saveMainDestination("library")
            browser.saveActiveGalleryWorkspace(true)
            library.saveCustomSeries(emptyMap())
            library.saveViewerPreferences(ViewerPreferences(readingMode = ViewerReadingMode.PAGE, readingDirection = ViewerReadingDirection.LEFT_TO_RIGHT, hideThumbnails = true))
            instrumentation.runOnMainSync { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java] }
            compose.waitUntil(10_000) { model.uiState.libraryBooks.size == 43 && !model.uiState.isLibraryScanning }
            compose.setContent { DoujinMenuTheme {
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                DoujinMenuApp(model)
            } }
            compose.onNodeWithText(name).performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(model.activeBrowserPage() is BrowserPage.LibrarySeries); assertFalse(model.uiState.librarySeriesMode) }
            // A detail route has no main navigation or gallery filter toolbar.
            compose.onNodeWithText(context.getString(R.string.ui_all)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.ui_history_navigation)).assertDoesNotExist()
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("1"))
            compose.waitUntil(5_000) { (model.activeBrowserPage() as BrowserPage.LibrarySeries).previewPosition != ReaderPosition(0) }
            val seriesPosition = (model.activeBrowserPage() as BrowserPage.LibrarySeries).previewPosition
            compose.onAllNodesWithContentDescription("1").onFirst().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-pager").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader-pager").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.ui_continue_with, "Route episode 2")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(context.getString(R.string.ui_open)).performClick()
            compose.waitUntil(10_000) { (model.activeBrowserPage() as? BrowserPage.LibraryReader)?.title == "Route episode 2" && !model.uiState.isLibraryBookLoading }
            compose.runOnIdle { back.onBackPressed() }
            compose.waitUntil(5_000) { model.activeBrowserPage() is BrowserPage.LibrarySeries }
            compose.onNode(hasScrollAction()).assertExists()
            compose.onNodeWithTag("reader-pager").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.ui_history_navigation)).assertDoesNotExist()
            compose.runOnIdle {
                val series = model.activeBrowserPage() as BrowserPage.LibrarySeries
                assertFalse(series.episodeList); assertEquals(seriesPosition, series.previewPosition); back.onBackPressed()
            }
            compose.onNodeWithText("Route standalone 2").assertExists()
            compose.runOnIdle {
                assertTrue(model.activeBrowserPage() is BrowserPage.LibraryHome)
                assertEquals(12, (model.activeBrowserPage() as BrowserPage.LibraryHome).scrollOffset)
                assertFalse(model.uiState.librarySeriesMode)
            }
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            val visible = compose.onAllNodesWithText("Route standalone", substring = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.height > 40 }.minBy { it.boundsInRoot.top }
            val title = visible.config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
            val top = visible.boundsInRoot.top
            compose.onNodeWithText(context.getString(MainDestination.Settings.labelResource)).performClick()
            compose.onNodeWithText(context.getString(MainDestination.Library.labelResource)).performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            assertEquals("Tab return must retain both item offset and toolbar position", top,
                compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top, 2f)
            compose.runOnIdle { model.setLibrarySeriesMode(true) }
            compose.onNodeWithText(name).performClick()
            compose.runOnIdle { back.onBackPressed() }
            compose.runOnIdle { assertTrue(model.uiState.librarySeriesMode); assertTrue(model.activeBrowserPage() is BrowserPage.LibraryHome) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            server.close(); workers.shutdownNow()
            profiles.save(previousProfiles)
            browser.saveGalleryWorkspace(previousWorkspace)
            browser.saveLibraryLocations(previousLocations)
            browser.saveMainDestination(previousDestination)
            browser.saveActiveGalleryWorkspace(previousActive)
            library.saveCustomSeries(previousSeries)
            library.saveViewerPreferences(previousViewer)
        }
    }
}
