package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionIsAccessibleAndDoesNotChangeCardGeometry() {
        var selected by mutableStateOf(false)
        compose.setContent { DoujinMenuTheme {
            WorkCard("Selected book", "Artist", "Pages", emptyList(), "", grid = true,
                selected = selected, onClick = { selected = !selected }) { Text("Cover") }
        } }
        val before = compose.onNodeWithText("Selected book").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Selected book").performClick()
        compose.onNode(isSelected()).assertExists()
        assertEquals(before, compose.onNodeWithText("Selected book").getUnclippedBoundsInRoot())
    }

    @Test fun cardsKeepEqualHeightsAcrossColumnsFontsAndListAndOnlyLayOutFittingTags() {
        // Equal-weight columns can differ by one physical pixel; aspect-ratio rounding adds at most two height pixels.
        val gridTolerance = 2f / InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density + .01f
        var font by mutableFloatStateOf(16f)
        var columns by mutableIntStateOf(2)
        var grid by mutableStateOf(true)
        compose.setContent { DoujinMenuTheme(fontSizeSp = font) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                @Composable fun Card(index: Int, modifier: Modifier) {
                    Box(modifier.testTag("card-$index")) {
                        WorkCard(title = if (index == 0) "Short" else "Long title repeated many times to fill both lines with ellipsis",
                            artist = "Artist", metadata = "50 pages", tags = if (index == 0) emptyList() else List(60) { CardTag(tagDisplayInfo("tail-$it")) },
                            footer = "", grid = grid, onClick = {}) { Text("Cover") }
                    }
                }
                if (grid) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(columns) { Card(it, Modifier.weight(1f)) }
                } else Column { Card(0, Modifier.fillMaxWidth()); Card(1, Modifier.fillMaxWidth()) }
            }
        } }
        for (size in listOf(16f, 18f, 20f, 28f)) for (count in listOf(2, 3, 4)) {
            compose.runOnIdle { font = size; columns = count }
            compose.waitForIdle()
            val first = compose.onNodeWithTag("card-0").getUnclippedBoundsInRoot()
            val second = compose.onNodeWithTag("card-1").getUnclippedBoundsInRoot()
            assertEquals((first.bottom - first.top).value, (second.bottom - second.top).value, gridTolerance)
            compose.onNodeWithText("tail-59", useUnmergedTree = true).assertDoesNotExist()
        }
        compose.runOnIdle { grid = false }
        compose.waitForIdle()
        assertEquals(compose.onNodeWithTag("card-0").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value },
            compose.onNodeWithTag("card-1").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }, .5f)
    }

    @Test fun customCardAndToolbarCanHaveOppositeBrightnessWithoutInvisibleCardText() {
        var ratio = 0.0
        compose.setContent { DoujinMenuTheme(darkTheme = false, style = AppStyle(light = AppPalette(0xFF8844FF, 0xFFEEEEEE, 0xFFFFFFFF, 0xFF000000, 0xFF111111))) {
            val colors = appCardColors()
            ratio = colorContrast(colors.contentColor.toArgb().toLong() and 0xFFFFFFFF, colors.containerColor.toArgb().toLong() and 0xFFFFFFFF)
            WorkCard("Card", "Artist", "Pages", emptyList(), "", grid = true, onClick = {}) { Text("Cover") }
        } }
        compose.waitForIdle()
        assertTrue(ratio >= 4.5)
    }
}
