package com.doujinmenu.android.model

data class DesktopProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val token: String,
)

data class CompanionStatus(
    val service: String,
    val version: Int,
    val pairingAvailable: Boolean,
    val syncGeneration: Long = 0L,
)

data class PairingResult(
    val deviceId: String,
    val deviceName: String,
    val token: String,
)

data class SearchResult(
    val galleryIds: List<Long>,
    val hasNextPage: Boolean,
)

data class GalleryTag(
    val type: String,
    val name: String,
    val isNegative: Boolean = false,
)

data class GallerySummary(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val series: List<String>,
    val galleryType: String?,
    val tags: List<GalleryTag>,
    val thumbnailUrl: String?,
    val pageCount: Int,
    val language: String?,
    val publishedDate: String? = null,
    val loadError: String? = null,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
    UNKNOWN,
}

data class DownloadQueueItem(
    val id: Long,
    val galleryId: Long,
    val galleryTitle: String,
    val galleryArtist: String?,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val progress: Int,
    val totalFiles: Int,
    val downloadedFiles: Int,
    val downloadSpeed: Long,
    val errorMessage: String?,
    val addedAt: String,
)
