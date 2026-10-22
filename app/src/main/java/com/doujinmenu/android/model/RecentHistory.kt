package com.doujinmenu.android.model

enum class HistorySource(val label: String) { SEARCH("검색 기록"), DEVICE("기기"), PC("PC") }
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
    .firstOrNull { !it.isCloud && matchesBook(it) } ?: books.firstOrNull { matchesBook(it) }

private fun RecentHistoryEntry.matchesBook(book: LibraryBook): Boolean = book.id == bookId ||
    (syncId != null && book.syncId == syncId) || (galleryId != null && book.metadata.hitomiId?.toLongOrNull() == galleryId)

/** Preserve device preference and original list order across all three matching identities. */
internal class HistoryBookIndex(books: List<LibraryBook>) {
    private val byId = mutableMapOf<String, IndexedValue<LibraryBook>>()
    private val bySyncId = mutableMapOf<String, IndexedValue<LibraryBook>>()
    private val byGalleryId = mutableMapOf<Long, IndexedValue<LibraryBook>>()
    private val preference = compareBy<IndexedValue<LibraryBook>> { it.value.isCloud }.thenBy { it.index }
    init {
        fun <K> add(map: MutableMap<K, IndexedValue<LibraryBook>>, key: K, candidate: IndexedValue<LibraryBook>) {
            val previous = map[key]
            if (previous == null || preference.compare(candidate, previous) < 0) map[key] = candidate
        }
        books.withIndex().forEach { candidate ->
            add(byId, candidate.value.id, candidate)
            candidate.value.syncId?.let { add(bySyncId, it, candidate) }
            candidate.value.metadata.hitomiId?.toLongOrNull()?.let { add(byGalleryId, it, candidate) }
        }
    }
    fun find(entry: RecentHistoryEntry): LibraryBook? = listOfNotNull(
        entry.bookId?.let(byId::get), entry.syncId?.let(bySyncId::get), entry.galleryId?.let(byGalleryId::get),
    ).minWithOrNull(preference)?.value
}
