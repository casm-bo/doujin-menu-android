package com.doujinmenu.android.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FilterSuggestion(
    val token: String,
    val displayName: String,
)

class HitomiSuggestionClient {
    suspend fun getSuggestions(type: String, partial: String): List<FilterSuggestion> =
        withContext(Dispatchers.IO) {
            when (type) {
                "type" -> TYPES.toSuggestions(type, partial)
                "language" -> LANGUAGES.toSuggestions(type, partial)
                in REMOTE_TYPES -> getRemoteSuggestions(type, partial)
                else -> emptyList()
            }
        }

    private fun getRemoteSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val first = partial.firstOrNull()?.lowercaseChar() ?: return emptyList()
        val bucket = if (first.isDigit()) "0-9" else first.toString()
        if (!first.isLetterOrDigit()) return emptyList()
        val listName = when (type) {
            "artist" -> "allartists"
            "series" -> "allseries"
            "character" -> "allcharacters"
            "group" -> "allgroups"
            "tag", "male", "female" -> "alltags"
            else -> return emptyList()
        }
        val connection = URL("https://gold-usergeneratedcontent.net/$listName-$bucket.html")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Referer", "https://hitomi.la/")
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLinks(html, type, partial)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLinks(html: String, type: String, partial: String): List<FilterSuggestion> {
        val normalizedPartial = normalizeValue(partial)
        return HREF_PATTERN.findAll(html)
            .mapNotNull { match ->
                val path = URLDecoder.decode(match.groupValues[1], Charsets.UTF_8.name())
                val value = when (type) {
                    "male", "female" -> path
                        .takeIf { it.startsWith("tag/$type:") }
                        ?.substringAfter("tag/$type:")
                    "tag" -> path
                        .takeIf {
                            it.startsWith("tag/") &&
                                !it.startsWith("tag/male:") &&
                                !it.startsWith("tag/female:")
                        }
                        ?.substringAfter("tag/")
                    else -> path
                        .takeIf { it.startsWith("$type/") }
                        ?.substringAfter("$type/")
                }?.removeSuffix("-all") ?: return@mapNotNull null
                val normalized = normalizeValue(value)
                if (!normalized.startsWith(normalizedPartial)) return@mapNotNull null
                FilterSuggestion(
                    token = "$type:$normalized",
                    displayName = value.replace('_', ' '),
                )
            }
            .distinctBy { it.token }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun List<String>.toSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val normalizedPartial = normalizeValue(partial)
        return asSequence()
            .filter { normalizeValue(it).startsWith(normalizedPartial) }
            .map { FilterSuggestion("$type:${normalizeValue(it)}", it) }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun normalizeValue(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "_")

    private companion object {
        val REMOTE_TYPES = setOf("artist", "series", "character", "group", "tag", "male", "female")
        val TYPES = listOf("doujinshi", "manga", "artistcg", "gamecg", "imageset", "anime")
        val LANGUAGES = listOf(
            "korean", "japanese", "english", "chinese", "spanish", "french", "german",
            "italian", "portuguese", "russian", "thai", "vietnamese", "indonesian",
        )
        val HREF_PATTERN = Regex("href=[\\\"']/?([^\\\"']+)\\.html[\\\"']", RegexOption.IGNORE_CASE)
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_RESULTS = 12
    }
}
