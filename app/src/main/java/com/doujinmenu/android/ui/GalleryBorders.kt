package com.doujinmenu.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.model.GalleryBorders

val LocalGalleryBorders = staticCompositionLocalOf { GalleryBorders() }

@Composable
internal fun galleryCardBorder(series: Boolean = false): BorderStroke? {
    val borders = LocalGalleryBorders.current
    val style = if (series) borders.series else borders.normal
    return if (style.widthDp == 0f) null else BorderStroke(style.widthDp.dp, Color(style.colorArgb))
}
