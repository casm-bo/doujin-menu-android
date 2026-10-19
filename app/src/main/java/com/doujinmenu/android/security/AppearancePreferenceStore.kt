package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.GalleryBorders
import com.doujinmenu.android.model.GalleryBorderStyle

class AppearancePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun loadBorders(): GalleryBorders {
        fun read(key: String, fallback: GalleryBorderStyle) = runCatching {
            GalleryBorderStyle(preferences.getFloat("${key}_width", fallback.widthDp), preferences.getLong("${key}_color", fallback.colorArgb))
        }.getOrDefault(fallback)
        val defaults = GalleryBorders()
        return GalleryBorders(read("normal", defaults.normal), read("series", defaults.series))
    }
    fun saveBorder(series: Boolean, style: GalleryBorderStyle) {
        val key = if (series) "series" else "normal"
        preferences.edit().putFloat("${key}_width", style.widthDp).putLong("${key}_color", style.colorArgb).apply()
    }
}
