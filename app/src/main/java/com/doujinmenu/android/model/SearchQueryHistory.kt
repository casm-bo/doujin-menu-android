package com.doujinmenu.android.model

data class SearchQueryEntry(val query: String, val languages: Set<String> = emptySet())

fun List<SearchQueryEntry>.recordQuery(entry: SearchQueryEntry): List<SearchQueryEntry> {
    val normalized = entry.copy(query = normalizeSearchSeparators(entry.query))
    val key = searchTabKey(normalized.query, normalized.languages)
    return (listOf(normalized) + filterNot { searchTabKey(it.query, it.languages) == key }).take(50)
}
