package com.doujinmenu.android.model

internal fun searchQueryTokens(query: String): List<String> {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var quoted = false
    var escaped = false
    for (char in query) {
        if (char == '"' && !escaped) quoted = !quoted
        if (!quoted && (char.isWhitespace() || char == ',')) {
            if (token.isNotEmpty()) { tokens += token.toString(); token.clear() }
        } else token.append(char)
        escaped = char == '\\' && !escaped
    }
    if (token.isNotEmpty()) tokens += token.toString()
    return tokens
}

internal fun searchTokenStart(query: String): Int {
    var start = 0
    var quoted = false
    var escaped = false
    query.forEachIndexed { index, char ->
        if (char == '"' && !escaped) quoted = !quoted
        if (!quoted && (char.isWhitespace() || char == ',')) start = index + 1
        escaped = char == '\\' && !escaped
    }
    return start
}

internal fun applySearchSuggestion(query: String, token: String): String {
    val start = searchTokenStart(query)
    return query.substring(0, start) + (if (query.substring(start).startsWith('-')) "-" else "") + token +
        (if (token.endsWith(':')) "" else ", ")
}

internal fun formatSearchQuery(query: String): String = searchQueryTokens(query).joinToString(", ")
