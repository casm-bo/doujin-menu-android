package com.doujinmenu.android.ui

import java.util.Locale

internal fun preferredLocalizedTitle(rawTitle: String, locale: Locale = Locale.getDefault()): String {
    val parts = rawTitle.split('|').map(String::trim).filter(String::isNotEmpty)
    if (parts.size < 2) return rawTitle.trim()
    val preferred = when (locale.language.lowercase()) {
        "ko" -> parts.firstOrNull { it.any(::isHangul) }
        "ja" -> parts.firstOrNull { it.any(::isJapaneseKana) }
            ?: parts.firstOrNull { it.any(::isCjk) && it.none(::isHangul) }
        "zh" -> parts.firstOrNull { it.any(::isCjk) && it.none(::isHangul) && it.none(::isJapaneseKana) }
        "en" -> parts.firstOrNull(::isPrimarilyLatin)
        else -> null
    }
    return preferred ?: parts.firstOrNull(::isPrimarilyLatin) ?: parts.first()
}

private fun isHangul(char: Char): Boolean = char.code in 0xAC00..0xD7A3 || char.code in 0x1100..0x11FF
private fun isJapaneseKana(char: Char): Boolean = char.code in 0x3040..0x30FF
private fun isCjk(char: Char): Boolean = char.code in 0x3400..0x9FFF

private fun isPrimarilyLatin(value: String): Boolean {
    val letters = value.filter(Char::isLetter)
    if (letters.isEmpty()) return false
    return letters.count { it.code <= 0x024F }.toFloat() / letters.length >= 0.7f
}
