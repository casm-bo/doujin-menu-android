package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun pages(): Pair<File, List<ReaderPageModel>> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "reader-controls-fixture.png")
        val bitmap = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file to List(3) { ReaderPageModel("controls:$it", file, ReaderImageSource(file.toURI().toString())) }
    }

    @Test fun counterAndEpisodeButtonsFollowToolbarAndUseCorrectCallbacks() {
        val (file, pages) = pages()
        var previous = 0
        var next = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            UnifiedReader("controls", "controls-fixture", title = "Episode 2", pages = pages, initialPage = 1,
                preferences = ViewerPreferences(readingMode = ViewerReadingMode.PAGE, hideThumbnails = true),
                onBack = {}, onProgress = {}, onPreferencesChange = {}, isSeries = true,
                previousBookTitle = "Episode 1", onOpenPreviousBook = { previous++ },
                nextBookTitle = "Episode 3", onOpenNextBook = { next++ })
        } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("reader-page-number").assertTextEquals("2 / 3").assertIsDisplayed()
        compose.onNodeWithTag("reader-previous-episode").performClick()
        compose.onNodeWithTag("reader-next-episode").performClick()
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, next) }
        compose.onNodeWithTag("reader-pager").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("reader-page-number").assertDoesNotExist()
        compose.onNodeWithTag("reader-previous-episode").assertDoesNotExist()
        compose.onNodeWithTag("reader-next-episode").assertDoesNotExist()
        compose.onNodeWithTag("reader-pager").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("reader-page-number").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithTag("reader-page-number").assertDoesNotExist()
        file.delete()
    }

    @Test fun seriesBoundariesDisableUnavailableEpisodeAndPageNumberPreferenceIsHonored() {
        val (file, pages) = pages()
        var lastEpisode by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            UnifiedReader("boundaries", "boundaries-fixture", title = "Series", pages = pages, initialPage = 0,
                preferences = ViewerPreferences(readingMode = ViewerReadingMode.PAGE, hideThumbnails = true, showPageNumber = false),
                onBack = {}, onProgress = {}, onPreferencesChange = {}, isSeries = true,
                previousBookTitle = if (lastEpisode) "Previous" else null, onOpenPreviousBook = {},
                nextBookTitle = if (lastEpisode) null else "Next", onOpenNextBook = {})
        } }
        compose.onNodeWithTag("reader-page-number").assertDoesNotExist()
        compose.onNodeWithTag("reader-previous-episode").assertIsNotEnabled()
        compose.onNodeWithTag("reader-next-episode").assertIsEnabled()
        compose.runOnIdle { lastEpisode = true }
        compose.onNodeWithTag("reader-previous-episode").assertIsEnabled()
        compose.onNodeWithTag("reader-next-episode").assertIsNotEnabled()
        file.delete()
    }
}
