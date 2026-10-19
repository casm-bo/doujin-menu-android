package com.doujinmenu.android.model

enum class HistorySource(val label: String) { SEARCH("검색"), DEVICE("기기"), PC("PC") }
enum class HistoryAction(val label: String) { DETAIL("상세 열람"), READING("읽는 중"), COMPLETED("읽음") }

data class RecentHistoryEntry(
    val key: String,
    val title: String,
    val viewedAt: Long,
    val source: HistorySource,
    val galleryId: Long? = null,
    val bookId: String? = null,
    val syncId: String? = null,
    val artist: String = "",
    val thumbnail: String? = null,
    val pageCount: Int = 0,
    val page: Int? = null,
    val action: HistoryAction = HistoryAction.DETAIL,
    val completed: Boolean = false,
)

const val RECENT_HISTORY_LIMIT = 500

fun RecentHistoryEntry.matches(other: RecentHistoryEntry): Boolean = key == other.key ||
    (galleryId != null && galleryId == other.galleryId) ||
    (syncId != null && syncId == other.syncId) || (bookId != null && bookId == other.bookId)

fun List<RecentHistoryEntry>.recordHistory(event: RecentHistoryEntry): List<RecentHistoryEntry> {
    val previous = filter { it.matches(event) }
    val lastRead = previous.firstOrNull { it.page != null }
    val merged = event.copy(
        bookId = event.bookId ?: previous.firstNotNullOfOrNull { it.bookId },
        syncId = event.syncId ?: previous.firstNotNullOfOrNull { it.syncId },
        galleryId = event.galleryId ?: previous.firstNotNullOfOrNull { it.galleryId },
        page = event.page ?: lastRead?.page,
        pageCount = event.pageCount.takeIf { it > 0 } ?: previous.firstOrNull()?.pageCount ?: 0,
        completed = event.completed || previous.any { it.completed },
    )
    return (filterNot { it.matches(event) } + merged).sortedByDescending { it.viewedAt }.take(RECENT_HISTORY_LIMIT)
}

fun RecentHistoryEntry.findBook(books: List<LibraryBook>): LibraryBook? = books
    .filter { it.id == bookId || (syncId != null && it.syncId == syncId) ||
        (galleryId != null && it.metadata.hitomiId?.toLongOrNull() == galleryId) }
    .sortedBy { it.isCloud }
    .firstOrNull()
