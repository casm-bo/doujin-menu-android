package com.doujinmenu.android.network

import com.doujinmenu.android.model.CompanionStatus
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import com.doujinmenu.android.model.PairingResult
import com.doujinmenu.android.model.SearchResult
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    suspend fun requestDownload(profile: DesktopProfile, galleryId: Long): String =
        withContext(Dispatchers.IO) {
            val data = request(
                url = "${profile.baseUrl}/v1/downloads",
                method = "POST",
                token = profile.token,
                body = JSONObject().put("galleryId", galleryId),
            ).requireSuccess()
            data.optString("status", "queued")
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

private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
    val array = this@toStringList ?: return@buildList
    repeat(array.length()) { index ->
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}

class CompanionApiException(message: String) : Exception(message)
