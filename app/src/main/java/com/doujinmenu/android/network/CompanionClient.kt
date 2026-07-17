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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SeriesSyncUpdate(
    val bookSyncId: String,
    val assignment: CustomSeriesAssignment?,
    val modifiedAt: Long,
)

class CompanionClient {
    suspend fun getStatus(baseUrl: String): CompanionStatus = withContext(Dispatchers.IO) {
        val root = request("$baseUrl/v1/status")
        val data = root.requireSuccess()
        CompanionStatus(
            service = data.optString("service", "unknown"),
            version = data.optInt("version", 0),
            pairingAvailable = data.optBoolean("pairingAvailable", false),
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
        val ids = data.getJSONArray("data")
        SearchResult(
            galleryIds = buildList(ids.length()) {
                repeat(ids.length()) { index -> add(ids.getLong(index)) }
            },
            hasNextPage = data.optBoolean("hasNextPage", false),
        )
    }

    suspend fun getGallery(profile: DesktopProfile, galleryId: Long): GallerySummary =
        withContext(Dispatchers.IO) {
            val data = request(
                url = "${profile.baseUrl}/v1/hitomi/gallery/$galleryId",
                token = profile.token,
            ).requireSuccess()
            val title = data.optJSONObject("title")?.optString("display")
                ?.takeIf { it.isNotBlank() }
                ?: "Gallery #$galleryId"
            val languageName = data.optJSONObject("languageName")
            val tagsArray = data.optJSONArray("tags")
            val tags = buildList {
                if (tagsArray != null) {
                    repeat(tagsArray.length()) { index ->
                        val item = tagsArray.optJSONObject(index) ?: return@repeat
                        val name = item.optString("name").takeIf { it.isNotBlank() }
                            ?: return@repeat
                        add(
                            GalleryTag(
                                type = item.optString("type", "tag"),
                                name = name,
                                isNegative = item.optBoolean("isNegative", false),
                            ),
                        )
                    }
                }
            }
            GallerySummary(
                id = data.optLong("id", galleryId),
                title = title,
                artists = data.optJSONArray("artists").toStringList(),
                series = data.optJSONArray("series").toStringList(),
                galleryType = data.optString("type").takeIf { it.isNotBlank() },
                tags = tags,
                thumbnailUrl = data.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                pageCount = data.optJSONArray("files")?.length() ?: 0,
                language = languageName?.optString("local")?.takeIf { it.isNotBlank() }
                    ?: languageName?.optString("english")?.takeIf { it.isNotBlank() },
                publishedDate = data.nullableString("publishedDate"),
            )
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
            val books = request(
                url = "${profile.baseUrl}/v1/library/books",
                token = profile.token,
            ).requireDataArray()
            buildList {
                repeat(books.length()) { index ->
                    val item = books.optJSONObject(index) ?: return@repeat
                    val id = item.optLong("id")
                    val syncId = item.nullableString("syncId") ?: item.nullableString("sync_id")
                    val hasSyncedSeriesState = item.has("seriesCollection")
                    val syncedSeriesObject = item.optJSONObject("seriesCollection")
                    val syncedSeriesModifiedAt = syncedSeriesObject?.optLong("modifiedAt", 0L) ?: 0L
                    val syncedSeries = syncedSeriesObject?.let { series ->
                        val name = series.optString("name").trim()
                        name.takeIf(String::isNotEmpty)?.let {
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
                    add(
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
                            syncedSeries = syncedSeries,
                            syncedSeriesModifiedAt = syncedSeriesModifiedAt,
                            hasSyncedSeriesState = hasSyncedSeriesState,
                            remoteBookId = id,
                            coverUriOverride = coverUrl,
                            cloudToken = profile.token,
                        ),
                    )
                }
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
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "assignments",
            JSONArray().apply {
                assignments.forEach { update ->
                    put(
                        JSONObject()
                            .put("bookSyncId", update.bookSyncId)
                            .put("name", update.assignment?.name ?: JSONObject.NULL)
                            .put("order", update.assignment?.order ?: 0)
                            .put("modifiedAt", update.modifiedAt),
                    )
                }
            },
        )
        request(
            url = "${profile.baseUrl}/v1/library/series",
            method = "POST",
            token = profile.token,
            body = body,
        ).requireSuccess()
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
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
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
    }
}

private fun absoluteUrl(baseUrl: String, value: String?): String? {
    if (value.isNullOrBlank()) return null
    return if (value.startsWith("http://") || value.startsWith("https://")) value
    else baseUrl.trimEnd('/') + "/" + value.trimStart('/')
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
