package com.doujinmenu.android.model

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
