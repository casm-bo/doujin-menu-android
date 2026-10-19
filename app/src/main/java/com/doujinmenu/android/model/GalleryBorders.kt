package com.doujinmenu.android.model

data class GalleryBorderStyle(val widthDp: Float = 1f, val colorArgb: Long = 0xFFB0A6BC) {
    init {
        require(widthDp.isFinite() && widthDp in 0f..6f)
        require(colorArgb in 0..0xFFFFFFFFL)
    }
}

data class GalleryBorders(val normal: GalleryBorderStyle = GalleryBorderStyle(), val series: GalleryBorderStyle = GalleryBorderStyle(colorArgb = 0xFF6750A4))

fun parseBorderColor(hex: String): Long? = hex.removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }
    ?.toLong(16)?.or(0xFF000000)
