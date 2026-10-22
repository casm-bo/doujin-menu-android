package com.doujinzip.android.model

import kotlin.math.pow

data class AppPalette(val accent: Long, val background: Long, val toolbar: Long, val card: Long, val text: Long) {
    init { require(listOf(accent, background, toolbar, card, text).all { it in 0xFF000000..0xFFFFFFFF }) }
}

enum class AppStylePreset {
    DEFAULT, OCEAN, AMBER, CATPPUCCIN;
    // Desktop theme CSS colors converted from OKLCH to sRGB; Android uses semantic surfaces.
    fun palette(dark: Boolean): AppPalette = when (this) {
    DEFAULT -> if (dark) AppPalette(0xFFD0BCFF, 0xFF101014, 0xFF22212A, 0xFF302E38, 0xFFF4EFFA)
        else AppPalette(0xFF6750A4, 0xFFEAE7F0, 0xFFFAF8FF, 0xFFFFFFFF, 0xFF211D29)
    OCEAN -> if (dark) AppPalette(0xFF34D399, 0xFF0F172A, 0xFF2D3748, 0xFF1E293B, 0xFFD1D5DB) else AppPalette(0xFF22C55E, 0xFFF0F8FF, 0xFFE0F2FE, 0xFFFFFFFF, 0xFF374151)
    AMBER -> if (dark) AppPalette(0xFFF59E0B, 0xFF171717, 0xFF262626, 0xFF323232, 0xFFE5E5E5) else AppPalette(0xFFF59E0B, 0xFFF0ECE5, 0xFFFFFAF0, 0xFFFFFFFF, 0xFF262626)
    CATPPUCCIN -> if (dark) AppPalette(0xFFCBA6F7, 0xFF181825, 0xFF585B70, 0xFF1E1E2E, 0xFFCDD6F4) else AppPalette(0xFF8839EF, 0xFFEFF1F5, 0xFFCCD0DA, 0xFFFFFFFF, 0xFF4C4F69)
    }
}

data class AppStyle(val preset: AppStylePreset = AppStylePreset.DEFAULT, val light: AppPalette? = null, val dark: AppPalette? = null) {
    fun palette(isDark: Boolean): AppPalette = (if (isDark) dark else light) ?: preset.palette(isDark)
    fun withPalette(isDark: Boolean, palette: AppPalette) = if (isDark) copy(dark = palette) else copy(light = palette)
}

fun colorContrast(first: Long, second: Long): Double {
    fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    val a = luminance(first); val b = luminance(second)
    return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}

fun readableForeground(preferred: Long, background: Long): Long = when {
    colorContrast(preferred, background) >= 4.5 -> preferred
    colorContrast(0xFF000000, background) >= colorContrast(0xFFFFFFFF, background) -> 0xFF000000
    else -> 0xFFFFFFFF
}

fun readableAccent(accent: Long, background: Long): Long {
    val target = readableForeground(accent, background)
    if (target == accent) return accent
    for (step in 1..100) {
        val color = (listOf(16, 8, 0).fold(0xFF000000L) { result, shift ->
            val from = ((accent shr shift) and 255).toInt()
            val to = ((target shr shift) and 255).toInt()
            result or ((from + (to - from) * step / 100).toLong() shl shift)
        })
        if (colorContrast(color, background) >= 4.5) return color
    }
    return target
}
