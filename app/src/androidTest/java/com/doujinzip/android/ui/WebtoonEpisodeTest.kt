package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WebtoonEpisodeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun image(height: Int): File {
        val file = File(context.cacheDir, "webtoon-episode-$height.png")
        val bitmap = Bitmap.createBitmap(200, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun bottomPullShowsConfirmationAndCancelRequiresAnotherGesture() {
        val file = image(1800)
        val pages = listOf(ReaderPageModel("webtoon-episode", file, ReaderImageSource(file.toURI().toString())))
        var opened = 0
        compose.setContent { MaterialTheme {
            UnifiedReader("episode-pull", "episode-pull-fixture", title = "Episode 1", pages = pages, initialPage = 0,
                preferences = ViewerPreferences(readingMode = ViewerReadingMode.WEBTOON, hideThumbnails = true),
                onBack = {}, onProgress = {}, onPreferencesChange = {}, nextBookTitle = "Episode 2", onOpenNextBook = { opened++ })
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.ui_pages, 1)).fetchSemanticsNodes().isNotEmpty() }
        val hint = context.getString(R.string.ui_pull_next_chapter)
        val popup = context.getString(R.string.ui_next_book)
        compose.onNodeWithTag("webtoon-reader").performScrollToNode(hasText(hint))
        compose.waitForIdle()
        // Restoring/jumping to the bottom must not open the confirmation.
        compose.onNodeWithText(popup).assertDoesNotExist()
        compose.onNodeWithTag("webtoon-reader").performTouchInput { swipeUp(startY = height * .7f, endY = height * .1f, durationMillis = 800) }
        compose.onNodeWithText(popup).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithText(context.getString(R.string.ui_cancel)).performClick()
        compose.onNodeWithText(popup).assertDoesNotExist()
        compose.onNodeWithTag("webtoon-reader").performTouchInput { swipeUp(startY = height * .7f, endY = height * .1f, durationMillis = 800) }
        compose.onNodeWithText(popup).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ui_open)).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        file.delete()
    }

    @Test fun shortContentDoesNotPromptOnLoadOrTinyDragAndLastEpisodeHasNoNextAction() {
        val file = image(100)
        val pages = listOf(ReaderPageModel("short-episode", file, ReaderImageSource(file.toURI().toString())))
        var nextAvailable by mutableStateOf(true)
        var prompts = 0
        compose.setContent { MaterialTheme {
            WebtoonReader("short-pull", pages, ReaderPosition(0), mapOf(0 to ImageDimensions(200, 100)),
                null, {}, {}, {}, {}, {}, if (nextAvailable) ({ prompts++ }) else null)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.ui_pages, 1)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(0, prompts) }
        compose.onNodeWithTag("webtoon-reader").performTouchInput { swipeUp(startY = height * .5f, endY = height * .5f - 15f, durationMillis = 500) }
        compose.runOnIdle { assertEquals(0, prompts) }
        compose.onNodeWithTag("webtoon-reader").performTouchInput { swipeUp(startY = height * .7f, endY = height * .1f, durationMillis = 800) }
        compose.runOnIdle { assertEquals(1, prompts); nextAvailable = false }
        compose.onNodeWithText(context.getString(R.string.ui_pull_next_chapter)).assertDoesNotExist()
        compose.onNodeWithTag("webtoon-reader").performTouchInput { swipeUp(startY = height * .7f, endY = height * .1f, durationMillis = 800) }
        compose.runOnIdle { assertEquals(1, prompts) }
        file.delete()
    }
}
