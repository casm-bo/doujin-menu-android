package com.doujinzip.android.model

const val DEFAULT_FONT_SIZE_SP = 18f
fun parseAppFontSize(value: String): Float? = value.toFloatOrNull()?.takeIf { it.isFinite() && it in 12f..28f }
