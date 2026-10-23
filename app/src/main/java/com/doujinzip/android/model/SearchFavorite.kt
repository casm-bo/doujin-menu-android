package com.doujinzip.android.model

data class SearchFavorite(
    val id: String,
    val name: String,
    val query: String,
    val languages: Set<String> = emptySet(),
)
