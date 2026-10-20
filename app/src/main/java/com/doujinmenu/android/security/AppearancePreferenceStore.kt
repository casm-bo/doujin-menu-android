package com.doujinmenu.android.security

import android.content.Context
import com.doujinmenu.android.model.DEFAULT_FONT_SIZE_SP
import com.doujinmenu.android.model.parseAppFontSize
import com.doujinmenu.android.model.GalleryBorderKind
import com.doujinmenu.android.model.GalleryBorders
import com.doujinmenu.android.model.GalleryBorderStyle
import com.doujinmenu.android.model.AppPalette
import com.doujinmenu.android.model.AppStyle
import com.doujinmenu.android.model.AppStylePreset
import org.json.JSONObject

class AppearancePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun loadHistoryGrid(): Boolean = preferences.getBoolean("history_grid", false)
    fun saveHistoryGrid(grid: Boolean) { preferences.edit().putBoolean("history_grid", grid).apply() }
    fun loadStyle(): AppStyle = appStyleFromJson(preferences.getString("style", null))
    fun saveStyle(style: AppStyle) { preferences.edit().putString("style", appStyleToJson(style)).apply() }
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

internal fun appStyleToJson(style: AppStyle): String {
    fun AppPalette.json() = JSONObject().put("accent", accent).put("background", background)
        .put("toolbar", toolbar).put("card", card).put("text", text)
    return JSONObject().put("preset", style.preset.name).put("light", style.light?.json()).put("dark", style.dark?.json()).toString()
}

internal fun appStyleFromJson(json: String?): AppStyle = runCatching {
    val data = JSONObject(requireNotNull(json))
    fun palette(key: String): AppPalette? = data.optJSONObject(key)?.let {
        AppPalette(it.getLong("accent"), it.getLong("background"), it.getLong("toolbar"), it.getLong("card"), it.getLong("text"))
    }
    AppStyle(AppStylePreset.valueOf(data.getString("preset")), palette("light"), palette("dark"))
}.getOrDefault(AppStyle())
