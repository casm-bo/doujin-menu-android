package com.doujinmenu.android.model

data class GalleryBorderStyle(val widthDp: Float = 1f, val colorArgb: Long = 0xFFB0A6BC) {
    init {
        require(widthDp.isFinite() && widthDp in 0f..6f)
        require(colorArgb in 0..0xFFFFFFFFL)
    }
}

enum class GalleryBorderKind { NORMAL, SERIES, FAVORITE }

data class GalleryBorders(
    val normal: GalleryBorderStyle = GalleryBorderStyle(),
    val series: GalleryBorderStyle = GalleryBorderStyle(colorArgb = 0xFF6750A4),
    val favorite: GalleryBorderStyle = GalleryBorderStyle(colorArgb = 0xFFE6A000),
) {
    fun styleFor(series: Boolean, favorite: Boolean): GalleryBorderStyle =
        if (favorite) this.favorite else if (series) this.series else normal
}

fun parseBorderColor(hex: String): Long? = hex.removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }
    ?.toLong(16)?.or(0xFF000000)
