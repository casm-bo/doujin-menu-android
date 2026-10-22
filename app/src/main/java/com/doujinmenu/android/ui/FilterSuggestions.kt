package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook

private val filterWhitespace = Regex("\\s+")

internal fun normalizeFilterValue(value: String): String = value.trim().lowercase().replace(filterWhitespace, "_")

internal fun libraryFilterTokens(books: List<LibraryBook>): List<String> = buildSet {
    books.forEach { book ->
        book.metadata.artists.forEach { add("artist:${normalizeFilterValue(it)}") }
        book.metadata.groups.forEach { add("group:${normalizeFilterValue(it)}") }
        book.metadata.series.forEach { add("series:${normalizeFilterValue(it)}") }
        book.metadata.characters.forEach { add("character:${normalizeFilterValue(it)}") }
        book.metadata.tags.forEach {
            val token = normalizeFilterValue(it)
            add(if (token.substringBefore(':') in listOf("male", "female", "tag")) token else "tag:$token")
        }
        book.metadata.language?.let { add("language:${normalizeFilterValue(it)}") }
    }
}.toList()
