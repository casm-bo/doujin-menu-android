package com.doujinmenu.android.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.doujinmenu.android.model.*
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeContrastTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unwrappedContentAndSurfacesFollowEveryPresetAndModeChange() {
        var preset by mutableStateOf(AppStylePreset.AMBER)
        var dark by mutableStateOf(true)
        var rootContrast = 0.0
        var surfaceContrast = 0.0
        compose.setContent { DoujinMenuTheme(darkTheme = dark, style = AppStyle(preset)) {
            fun color(value: androidx.compose.ui.graphics.Color) = value.toArgb().toLong() and 0xFFFFFFFF
            rootContrast = colorContrast(color(LocalContentColor.current), color(MaterialTheme.colorScheme.background))
            Surface { surfaceContrast = colorContrast(color(LocalContentColor.current), color(MaterialTheme.colorScheme.surface)) }
        } }
        AppStylePreset.entries.forEach { style -> listOf(true, false).forEach { mode ->
            compose.runOnIdle { preset = style; dark = mode }
            compose.runOnIdle {
                assertTrue("$style dark=$mode root contrast=$rootContrast", rootContrast >= 4.5)
                assertTrue("$style dark=$mode surface contrast=$surfaceContrast", surfaceContrast >= 4.5)
            }
        } }
    }
}
