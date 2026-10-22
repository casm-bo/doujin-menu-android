package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class AppFontSizeTest {
    @Test fun customSizeAcceptsFractionsAndRejectsInvalidOrUnreadableSizes() {
        assertEquals(18.5f, parseAppFontSize("18.5"))
        assertEquals(12f, parseAppFontSize("12"))
        assertEquals(28f, parseAppFontSize("28"))
        listOf("", "NaN", "Infinity", "-1", "11.9", "28.1", "16sp").forEach { assertNull(it, parseAppFontSize(it)) }
    }
}
