package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.DEFAULT_FONT_SIZE_SP
import com.doujinmenu.android.model.parseAppFontSize
import com.doujinmenu.android.model.GalleryBorderKind
import com.doujinmenu.android.model.GalleryBorders
import com.doujinmenu.android.model.GalleryBorderStyle

class AppearancePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun loadFontSize(): Float = runCatching { parseAppFontSize(preferences.getFloat("font_size", DEFAULT_FONT_SIZE_SP).toString()) }.getOrNull() ?: DEFAULT_FONT_SIZE_SP
    fun saveFontSize(size: Float) {
        require(parseAppFontSize(size.toString()) != null)
        preferences.edit().putFloat("font_size", size).apply()
    }
    fun loadBorders(): GalleryBorders {
        fun read(key: String, fallback: GalleryBorderStyle) = runCatching {
            GalleryBorderStyle(preferences.getFloat("${key}_width", fallback.widthDp), preferences.getLong("${key}_color", fallback.colorArgb))
        }.getOrDefault(fallback)
        val defaults = GalleryBorders()
        return GalleryBorders(read("normal", defaults.normal), read("series", defaults.series), read("favorite", defaults.favorite))
    }
    fun saveBorder(kind: GalleryBorderKind, style: GalleryBorderStyle) {
        val key = kind.name.lowercase()
        preferences.edit().putFloat("${key}_width", style.widthDp).putLong("${key}_color", style.colorArgb).apply()
    }
}
