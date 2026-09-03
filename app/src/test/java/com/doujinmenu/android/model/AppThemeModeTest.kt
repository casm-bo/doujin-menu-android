package com.doujinmenu.android.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun systemModeFollowsSystemTheme() {
        assertTrue(AppThemeMode.SYSTEM.usesDarkTheme(systemInDarkTheme = true))
        assertFalse(AppThemeMode.SYSTEM.usesDarkTheme(systemInDarkTheme = false))
    }

    @Test
    fun explicitModesOverrideSystemTheme() {
        assertFalse(AppThemeMode.LIGHT.usesDarkTheme(systemInDarkTheme = true))
        assertTrue(AppThemeMode.DARK.usesDarkTheme(systemInDarkTheme = false))
    }
}
