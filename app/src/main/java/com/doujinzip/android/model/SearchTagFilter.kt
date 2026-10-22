package com.doujinzip.android.model

enum class SearchTagFilter(val tags: Set<String>) {
    BL(setOf("tag:yaoi")),
    GORE(setOf("tag:guro", "tag:gore")),
    LOLI(setOf("female:lolicon", "tag:lolicon")),
}

internal fun parseBlockedSearchTags(query: String): Set<String> {
    val tokens=searchQueryTokens(query)
    require(tokens.size<=50)
    return tokens.map { token ->
        val raw=token.removePrefix("-").lowercase(java.util.Locale.ROOT)
        val type=if(':' in raw) raw.substringBefore(':') else "tag"
        val name=raw.substringAfter(':',raw).removeSurrounding("\"").replace(' ','_')
        require(type in setOf("tag","female","male") && name.length in 1..100 && name.matches(Regex("[\\p{L}\\p{N}_./-]+")))
        "$type:$name"
    }.toSet()
}

internal fun applySearchTagFilters(query: String, filters: Set<SearchTagFilter>, custom: Set<String> = emptySet()): String =
    (searchQueryTokens(query) + (filters.sortedBy { it.ordinal }.flatMap { it.tags } + custom.sorted()).map { "-$it" }).distinct().joinToString(" ")
