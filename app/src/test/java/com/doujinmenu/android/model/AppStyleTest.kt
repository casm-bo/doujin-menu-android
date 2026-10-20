package com.doujinmenu.android.model

import com.doujinmenu.android.security.appStyleFromJson
import com.doujinmenu.android.security.appStyleToJson
import org.junit.Assert.*
import org.junit.Test

class AppStyleTest {
    @Test fun everyPaletteKeepsReadableForegroundsAndIndependentModeOverrides() {
        AppStylePreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val palette = preset.palette(dark)
                listOf(palette.background, palette.toolbar, palette.card, palette.accent).forEach { background ->
                    assertTrue(colorContrast(readableAccent(palette.accent, background), background) >= 4.5)
                    assertTrue(colorContrast(readableForeground(palette.text, background), background) >= 4.5)
                }
                val style = AppStyle(preset).withPalette(dark, palette.copy(card = 0xFF123456))
                assertEquals(preset.palette(!dark), style.palette(!dark))
                assertEquals(style, appStyleFromJson(appStyleToJson(style)))
            }
        }
    }
    @Test fun corruptSettingsFallBackAndCustomTextNeverBecomesInvisible() {
        assertEquals(AppStyle(), appStyleFromJson("{\"preset\":\"missing\"}"))
        assertEquals(AppStyle(), appStyleFromJson(null))
        assertEquals(0xFF000000, readableForeground(0xFFFFFFFF, 0xFFFFFFFF))
        assertEquals(0xFFFFFFFF, readableForeground(0xFF000000, 0xFF000000))
    }
}
