package com.doujinmenu.android.ui

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatGalleryPublishedDate(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
    val date = runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
    }.recoverCatching {
        OffsetDateTime.parse(raw).toLocalDate()
    }.recoverCatching {
        LocalDate.parse(raw.take(10))
    }.getOrNull() ?: return raw
    return date.format(GALLERY_DATE_FORMATTER)
}

private val GALLERY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd")
