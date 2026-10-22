package com.doujinzip.android.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryFontLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishHistoryAndContinueFitNormalLargeAndCustomFonts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        var size by mutableFloatStateOf(18f)
        val entry = RecentHistoryEntry("fixture", "Fixture title", 1, HistorySource.DEVICE, page = 5, pageCount = 30)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides english, LocalConfiguration provides english.resources.configuration) {
                DoujinZipTheme(fontSizeSp = size) {
                    Box(Modifier.fillMaxSize().requiredWidth(360.dp)) {
                        Box(Modifier.width(170.dp).align(Alignment.TopCenter)) { HistoryCard(entry, MainUiState(), true, {}, {}, {}) }
                        Box(Modifier.align(Alignment.BottomCenter)) { MainBottomNavigation(MainDestination.History) {} }
                    }
                }
            }
        }
        for (font in listOf(18f, 20f, 28f)) {
            compose.runOnIdle { size = font }
            for (label in listOf("History", "Continue")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
                assertEquals(1, layouts.size)
                val result = layouts.single()
                assertFalse("$label truncated at $font sp", result.multiParagraph.didExceedMaxLines || (0 until result.lineCount).any(result::isLineEllipsized))
                assertTrue("$label clipped at $font sp: size=${result.size}, lines=${(0 until result.lineCount).map { result.getLineLeft(it) to result.getLineRight(it) }}",
                    (0 until result.lineCount).all { result.getLineLeft(it) >= -1f && result.getLineRight(it) <= result.size.width + 1f })
                assertTrue("$label height clipped at $font sp", result.getLineBottom(result.lineCount - 1) <= result.size.height + 1f)
            }
        }
    }
}
