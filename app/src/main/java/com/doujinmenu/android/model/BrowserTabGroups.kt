package com.doujinmenu.android.model

import java.util.UUID

data class BrowserTabGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "새 그룹",
    val colorIndex: Int = 0,
    val archivedTabs: List<BrowserTab> = emptyList(),
) {
    val isArchived: Boolean get() = archivedTabs.isNotEmpty()
}

sealed interface BrowserOverviewItem {
    val key: String

    data class Tab(val tab: BrowserTab) : BrowserOverviewItem {
        override val key: String get() = "tab:${tab.id}"
    }

    data class Group(val group: BrowserTabGroup, val tabs: List<BrowserTab>) : BrowserOverviewItem {
        override val key: String get() = "group:${group.id}"
    }
}

val BrowserWorkspace.overviewItems: List<BrowserOverviewItem>
    get() {
        val activeGroups = groups.filterNot { it.isArchived }.associateBy { it.id }
        val groupedTabs = tabs.filter { it.groupId in activeGroups }.groupBy { it.groupId }
        val seen = mutableSetOf<String>()
        val items = tabs.mapNotNull { tab ->
            val group = activeGroups[tab.groupId]
            if (group == null) BrowserOverviewItem.Tab(tab)
            else if (seen.add(group.id)) BrowserOverviewItem.Group(group, groupedTabs[group.id].orEmpty())
            else null
        }
        val positions = overviewOrder.withIndex().associate { it.value to it.index }
        return items.sortedBy { positions[it.key] ?: Int.MAX_VALUE }
    }

fun BrowserWorkspace.createGroup(
    tabIds: Set<String>,
    name: String = "새 그룹",
    colorIndex: Int = 0,
): BrowserWorkspace {
    if (tabs.none { it.id in tabIds }) return this
    val group = BrowserTabGroup(name = name.trim().ifBlank { "새 그룹" }, colorIndex = colorIndex.coerceIn(0, 5))
    val oldKeys = overviewItems.map { it.key }
    val firstKey = overviewItems.firstOrNull { item ->
        when (item) {
            is BrowserOverviewItem.Tab -> item.tab.id in tabIds
            is BrowserOverviewItem.Group -> item.tabs.any { it.id in tabIds }
        }
    }?.key
    val order = oldKeys.toMutableList().apply { add(indexOf(firstKey).coerceAtLeast(0), "group:${group.id}") }
    return copy(
        tabs = tabs.map { if (it.id in tabIds) it.copy(groupId = group.id) else it },
        groups = groups + group,
        overviewOrder = order,
    ).normalizeGroups()
}

fun BrowserWorkspace.assignTabToGroup(tabId: String, groupId: String?): BrowserWorkspace {
    if (groupId != null && groups.none { it.id == groupId && !it.isArchived }) return this
    return copy(tabs = tabs.map { if (it.id == tabId) it.copy(groupId = groupId) else it }).normalizeGroups()
}

fun BrowserWorkspace.updateGroup(groupId: String, name: String, colorIndex: Int): BrowserWorkspace = copy(
    groups = groups.map { group ->
        if (group.id == groupId) group.copy(name = name.trim().ifBlank { "새 그룹" }, colorIndex = colorIndex.coerceIn(0, 5))
        else group
    },
)

fun BrowserWorkspace.ungroup(groupId: String): BrowserWorkspace {
    val group = groups.firstOrNull { it.id == groupId && !it.isArchived } ?: return this
    val memberKeys = tabs.filter { it.groupId == group.id }.map { "tab:${it.id}" }
    val order = overviewItems.flatMap { if (it.key == "group:$groupId") memberKeys else listOf(it.key) }
    return copy(
        tabs = tabs.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
        groups = groups.filterNot { it.id == groupId },
        overviewOrder = order,
    ).normalizeGroups()
}

fun BrowserWorkspace.archiveGroup(
    groupId: String,
    fallback: BrowserPage = BrowserPage.Search(),
): BrowserWorkspace {
    val members = tabs.filter { it.groupId == groupId }
    if (members.isEmpty() || groups.none { it.id == groupId && !it.isArchived }) return this
    val snapshots = members.map { tab ->
        tab.copy(history = tab.history.map { page ->
            if (page is BrowserPage.Search) page.copy(results = emptyList(), resultIds = emptyList()) else page
        })
    }
    val remaining = tabs.filterNot { it.groupId == groupId }.ifEmpty { listOf(BrowserTab(history = listOf(fallback))) }
    return copy(
        tabs = remaining,
        activeTabId = activeTabId.takeIf { id -> remaining.any { it.id == id } } ?: remaining.first().id,
        groups = groups.map { if (it.id == groupId) it.copy(archivedTabs = snapshots) else it },
    ).normalizeGroups()
}

fun BrowserWorkspace.restoreGroup(groupId: String): BrowserWorkspace {
    val group = groups.firstOrNull { it.id == groupId && it.isArchived } ?: return this
    return copy(
        tabs = tabs + group.archivedTabs,
        activeTabId = group.archivedTabs.first().id,
        groups = groups.map { if (it.id == groupId) it.copy(archivedTabs = emptyList()) else it },
    ).normalizeGroups()
}

fun BrowserWorkspace.deleteArchivedGroup(groupId: String): BrowserWorkspace = copy(
    groups = groups.filterNot { it.id == groupId && it.isArchived },
)

fun BrowserWorkspace.moveOverviewItem(key: String, targetKey: String): BrowserWorkspace {
    val order = overviewItems.map { it.key }.toMutableList()
    val from = order.indexOf(key)
    val to = order.indexOf(targetKey)
    if (from < 0 || to < 0 || from == to) return this
    order.add(to, order.removeAt(from))
    return copy(overviewOrder = order)
}

fun BrowserWorkspace.moveTabTo(tabId: String, targetTabId: String): BrowserWorkspace {
    val from = tabs.indexOfFirst { it.id == tabId }
    val to = tabs.indexOfFirst { it.id == targetTabId }
    if (from < 0 || to < 0 || from == to || tabs[from].groupId != tabs[to].groupId) return this
    val reordered = tabs.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(tabs = reordered)
}

internal fun BrowserWorkspace.normalizeGroups(): BrowserWorkspace {
    val validGroups = groups.distinctBy { it.id }.filter { group ->
        group.isArchived || tabs.any { it.groupId == group.id }
    }
    val activeGroupIds = validGroups.filterNot { it.isArchived }.mapTo(mutableSetOf()) { it.id }
    val normalized = copy(
        tabs = tabs.map { if (it.groupId != null && it.groupId !in activeGroupIds) it.copy(groupId = null) else it },
        groups = validGroups,
    )
    val keys = normalized.overviewItems.map { it.key }
    return normalized.copy(overviewOrder = overviewOrder.filter { it in keys }.distinct())
}
