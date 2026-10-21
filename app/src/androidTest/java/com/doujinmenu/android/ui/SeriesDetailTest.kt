package com.doujinmenu.android.ui

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SeriesDetailTest {
    @get:Rule val compose = createComposeRule()
    @Test fun continuingAtEpisodeEndThenBackReturnsToSeriesDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cover = File(context.cacheDir, "series-navigation.png")
        val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val books = listOf("First episode", "Second episode").mapIndexed { index, title ->
            LibraryBook("reader-nav-$index", title, locationUri = "fixture", locationName = "Fixture", folderUri = "",
                pages = listOf(LibraryPage(cover.toURI().toString(), "Page 1")), modifiedAt = 1)
        }
        val home = BrowserPage.LibrarySeries("Navigation series")
        var workspace by mutableStateOf(BrowserWorkspace.initial(home))
        lateinit var back: OnBackPressedDispatcher
        compose.setContent { DoujinMenuTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val current = workspace.activeTab.currentPage
            if (current is BrowserPage.LibraryReader) {
                BackHandler { workspace = workspace.goBack() }
                val book = books.first { it.id == current.bookId }
                LocalReaderScreen(current.key, book, 0, 0, false, null, false,
                    ViewerPreferences(readingMode = ViewerReadingMode.PAGE, readingDirection = ViewerReadingDirection.LEFT_TO_RIGHT, hideThumbnails = true),
                    onBack = { workspace = workspace.goBack() }, onToggleFavorite = {},
                    onProgress = { workspace = workspace.updateReaderPosition(current.key, it) },
                    nextBookTitle = if (book.id == books.first().id) books.last().title else null,
                    onOpenNextBook = { workspace = workspace.continueLibraryReader(BrowserPage.LibraryReader(books.last().id, books.last().title, 0)) },
                    onPreferencesChange = {})
            } else SeriesDetailContent("Navigation series", books, MainUiState(), {}, {}, {},
                { id, page -> workspace = workspace.openLibraryReader(BrowserPage.LibraryReader(id, books.first { it.id == id }.title, page)) },
                {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.ui_read_from_beginning)))
        compose.onNodeWithText(context.getString(R.string.ui_read_from_beginning)).performClick()
        compose.onNodeWithTag("reader-pager").performTouchInput { click(Offset(width * .9f, height * .5f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.ui_continue_with, books.last().title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.ui_continue_with, books.last().title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.ui_open)).performClick()
        compose.onNodeWithText(books.last().title).assertExists()
        compose.runOnIdle {
            assertEquals(books.last().id, (workspace.activeTab.currentPage as BrowserPage.LibraryReader).bookId)
            assertEquals(2, workspace.activeTab.history.size)
            back.onBackPressed()
        }
        compose.onNodeWithText("Navigation series").assertExists()
        compose.runOnIdle { assertEquals(home, workspace.activeTab.currentPage) }
        cover.delete()
    }

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
