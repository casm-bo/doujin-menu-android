package com.doujinmenu.android.model

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class SearchSubscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val query: String,
    val languages: Set<String> = emptySet(),
    val enabled: Boolean = false,
    val deleted: Boolean = false,
    val version: Long = 0,
) {
    val queries: List<String> get() {
        // Explicit exclusions remain conditions even when the language picker is used.
        val exclusions = query.split(Regex("\\s+")).filter { it.startsWith("-language:") }
        return queriesWithPreferredLanguages(query, languages).map { (listOf(it) + exclusions).distinct().joinToString(" ") }
    }
    val conditionKey get() = queries.map { it.split(Regex("\\s+")).filter(String::isNotBlank).distinct().sorted().joinToString(" ") }.sorted().joinToString("\n")
    fun validate() {
        require(name.isNotBlank() && name.length <= 200) { "이름은 1~200자로 입력하세요." }
        require(query.isNotBlank() && query.length <= 2000) { "검색조건은 1~2000자로 입력하세요." }
        require(languages.size <= 20 && languages.all { it.matches(Regex("[a-z0-9_-]{1,50}")) }) { "언어 설정이 올바르지 않습니다." }
        if (enabled && !deleted) queries.forEach { validateSubscriptionQuery(it) }
    }
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("query", query)
        .put("languages", JSONArray(languages.sorted())).put("enabled", enabled).put("deleted", deleted).put("version", version)

    companion object {
        fun fromJson(value: JSONObject): SearchSubscription = SearchSubscription(
            id = value.getString("id"), name = value.getString("name"), query = value.getString("query"),
            languages = value.optJSONArray("languages")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty(),
            enabled = value.getBoolean("enabled"), deleted = value.optBoolean("deleted"), version = value.optLong("version"),
        ).also { require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))); require(it.version >= 0); it.validate() }
    }
}

fun validateSubscriptionQuery(query: String) {
    val terms = query.split(Regex("\\s+")).filter(String::isNotBlank)
    val tag = Regex("-?(artist|group|series|character|type|tag|male|female|language):[a-z0-9_.-]+")
    require(terms.isNotEmpty() && terms.all { tag.matches(it) } && terms.any { !it.startsWith('-') }) {
        "신작 확인은 포함 태그가 있는 조건만 지원합니다. 제목 키워드와 정렬 조건을 제거하세요."
    }
}

fun subscriptionNewIds(ids: Collection<Long>, baseline: Long?): List<Long> =
    if (baseline == null) emptyList() else ids.asSequence().filter { it > baseline }.distinct().sortedDescending().toList()
