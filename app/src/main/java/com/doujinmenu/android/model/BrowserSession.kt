package com.doujinmenu.android.model

import java.util.UUID

sealed interface BrowserPage {
    val key: String

    data class Search(
        override val key: String = UUID.randomUUID().toString(),
        val query: String = "",
        val preferredLanguages: Set<String> = emptySet(),
        val submittedQuery: String = "",
        val submittedQueries: List<String> = emptyList(),
        val resultIds: List<Long> = emptyList(),
        val results: List<GallerySummary> = emptyList(),
        val currentPage: Int = 0,
        val hasNextPage: Boolean = false,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : BrowserPage

    data class OnlineGallery(
        val galleryId: Long,
        val title: String,
        override val key: String = UUID.randomUUID().toString(),
    ) : BrowserPage

    data class LibraryBook(
        val bookId: String,
        val title: String,
        override val key: String = UUID.randomUUID().toString(),
    ) : BrowserPage

    data class OnlineReader(
        val galleryId: Long,
        val title: String,
        val startPage: Int,
        override val key: String = UUID.randomUUID().toString(),
    ) : BrowserPage

    data class LibraryReader(
        val bookId: String,
        val title: String,
        val startPage: Int,
        override val key: String = UUID.randomUUID().toString(),
    ) : BrowserPage
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val history: List<BrowserPage> = listOf(BrowserPage.Search()),
    val currentIndex: Int = 0,
) {
    val currentPage: BrowserPage
        get() = history[currentIndex.coerceIn(history.indices)]

    val canGoBack: Boolean
        get() = currentIndex > 0
}

data class BrowserWorkspace(
    val tabs: List<BrowserTab>,
    val activeTabId: String,
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    companion object {
        fun initial(page: BrowserPage = BrowserPage.Search()): BrowserWorkspace {
            val tab = BrowserTab(history = listOf(page))
            return BrowserWorkspace(tabs = listOf(tab), activeTabId = tab.id)
        }
    }
}

fun BrowserWorkspace.selectTab(tabId: String): BrowserWorkspace =
    if (tabs.any { it.id == tabId }) copy(activeTabId = tabId) else this

fun BrowserWorkspace.openTab(
    page: BrowserPage = BrowserPage.Search(),
    maxTabs: Int = 20,
): BrowserWorkspace {
    val tab = BrowserTab(history = listOf(page))
    val retained = if (tabs.size < maxTabs) tabs else tabs.drop(1)
    return copy(tabs = retained + tab, activeTabId = tab.id)
}

fun BrowserWorkspace.closeTab(tabId: String): BrowserWorkspace {
    val closingIndex = tabs.indexOfFirst { it.id == tabId }
    if (closingIndex < 0) return this
    if (tabs.size == 1) return BrowserWorkspace.initial()

    val remaining = tabs.filterNot { it.id == tabId }
    if (activeTabId != tabId) return copy(tabs = remaining)
    val next = remaining.getOrElse(closingIndex.coerceAtMost(remaining.lastIndex)) { remaining.last() }
    return copy(tabs = remaining, activeTabId = next.id)
}

fun BrowserWorkspace.pushPage(page: BrowserPage): BrowserWorkspace = updateActiveTab { tab ->
    val history = (tab.history.take(tab.currentIndex + 1) + page).takeLast(MAX_TAB_HISTORY)
    tab.copy(history = history, currentIndex = history.lastIndex)
}

fun BrowserWorkspace.goBack(): BrowserWorkspace = updateActiveTab { tab ->
    if (tab.canGoBack) tab.copy(currentIndex = tab.currentIndex - 1) else tab
}

fun BrowserWorkspace.updateActiveSearch(
    transform: (BrowserPage.Search) -> BrowserPage.Search,
): BrowserWorkspace = updateActiveTab { tab ->
    val search = tab.currentPage as? BrowserPage.Search ?: return@updateActiveTab tab
    tab.copy(history = tab.history.toMutableList().apply {
        this[tab.currentIndex] = transform(search)
    })
}

fun BrowserWorkspace.moveTab(tabId: String, offset: Int): BrowserWorkspace {
    val from = tabs.indexOfFirst { it.id == tabId }
    val to = (from + offset).coerceIn(tabs.indices)
    if (from < 0 || from == to) return this
    val reordered = tabs.toMutableList()
    val tab = reordered.removeAt(from)
    reordered.add(to, tab)
    return copy(tabs = reordered)
}

private fun BrowserWorkspace.updateActiveTab(
    transform: (BrowserTab) -> BrowserTab,
): BrowserWorkspace = copy(tabs = tabs.map { tab ->
    if (tab.id == activeTabId) transform(tab) else tab
})

val BrowserPage.label: String
    get() = when (this) {
        is BrowserPage.Search -> query.ifBlank { "새 검색" }
        is BrowserPage.OnlineGallery -> title.ifBlank { "갤러리 $galleryId" }
        is BrowserPage.LibraryBook -> title.ifBlank { "다운로드 작품" }
        is BrowserPage.OnlineReader -> title.ifBlank { "갤러리 $galleryId" }
        is BrowserPage.LibraryReader -> title.ifBlank { "다운로드 작품" }
    }

private const val MAX_TAB_HISTORY = 50
