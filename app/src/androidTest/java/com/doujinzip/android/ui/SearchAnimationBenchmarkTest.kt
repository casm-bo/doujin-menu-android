package com.doujinzip.android.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.BrowserWorkspace
import com.doujinzip.android.model.SearchQueryEntry
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real-clock search/IME transitions with the same cached browsing content each run. */
class SearchAnimationBenchmarkTest {
    @Test fun expandAndCollapseSearchWithBrowsingVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val search = InlineSearchState()
        val tagActions = InstrumentationRegistry.getArguments().getString("tagActions") == "true"
        val query = List(12) { "tag:sample_$it" }.joinToString(", ")
        var edits = 0
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var window: Window
            scenario.onActivity { activity ->
                window = activity.window
                activity.setContent {
                    val themed = remember { android.view.ContextThemeWrapper(activity, R.style.Theme_DoujinZip) }
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides themed) {
                        DoujinZipTheme(darkTheme = false, fontSizeSp = 16f) {
                            InlineSearchLayout(search) { Column {
                                BrowserTabStrip(BrowserWorkspace.initial(), {}, {}, remember { BrowserTabPreviewCache(context) },
                                    titleContent = { UnifiedSearchField(query, { edits++ }, "Fixture query", {}, {},
                                        List(12) { SearchQueryEntry("tag:history_$it") }) })
                                LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                                    items(100, key = { it }) { index ->
                                        WorkCard("Fixture $index", "Artist", "50 pages", List(20) { CardTag(tagDisplayInfo("tag-$it"), if (tagActions) ({}) else null) },
                                            "", grid = true, onClick = {}) { Text("Cover") }
                                    }
                                }
                            } }
                        }
                    }
                }
            }
            fun toggle(expanded: Boolean) { scenario.onActivity { search.expanded = expanded }; Thread.sleep(700) }
            Thread.sleep(1000)
            toggle(true); toggle(false)
            val frames = mutableListOf<Double>()
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 1L) frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            }
            scenario.onActivity { window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
            val trace = InstrumentationRegistry.getArguments().getString("traceSearch") == "true"
            if (trace) android.os.Debug.startMethodTracingSampling(java.io.File(context.filesDir, "search-animation.trace").absolutePath, 32 * 1024 * 1024, 1_000)
            repeat(5) { toggle(true); toggle(false) }
            if (trace) android.os.Debug.stopMethodTracing()
            scenario.onActivity { window.removeOnFrameMetricsAvailableListener(listener) }
            val sorted = frames.sorted()
            assertTrue(sorted.isNotEmpty())
            assertEquals("Binding the query must not run a search/filter", 0, edits)
            val result = JSONObject().put("frames", sorted.size).put("p50Ms", sorted[sorted.size / 2])
                .put("p95Ms", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)])
                .put("over16ms", sorted.count { it > 16.67 }).put("keyboard", true).put("network", false).put("tagActions", tagActions)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nSEARCH_ANIMATION_BENCHMARK $result\n") })
        }
    }
}
