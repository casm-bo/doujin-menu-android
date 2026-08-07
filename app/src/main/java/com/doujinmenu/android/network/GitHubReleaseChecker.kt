package com.doujinmenu.android.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppRelease(
    val versionName: String,
    val pageUrl: String,
    val notes: String,
)

class GitHubReleaseChecker {
    suspend fun latestRelease(): AppRelease = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "doujin-menu-android")
            if (connection.responseCode !in 200..299) {
                error("GitHub release check failed: HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            AppRelease(
                versionName = json.getString("tag_name").removePrefix("v"),
                pageUrl = json.getString("html_url"),
                notes = json.optString("body").take(1_500),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/casm-bo/doujin-menu-android/releases/latest"
    }
}

internal fun isNewerAppVersion(latest: String, current: String): Boolean {
    val latestParts = versionParts(latest)
    val currentParts = versionParts(current)
    repeat(maxOf(latestParts.size, currentParts.size)) { index ->
        val result = latestParts.getOrElse(index) { 0 }.compareTo(currentParts.getOrElse(index) { 0 })
        if (result != 0) return result > 0
    }
    return false
}

private fun versionParts(value: String): List<Int> = value.removePrefix("v")
    .split('.')
    .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
