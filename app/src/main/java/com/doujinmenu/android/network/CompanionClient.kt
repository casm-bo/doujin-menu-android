package com.doujinmenu.android.network

import com.doujinmenu.android.model.CompanionStatus
import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.model.DownloadQueueItem
import com.doujinmenu.android.model.DownloadStatus
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryMetadata
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.PairingResult
import com.doujinmenu.android.model.SearchResult
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SeriesSyncUpdate(
    val bookSyncId: String,
    val assignment: CustomSeriesAssignment?,
    val modifiedAt: Long,
    val baseVersion: Long? = null,
    val mutationId: String = UUID.randomUUID().toString(),
)

internal fun SeriesSyncUpdate.toJsonObject(): JSONObject = JSONObject()
    .put("mutationId", mutationId)
    .put("bookSyncId", bookSyncId)
    .put("name", assignment?.name ?: JSONObject.NULL)
    .put("order", assignment?.order ?: 0)
    .put("modifiedAt", modifiedAt)
    .also { payload -> baseVersion?.let { payload.put("baseVersion", it) } }

data class BookStateSyncUpdate(
    val mutationId: String = UUID.randomUUID().toString(),
    val bookSyncId: String,
    val baseVersion: Long,
    val currentPage: Int,
    val isFavorite: Boolean,
    val isRead: Boolean,
    val isHidden: Boolean,
    val customTitle: String?,
    val seriesFavorite: Boolean,
    val historyEventId: String? = null,
    val historyViewedAt: String? = null,
)

data class BookStateSyncResult(
    val mutationId: String,
    val status: String,
    val conflict: Boolean,
    val version: Long,
)

class CompanionClient {
    suspend fun getStatus(baseUrl: String): CompanionStatus = withContext(Dispatchers.IO) {
        val root = request("$baseUrl/v1/status")
        val data = root.requireSuccess()
        CompanionStatus(
            service = data.optString("service", "unknown"),
            version = data.optInt("version", 0),
            pairingAvailable = data.optBoolean("pairingAvailable", false),
            syncGeneration = data.optLong("syncGeneration", 0L),
        )
    }

    suspend fun pair(baseUrl: String, code: String, deviceName: String): PairingResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("code", code)
                .put("deviceName", deviceName)
            val data = request("$baseUrl/v1/pair", method = "POST", body = body).requireSuccess()
            val device = data.getJSONObject("device")
            PairingResult(
                deviceId = device.getString("id"),
                deviceName = device.optString("name", deviceName),
                token = data.getString("token"),
            )
        }

    suspend fun search(
        profile: DesktopProfile,
        searchQuery: String,
        page: Int = 1,
        offset: Int = 0,
    ): SearchResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("searchQuery", searchQuery)
            .put("page", page)
            .put("offset", offset)
        val data = request(
            url = "${profile.baseUrl}/v1/hitomi/search",
            method = "POST",
            token = profile.token,
            body = body,
        ).requireSuccess()
        parseHitomiSearchResult(data)
    }

    suspend fun getGallery(profile: DesktopProfile, galleryId: Long): GallerySummary =
        withContext(Dispatchers.IO) {
            val data = request(
                url = "${profile.baseUrl}/v1/hitomi/gallery/$galleryId",
                token = profile.token,
            ).requireSuccess()
            parseHitomiGallery(data, galleryId)
        }

    suspend fun getGalleryPages(profile: DesktopProfile, galleryId: Long): List<String> =
        withContext(Dispatchers.IO) {
            val root = request(
                url = "${profile.baseUrl}/v1/hitomi/gallery/$galleryId/pages",
                token = profile.token,
            )
            if (!root.optBoolean("success", false)) {
                throw CompanionApiException(root.errorMessage("페이지 목록 요청에 실패했습니다."))
            }
            val pages = root.optJSONArray("data")
                ?: throw CompanionApiException("응답에 페이지 배열이 없습니다.")
            pages.toStringList()
        }

    suspend fun getLibraryBooks(profile: DesktopProfile): List<LibraryBook> =
        withContext(Dispatchers.IO) {
            val collected = mutableListOf<LibraryBook>()
            var cursor: Long? = null
            do {
                val query = buildString {
                    append("?limit=$LIBRARY_PAGE_SIZE")
                    cursor?.let { append("&cursor=$it") }
                }
                val root = request(
                    url = "${profile.baseUrl}/v1/library/books$query",
                    token = profile.token,
                )
                if (!root.optBoolean("success", false)) {
                    throw CompanionApiException(root.errorMessage("라이브러리 목록 요청에 실패했습니다."))
                }
                val data = root.opt("data")
                val books = when (data) {
                    is JSONArray -> data
                    is JSONObject -> data.optJSONArray("books")
                    else -> null
                } ?: throw CompanionApiException("라이브러리 응답에 책 목록이 없습니다.")
                repeat(books.length()) { index ->
                    val item = books.optJSONObject(index) ?: return@repeat
                    val id = item.optLong("id")
                    val syncId = item.nullableString("syncId") ?: item.nullableString("sync_id")
                    val hasSyncedSeriesState = item.has("seriesCollection")
                    val syncedSeriesObject = item.optJSONObject("seriesCollection")
                    val syncedSeriesModifiedAt = syncedSeriesObject?.optLong("modifiedAt", 0L) ?: 0L
                    val syncedSeries = syncedSeriesObject?.let { series ->
                        series.nullableString("name")?.let {
                            CustomSeriesAssignment(
                                name = it,
                                order = series.optInt("order", 0).coerceAtLeast(0),
                                modifiedAt = syncedSeriesModifiedAt,
                            )
                        }
                    }
                    val pageCount = item.optInt("pageCount", item.optInt("page_count", 0))
                    val sourcePath = item.nullableString("libraryPath")
                        ?: item.nullableString("library_path")
                        ?: "데스크톱 라이브러리"
                    val coverUrl = absoluteUrl(profile.baseUrl, item.nullableString("coverUrl"))
                        ?: "${profile.baseUrl}/v1/library/books/$id/cover"
                    collected.add(
                        LibraryBook(
                            id = syncId?.let { "desktop:${profile.id}:${it.lowercase()}" }
                                ?: "desktop:${profile.id}:$id",
                            title = item.optString("title", "Gallery #$id"),
                            locationUri = "desktop:${profile.id}:$sourcePath",
                            locationName = sourcePath.substringAfterLast('\\').substringAfterLast('/'),
                            folderUri = item.nullableString("path").orEmpty(),
                            pages = List(pageCount) { page ->
                                LibraryPage(uri = if (page == 0) coverUrl else "", name = "${page + 1}")
                            },
                            modifiedAt = item.optLong("modifiedAt", 0L),
                            metadata = LibraryMetadata(
                                hitomiId = item.nullableString("hitomiId") ?: item.nullableString("hitomi_id"),
                                artists = item.optJSONArray("artists").metadataNames(),
                                groups = item.optJSONArray("groups").metadataNames(),
                                galleryType = item.nullableString("type"),
                                series = item.optJSONArray("series").metadataNames(),
                                characters = item.optJSONArray("characters").metadataNames(),
                                tags = item.optJSONArray("tags").metadataNames(),
                                language = item.nullableString("language")
                                    ?: item.nullableString("language_name_local")
                                    ?: item.nullableString("language_name_english"),
                            ),
                            isCloud = true,
                            syncId = syncId,
                            syncStateVersion = item.optLong("stateVersion", 0L),
                            syncedFavorite = item.optBoolean("isFavorite", false),
                            syncedRead = item.optBoolean("isRead", false),
                            syncedHidden = item.optBoolean("isHidden", false),
                            syncedProgress = item.optInt("currentPage", 0).coerceAtLeast(0),
                            syncedCustomTitle = item.nullableString("customTitle"),
                            syncedSeriesFavorite = item.optBoolean("seriesFavorite", false),
                            syncedSeries = syncedSeries,
                            syncedSeriesModifiedAt = syncedSeriesModifiedAt,
                            hasSyncedSeriesState = hasSyncedSeriesState,
                            remoteBookId = id,
                            coverUriOverride = coverUrl,
                            cloudToken = profile.token,
                        ),
                    )
                }
                val page = data as? JSONObject
                cursor = page?.optLong("nextCursor", 0L)?.takeIf { it > 0L }
                val hasMore = page?.optBoolean("hasMore", false) == true
                if (!hasMore || cursor == null) break
            } while (true)

            val duplicateSyncIds = collected.mapNotNull(LibraryBook::syncId)
                .groupingBy(String::lowercase)
                .eachCount()
                .filterValues { it > 1 }
                .keys
            collected.map { book ->
                if (book.syncId?.lowercase() in duplicateSyncIds) {
                    book.copy(id = "${book.id}:${book.remoteBookId}")
                } else book
            }
        }

    suspend fun getDesktopDownloadPath(profile: DesktopProfile): String? =
        withContext(Dispatchers.IO) {
            request(
                url = "${profile.baseUrl}/v1/downloads/path",
                token = profile.token,
            ).requireSuccess().nullableString("path")
        }

    suspend fun getLibraryBookPages(profile: DesktopProfile, bookId: Long): List<String> =
        withContext(Dispatchers.IO) {
            val pages = request(
                url = "${profile.baseUrl}/v1/library/books/$bookId/pages",
                token = profile.token,
            ).requireDataArray()
            pages.toStringList().map { absoluteUrl(profile.baseUrl, it) ?: it }
        }

    suspend fun deleteLibraryBook(profile: DesktopProfile, bookId: Long) =
        withContext(Dispatchers.IO) {
            request(
                url = "${profile.baseUrl}/v1/library/books/$bookId",
                method = "DELETE",
                token = profile.token,
            ).requireSuccess()
        }

    suspend fun saveLibrarySeries(
        profile: DesktopProfile,
        assignments: List<SeriesSyncUpdate>,
    ): List<SeriesSyncResult> = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "assignments",
            JSONArray().apply {
                assignments.forEach { update ->
                    put(update.toJsonObject())
                }
            },
        )
        val data = request(
            url = "${profile.baseUrl}/v1/library/series",
            method = "POST",
            token = profile.token,
            body = body,
            readTimeoutMs = SERIES_SYNC_READ_TIMEOUT_MS,
        ).requireSuccess()
        val results = data.optJSONArray("results")
            ?: throw CompanionApiException("시리즈 동기화 응답에 항목별 결과가 없습니다.")
        buildList(results.length()) {
            repeat(results.length()) { index ->
                val item = results.optJSONObject(index) ?: return@repeat
                add(
                    SeriesSyncResult(
                        mutationId = item.nullableString("mutationId"),
                        bookSyncId = item.nullableString("bookSyncId").orEmpty(),
                        status = item.optString("status"),
                        version = item.optLong("version", 0L),
                        modifiedAt = item.optLong("modifiedAt", 0L),
                        name = item.nullableString("name"),
                        order = item.optInt("order", 0),
                    ),
                )
            }
        }
    }

    suspend fun uploadLibraryArchive(
        profile: DesktopProfile,
        archive: File,
        fileName: String,
        syncId: String,
    ): LibraryImportResult = withContext(Dispatchers.IO) {
        val connection = URL("${profile.baseUrl}/v1/library/import").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = UPLOAD_READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(archive.length())
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.setRequestProperty("Authorization", "Bearer ${profile.token}")
            connection.setRequestProperty("X-File-Name", URLEncoder.encode(fileName, Charsets.UTF_8.name()))
            connection.setRequestProperty("X-Sync-Id", syncId)
            FileInputStream(archive).buffered().use { input ->
                connection.outputStream.buffered().use(input::copyTo)
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(raw) }.getOrElse {
                throw CompanionApiException("파일 업로드 응답이 올바른 JSON이 아닙니다. (HTTP $statusCode)")
            }
            if (statusCode !in 200..299) throw CompanionApiException(root.errorMessage("HTTP $statusCode"))
            val data = root.requireSuccess()
            LibraryImportResult(
                status = data.optString("status"),
                remoteBookId = data.optLong("id"),
                syncId = data.nullableString("syncId") ?: syncId,
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun saveBookStates(
        profile: DesktopProfile,
        updates: List<BookStateSyncUpdate>,
    ): List<BookStateSyncResult> = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "mutations",
            JSONArray().apply {
                updates.forEach { update ->
                    val mutation = JSONObject()
                            .put("mutationId", update.mutationId)
                            .put("bookSyncId", update.bookSyncId)
                            .put("baseVersion", update.baseVersion)
                            .put("currentPage", update.currentPage)
                            .put("isFavorite", update.isFavorite)
                            .put("isRead", update.isRead)
                            .put("isHidden", update.isHidden)
                            .put("customTitle", update.customTitle ?: JSONObject.NULL)
                            .put("seriesFavorite", update.seriesFavorite)
                    if (update.historyEventId != null && update.historyViewedAt != null) {
                        mutation.put(
                            "historyEvent",
                            JSONObject()
                                .put("eventId", update.historyEventId)
                                .put("viewedAt", update.historyViewedAt)
                                .put("currentPage", update.currentPage),
                        )
                    }
                    put(mutation)
                }
            },
        )
        val data = request(
            url = "${profile.baseUrl}/v1/sync/changes",
            method = "POST",
            token = profile.token,
            body = body,
        ).requireSuccess()
        val results = data.optJSONArray("results")
            ?: throw CompanionApiException("책 상태 동기화 응답에 항목별 결과가 없습니다.")
        buildList(results.length()) {
            repeat(results.length()) { index ->
                val item = results.optJSONObject(index) ?: return@repeat
                add(
                    BookStateSyncResult(
                        mutationId = item.optString("mutationId"),
                        status = item.optString("status"),
                        conflict = item.optBoolean("conflict", false),
                        version = item.optJSONObject("state")?.optLong("version", 0L) ?: 0L,
                    ),
                )
            }
        }
    }

    suspend fun requestDownload(profile: DesktopProfile, galleryId: Long): DownloadQueueItem =
        withContext(Dispatchers.IO) {
            val data = request(
                url = "${profile.baseUrl}/v1/downloads",
                method = "POST",
                token = profile.token,
                body = JSONObject().put("galleryId", galleryId),
            ).requireSuccess()
            data.toDownloadQueueItem()
        }

    suspend fun getDownloads(profile: DesktopProfile): List<DownloadQueueItem> =
        withContext(Dispatchers.IO) {
            request(
                url = "${profile.baseUrl}/v1/downloads",
                token = profile.token,
            ).requireDataArray().toDownloadQueueItems()
        }

    suspend fun pauseDownload(profile: DesktopProfile, queueId: Long) =
        downloadAction(profile, queueId, "pause")

    suspend fun resumeDownload(profile: DesktopProfile, queueId: Long) =
        downloadAction(profile, queueId, "resume")

    suspend fun retryDownload(profile: DesktopProfile, queueId: Long) =
        downloadAction(profile, queueId, "retry")

    suspend fun removeDownload(profile: DesktopProfile, queueId: Long) =
        withContext(Dispatchers.IO) {
            request(
                url = "${profile.baseUrl}/v1/downloads/$queueId",
                method = "DELETE",
                token = profile.token,
            ).requireSuccess()
        }

    suspend fun clearCompletedDownloads(profile: DesktopProfile) =
        withContext(Dispatchers.IO) {
            request(
                url = "${profile.baseUrl}/v1/downloads/completed",
                method = "DELETE",
                token = profile.token,
            ).requireSuccess()
        }

    private suspend fun downloadAction(
        profile: DesktopProfile,
        queueId: Long,
        action: String,
    ) = withContext(Dispatchers.IO) {
        request(
            url = "${profile.baseUrl}/v1/downloads/$queueId/$action",
            method = "POST",
            token = profile.token,
        ).requireSuccess()
    }

    private fun request(
        url: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val rawResponse = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(rawResponse) }.getOrElse {
                throw CompanionApiException("서버가 올바른 JSON을 반환하지 않았습니다. (HTTP $statusCode)")
            }
            if (statusCode !in 200..299) {
                throw CompanionApiException(response.errorMessage("HTTP $statusCode"))
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.requireSuccess(): JSONObject {
        if (!optBoolean("success", false)) {
            throw CompanionApiException(errorMessage("요청에 실패했습니다."))
        }
        return optJSONObject("data")
            ?: throw CompanionApiException("응답에 data 객체가 없습니다.")
    }

    private fun JSONObject.requireDataArray(): JSONArray {
        if (!optBoolean("success", false)) {
            throw CompanionApiException(errorMessage("요청에 실패했습니다."))
        }
        return optJSONArray("data")
            ?: throw CompanionApiException("응답에 data 배열이 없습니다.")
    }

    private fun JSONObject.errorMessage(fallback: String): String {
        val errorObject = optJSONObject("error")
        return errorObject?.optString("message")?.takeIf { it.isNotBlank() }
            ?: optString("error").takeIf { it.isNotBlank() }
            ?: optString("message").takeIf { it.isNotBlank() }
            ?: fallback
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000
        const val SERIES_SYNC_READ_TIMEOUT_MS = 60_000
        const val UPLOAD_READ_TIMEOUT_MS = 120_000
        const val LIBRARY_PAGE_SIZE = 200
    }
}

data class SeriesSyncResult(
    val mutationId: String?,
    val bookSyncId: String,
    val status: String,
    val version: Long,
    val modifiedAt: Long,
    val name: String?,
    val order: Int,
)

data class LibraryImportResult(
    val status: String,
    val remoteBookId: Long,
    val syncId: String,
)

private fun absoluteUrl(baseUrl: String, value: String?): String? {
    if (value.isNullOrBlank()) return null
    return if (value.startsWith("http://") || value.startsWith("https://")) value
    else baseUrl.trimEnd('/') + "/" + value.trimStart('/')
}

internal fun parseHitomiSearchResult(data: JSONObject): SearchResult {
    val items = data.optJSONArray("data")
        ?: throw CompanionApiException("검색 응답에 갤러리 목록이 없습니다.")
    val galleryIds = buildList(items.length()) {
        repeat(items.length()) { index ->
            val id = when (val item = items.opt(index)) {
                is Number -> item.toLong()
                is JSONObject -> item.optLong("id", 0L)
                else -> item?.toString()?.toLongOrNull() ?: 0L
            }
            if (id > 0L) add(id)
        }
    }
    return SearchResult(
        galleryIds = galleryIds,
        hasNextPage = data.optBoolean("hasNextPage", false),
    )
}

internal fun parseHitomiGallery(data: JSONObject, fallbackId: Long): GallerySummary {
    val titleValue = data.opt("title")
    val title = when (titleValue) {
        is JSONObject -> titleValue.nullableString("display")
            ?: titleValue.nullableString("japanese")
        is String -> titleValue.trim().takeIf(String::isNotEmpty)
        else -> null
    } ?: "Gallery #$fallbackId"

    val languageName = data.optJSONObject("languageName")
    val legacyLanguage = data.optJSONObject("language")
    val language = languageName?.nullableString("local")
        ?: languageName?.nullableString("english")
        ?: legacyLanguage?.nullableString("localName")
        ?: legacyLanguage?.nullableString("name")
        ?: (data.opt("language") as? String)?.trim()?.takeIf(String::isNotEmpty)
    val tags = buildList {
        val source = data.optJSONArray("tags") ?: return@buildList
        repeat(source.length()) { index ->
            when (val item = source.opt(index)) {
                is JSONObject -> {
                    val name = item.nullableString("name") ?: return@repeat
                    add(
                        GalleryTag(
                            type = item.nullableString("type") ?: "tag",
                            name = name,
                            isNegative = item.optBoolean("isNegative", false),
                        ),
                    )
                }
                is String -> item.trim().takeIf(String::isNotEmpty)?.let {
                    add(GalleryTag(type = "tag", name = it))
                }
            }
        }
    }

    return GallerySummary(
        id = data.optLong("id", fallbackId),
        title = title,
        artists = data.optJSONArray("artists").metadataNames(),
        series = data.optJSONArray("series").metadataNames(),
        galleryType = data.nullableString("type"),
        tags = tags,
        thumbnailUrl = data.nullableString("thumbnailUrl")
            ?: data.nullableString("thumbnail"),
        pageCount = data.optJSONArray("files")?.length()
            ?: data.optJSONArray("images")?.length()
            ?: 0,
        language = language,
        publishedDate = data.nullableString("publishedDate")
            ?: data.nullableString("publishedAt"),
    )
}

private fun JSONArray?.metadataNames(): List<String> = buildList {
    val source = this@metadataNames ?: return@buildList
    repeat(source.length()) { index ->
        val name = source.optJSONObject(index)?.optString("name") ?: source.optString(index)
        name.takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONArray.toDownloadQueueItems(): List<DownloadQueueItem> = buildList {
    repeat(length()) { index ->
        optJSONObject(index)?.let { add(it.toDownloadQueueItem()) }
    }
}

private fun JSONObject.toDownloadQueueItem(): DownloadQueueItem = DownloadQueueItem(
    id = getLong("id"),
    galleryId = getLong("gallery_id"),
    galleryTitle = optString("gallery_title", "Gallery #${optLong("gallery_id")}"),
    galleryArtist = nullableString("gallery_artist"),
    thumbnailUrl = nullableString("thumbnail_url"),
    status = when (optString("status").lowercase()) {
        "pending" -> DownloadStatus.PENDING
        "downloading" -> DownloadStatus.DOWNLOADING
        "completed" -> DownloadStatus.COMPLETED
        "failed" -> DownloadStatus.FAILED
        "paused" -> DownloadStatus.PAUSED
        else -> DownloadStatus.UNKNOWN
    },
    progress = optInt("progress", 0).coerceIn(0, 100),
    totalFiles = optInt("total_files", 0),
    downloadedFiles = optInt("downloaded_files", 0),
    downloadSpeed = optLong("download_speed", 0L),
    errorMessage = nullableString("error_message"),
    addedAt = optString("added_at"),
)

private fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
}

private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
    val array = this@toStringList ?: return@buildList
    repeat(array.length()) { index ->
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}

class CompanionApiException(message: String) : Exception(message)
