package com.doujinzip.android.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.security.LibraryPreferenceStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Controlled local-image workload: separates UI/state work from network loading. */
class LibraryScrollBenchmarkTest {
    @Test fun scrollCachedLibraryAndReportFramesAndStateWrites() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val cover = File(context.cacheDir, "scroll-cover.png")
        val bitmap = Bitmap.createBitmap(512, 712, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(64, 96, 128))
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        LibraryPreferenceStore(context).apply { saveLibraryViewMode("GRID"); saveLibraryGridColumns(2) }
        val uri = cover.toURI().toString()
        val books = List(600) { index -> LibraryBook("fixture-$index", "Fixture book $index", locationUri = "fixture", locationName = "Device",
            folderUri = uri, pages = List(30) { LibraryPage(uri, "$it") }, modifiedAt = index.toLong(),
            metadata = LibraryMetadata(artists = listOf("Sample artist"), tags = List(24) { "sample tag $it" })) }
        var state by mutableStateOf(MainUiState(libraryBooks = books, libraryLocations = listOf(StorageLocation("fixture", "Fixture"))))
        var writes = 0
        // No Compose test clock: frames and flings must run on the device's real Choreographer.
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var window: Window
            var width = 0
            var height = 0
            scenario.onActivity { activity ->
                window = activity.window
                activity.setContent { DoujinZipTheme(darkTheme = false, fontSizeSp = 16f) {
                LibraryScreen(state = state, tabHeader = { query, actions -> Row(Modifier.fillMaxWidth()) { Box(Modifier.weight(1f)) { query() }; actions() } },
                    contentPadding = PaddingValues(0.dp), onSync = {}, onQueryChange = {}, onToggleFavoritesFilter = {}, onExcludeSeriesChange = {},
                    onReadFilterChange = {}, onVisibilityFilterChange = {}, onSortChange = {}, onLocationChange = {}, onRefresh = {}, onOpenBook = {},
                    onToggleFavorite = {}, onToggleSeriesFavorite = {}, onToggleRead = {}, onAddFavorites = {}, onMarkRead = {}, onMarkUnread = {},
                    onAssignSeries = { _, _ -> }, onAppendSeries = { _, _ -> true }, onSetBooksHidden = { _, _ -> },
                    onDeleteBooks = {}, onRenameBook = { _, _ -> }, onRemoveBooksFromSeries = {}, onRenameSeries = { _, _ -> }, onMergeSeries = { _, _ -> },
                    onDeleteSeries = {}, onAutoCreateSeries = {}, onSeriesModeChange = {}, onSelectedSeriesChange = {}, onBackToSearch = {},
                    pageKey = "fixture", initialScrollAnchorKey = null, initialScrollIndex = 0, initialScrollOffset = 0,
                    onScrollChange = { anchor, index, offset ->
                        writes++
                        state = state.copy(galleryWorkspace = state.galleryWorkspace.updateLibraryScroll(state.galleryWorkspace.activeTab.currentPage.key, anchor, index, offset))
                    })
            } } }
            Thread.sleep(1500)
            scenario.onActivity { width = window.decorView.width; height = window.decorView.height }
            fun swipe() {
                val downTime = SystemClock.uptimeMillis()
                for (step in 0..25) {
                    val delay = downTime + step * 16 - SystemClock.uptimeMillis()
                    if (delay > 0) Thread.sleep(delay)
                    val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 25 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, width * .5f,
                        height * (.8f - .5f * step / 25), 0)
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                Thread.sleep(350)
            }
            swipe()
            Thread.sleep(500)
            val frames = mutableListOf<Double>()
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 1L) frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            }
            scenario.onActivity { writes = 0; window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
            repeat(8) { swipe() }
            Thread.sleep(500)
            scenario.onActivity { window.removeOnFrameMetricsAvailableListener(listener) }
            val sorted = frames.sorted()
            assertTrue("Frame metrics must be available", sorted.isNotEmpty())
            assertTrue(writes > 0)
            val result = JSONObject().put("device", android.os.Build.MODEL).put("frames", frames.size).put("stateWrites", writes)
                .put("p50Ms", sorted[sorted.size / 2]).put("p95Ms", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)])
                .put("over16ms", sorted.count { it > 16.67 }).put("network", false).put("input", "native-real-clock-16ms")
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nLIBRARY_SCROLL_BENCHMARK $result\n") })
            File(context.filesDir, "library-scroll-benchmark.json").writeText(result.toString(2))
        }
        cover.delete()
    }
}
