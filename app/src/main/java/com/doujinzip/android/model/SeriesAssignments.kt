package com.doujinzip.android.model

fun appendSeriesMembers(
    assignments: Map<String, CustomSeriesAssignment>, bookIds: Set<String>, name: String, modifiedAt: Long,
): Map<String, CustomSeriesAssignment> {
    val affected = bookIds.mapNotNull { assignments[it]?.name }.toSet() + name
    var order = assignments.values.filter { it.name == name }.maxOfOrNull { it.order }?.plus(1) ?: 0
    val next = assignments.toMutableMap()
    bookIds.forEach { id -> if (next[id]?.name != name) next[id] = CustomSeriesAssignment(name, order++, modifiedAt) }
    next.entries.groupBy { it.value.name }.filterKeys { it in affected }.forEach { (series, members) ->
        members.sortedWith(compareBy({ it.value.order }, { it.key })).forEachIndexed { index, entry ->
            next[entry.key] = CustomSeriesAssignment(series, index, modifiedAt)
        }
    }
    return next
}

/** One replacement covers order and membership; excluded media remain in the library. */
fun replaceSeriesMembers(assignments: Map<String, CustomSeriesAssignment>, name: String, orderedIds: List<String>, modifiedAt: Long): Map<String, CustomSeriesAssignment> {
    require(name.isNotBlank() && orderedIds.distinct().size == orderedIds.size)
    val affectedNames = orderedIds.mapNotNull { assignments[it]?.name }.toSet() + name
    val next = assignments.filterValues { it.name != name }.toMutableMap()
    orderedIds.forEachIndexed { index, id -> next[id] = CustomSeriesAssignment(name, index, modifiedAt) }
    next.entries.groupBy { it.value.name }.filterKeys { it in affectedNames }.forEach { (series, members) ->
        members.sortedWith(compareBy({ it.value.order }, { it.key })).forEachIndexed { index, member ->
            next[member.key] = CustomSeriesAssignment(series, index, modifiedAt)
        }
    }
    return next
}

fun unionSeriesMetadata(books: List<LibraryBook>): LibraryMetadata = LibraryMetadata(
    artists = books.flatMap { it.metadata.artists }.distinct(), groups = books.flatMap { it.metadata.groups }.distinct(),
    series = books.flatMap { it.metadata.series }.distinct(), characters = books.flatMap { it.metadata.characters }.distinct(),
    tags = books.flatMap { it.metadata.tags }.distinct(), galleryType = books.mapNotNull { it.metadata.galleryType }.distinct().singleOrNull(),
    language = books.mapNotNull { it.metadata.language }.distinct().singleOrNull(),
)
