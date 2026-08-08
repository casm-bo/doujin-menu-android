package com.doujinmenu.android.model

import com.doujinmenu.android.ui.initialThumbnailVisibility
import com.doujinmenu.android.ui.nextCustomAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerPreferencesTest {
    @Test
    fun `thumbnail preference controls initial visibility only`() {
        assertEquals(true, initialThumbnailVisibility(ViewerPreferences()))
        assertEquals(
            false,
            initialThumbnailVisibility(ViewerPreferences(hideThumbnails = true)),
        )
    }

    @Test
    fun `default custom tap zones provide previous none and next actions in every row`() {
        val zones = ViewerTapZones()

        repeat(3) { row ->
            assertEquals(ViewerTapAction.PREVIOUS_PAGE, zones.actionAt(row * 3))
            assertEquals(ViewerTapAction.NONE, zones.actionAt(row * 3 + 1))
            assertEquals(ViewerTapAction.NEXT_PAGE, zones.actionAt(row * 3 + 2))
        }
    }

    @Test
    fun `changing one tap zone preserves all other zones`() {
        val original = ViewerTapZones()
        val changed = original.withAction(4, ViewerTapAction.NONE)

        assertEquals(ViewerTapAction.NONE, changed.actionAt(4))
        assertEquals(ViewerTapAction.PREVIOUS_PAGE, changed.actionAt(3))
        assertEquals(ViewerTapAction.NEXT_PAGE, changed.actionAt(5))
    }

    @Test
    fun `invalid persisted tap zones fall back per cell`() {
        val zones = ViewerTapZones(actions = emptyList())

        assertEquals(ViewerTapAction.PREVIOUS_PAGE, zones.actionAt(0))
        assertEquals(ViewerTapAction.NONE, zones.actionAt(4))
        assertEquals(ViewerTapAction.NEXT_PAGE, zones.actionAt(8))
    }

    @Test
    fun `swapping reading direction mirrors page actions but preserves none actions`() {
        val swapped = ViewerTapZones().swapPreviousAndNext()

        assertEquals(ViewerTapAction.NEXT_PAGE, swapped.actionAt(0))
        assertEquals(ViewerTapAction.NONE, swapped.actionAt(1))
        assertEquals(ViewerTapAction.PREVIOUS_PAGE, swapped.actionAt(2))
    }

    @Test
    fun `custom actions cycle previous toggle next and none`() {
        assertEquals(ViewerTapAction.TOGGLE_CONTROLS, ViewerTapAction.PREVIOUS_PAGE.nextCustomAction())
        assertEquals(ViewerTapAction.NEXT_PAGE, ViewerTapAction.TOGGLE_CONTROLS.nextCustomAction())
        assertEquals(ViewerTapAction.NONE, ViewerTapAction.NEXT_PAGE.nextCustomAction())
        assertEquals(ViewerTapAction.PREVIOUS_PAGE, ViewerTapAction.NONE.nextCustomAction())
    }
}
