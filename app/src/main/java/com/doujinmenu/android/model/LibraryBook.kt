package com.doujinmenu.android.model

data class LibraryBook(
    val id: String,
    val title: String,
    val locationUri: String,
    val locationName: String,
    val folderUri: String,
    val pages: List<LibraryPage>,
    val modifiedAt: Long,
    val metadata: LibraryMetadata = LibraryMetadata(),
    val isCloud: Boolean = false,
    val remoteBookId: Long? = null,
    val coverUriOverride: String? = null,
    val cloudToken: String? = null,
) {
    val coverUri: String get() = coverUriOverride ?: pages.firstOrNull()?.uri.orEmpty()
}

data class LibraryPage(
    val uri: String,
    val name: String,
    val archiveUri: String? = null,
    val archiveEntry: String? = null,
)

data class LibraryMetadata(
    val hitomiId: String? = null,
    val artists: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val galleryType: String? = null,
    val series: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
)

enum class LibraryReadFilter { ALL, UNREAD, READ }

enum class LibrarySort { TITLE_ASC, TITLE_DESC, NEWEST, OLDEST }

enum class ViewerScale { FIT_SCREEN, FIT_WIDTH }

data class ViewerPreferences(
    val scale: ViewerScale = ViewerScale.FIT_SCREEN,
    val showPageNumber: Boolean = true,
    val keepScreenOn: Boolean = true,
)
