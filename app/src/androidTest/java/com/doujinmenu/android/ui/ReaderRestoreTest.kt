package com.doujinmenu.android.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.data.ReaderImages
import com.doujinmenu.android.model.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ReaderRestoreTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(name: String, width: Int, height: Int): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(42, 84, 126))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun pagedReaderResumesPage50AfterLockInsteadOfOriginalPage4() {
        val file = image("paged-fixture.png", 200, 300)
        val pages = List(60) { ReaderPageModel("$it", file, ReaderImageSource(file.toURI().toString())) }
        var workspace by mutableStateOf(BrowserWorkspace.initial(BrowserPage.OnlineReader(1, "Fixture", 3)))
        var locked by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            val session = workspace.activeTab.currentPage as BrowserPage.OnlineReader
            if (locked) Text("Locked") else UnifiedReader(session.key, "restore-fixture", title = "Fixture", pages = pages,
                initialPage = session.position?.page ?: session.startPage, preferences = ViewerPreferences(readingMode = ViewerReadingMode.PAGE, hideThumbnails = true),
                onBack = {}, onProgress = { workspace = workspace.updateReaderPosition(session.key, it) }, onPreferencesChange = {})
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("reader-pager").performScrollToIndex(49)
        compose.runOnIdle { assertEquals(49, (workspace.activeTab.currentPage as BrowserPage.OnlineReader).position?.page); locked = true }
        compose.onNodeWithText("Locked").assertExists()
        compose.runOnIdle { locked = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(49, (workspace.activeTab.currentPage as BrowserPage.OnlineReader).position?.page) }
        file.delete()
    }

    @Test fun previewReturnsToThumbnailAndOffsetAfterHeaderSizeChanges() {
        var position by mutableStateOf(ReaderPosition(0))
        var show by mutableStateOf(true)
        var header by mutableIntStateOf(200)
        lateinit var state: LazyGridState
        compose.setContent { MaterialTheme {
            if (show) {
                state = previewGridState("detail", position, 60) { position = it }
                LazyVerticalGrid(GridCells.Fixed(3), state = state, modifier = Modifier.testTag("preview")) {
                    item(span = { GridItemSpan(3) }) { Text("Header", Modifier.height(header.dp)) }
                    items(60) { Text("Page $it", Modifier.height(100.dp)) }
                }
            } else Text("Reader")
        } }
        compose.onNodeWithTag("preview").performScrollToIndex(43)
        var expected = ReaderPosition(0)
        compose.runOnIdle { expected = ReaderPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset); show = false }
        compose.onNodeWithText("Reader").assertExists()
        compose.runOnIdle { header = 390; show = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(expected.page, state.firstVisibleItemIndex); assertEquals(expected.offset, state.firstVisibleItemScrollOffset) }
    }

    @Test fun realTallImageIsDetectedAndDecodedInBoundedTilesAndRestoresInternalOffset() = runBlocking<Unit> {
        val file = image("webtoon-fixture.png", 512, 18_000)
        val repository = ReaderImages(context)
        val source = ReaderImageSource(file.toURI().toString())
        val dimensions = repository.probe(source)!!
        assertEquals(ViewerReadingMode.WEBTOON, detectReadingMode(List(3) { dimensions }))
        val cached = repository.file(source)
        val tile = repository.tile(cached, webtoonTiles(3, dimensions, 1080)[5], 1080)
        assertTrue(tile.width <= 1600 && tile.height <= 2048)
        assertTrue(tile.allocationByteCount <= 1600 * 2048 * 4)
        tile.recycle()
        val pages = List(5) { ReaderPageModel("tall:$it", file, source) }
        var saved by mutableStateOf(ReaderPosition(3, 5000))
        var show by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            if (show) WebtoonReader("webtoon", pages, saved, mapOf(3 to dimensions), null, {}, {}, { saved = it }, {}, {})
            else Text("Locked webtoon")
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("webtoon-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, saved.page); assertTrue(abs(saved.offset - 5000) <= 2); show = false }
        compose.onNodeWithText("Locked webtoon").assertExists()
        compose.runOnIdle { show = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, saved.page); assertTrue(abs(saved.offset - 5000) <= 4) }
        file.delete()
    }
}
