package com.doujinmenu.android.model

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun usesDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}
