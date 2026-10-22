package com.doujinmenu.android.model

enum class SearchTagFilter(val tags: Set<String>) {
    BL(setOf("tag:yaoi")),
    GORE(setOf("tag:guro", "tag:gore")),
    LOLI(setOf("female:lolicon", "tag:lolicon")),
}

internal fun applySearchTagFilters(query: String, filters: Set<SearchTagFilter>): String =
    (searchQueryTokens(query) + filters.sortedBy { it.ordinal }.flatMap { it.tags }.map { "-$it" }).distinct().joinToString(" ")
