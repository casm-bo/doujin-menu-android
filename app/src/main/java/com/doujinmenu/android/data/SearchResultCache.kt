package com.doujinmenu.android.data

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight slots stay resident; full gallery metadata belongs to the visible page window. */
data class SearchResultIndex(
    val sessionId: String,
    val queries: List<String>,
    val pages: List<List<Long>> = emptyList(),
    val hasNextPage: Boolean = false,
) {
    val ids: List<Long> get() = pages.flatten()

    fun append(galleries: List<GallerySummary>, hasNext: Boolean): SearchResultIndex {
        return appendIds(galleries.map { it.id }, hasNext)
    }

    fun appendIds(newIds: List<Long>, hasNext: Boolean): SearchResultIndex {
        val seen = ids.toMutableSet()
        return copy(pages = pages + listOf(newIds.filter { seen.add(it) }), hasNextPage = hasNext)
    }

    fun pageAt(resultIndex: Int): Int {
        var remaining = resultIndex.coerceAtLeast(0)
        pages.forEachIndexed { index, ids ->
            if (remaining < ids.size) return index + 1
            remaining -= ids.size
        }
        return pages.size.coerceAtLeast(1)
    }

    fun window(firstResult: Int, lastResult: Int): IntRange {
        val first = pageAt(firstResult)
        val last = pageAt(lastResult).coerceAtMost(first + 2)
        return (first - 1).coerceAtLeast(1)..(last + 1).coerceAtMost(pages.size)
    }
}

/** Disposable page cache. Reads, atomic writes and LRU maintenance never run on the UI thread. */
class SearchResultCache(
    private val directory: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val minimumFreeBytes: Long = 32L * 1024 * 1024,
) {
    private val mutex = Mutex()

    suspend fun readIndex(sessionId: String): SearchResultIndex? = io {
        read(file(sessionId, "index"))?.let { raw ->
            require(raw.getInt("version") == 1 && raw.getString("sessionId") == sessionId)
            SearchResultIndex(sessionId, raw.getJSONArray("queries").strings(),
                raw.getJSONArray("pages").let { pages -> List(pages.length()) { page ->
                    pages.getJSONArray(page).let { ids -> List(ids.length()) { ids.getLong(it).also { id -> require(id > 0) } } }
                } }, raw.getBoolean("hasNextPage"))
        }
    }

    suspend fun writeIndex(index: SearchResultIndex): Boolean = io {
        write(file(index.sessionId, "index"), JSONObject().put("version", 1)
            .put("sessionId", index.sessionId).put("queries", JSONArray(index.queries))
            .put("pages", JSONArray(index.pages.map(::JSONArray))).put("hasNextPage", index.hasNextPage))
    } ?: false

    suspend fun readPage(index: SearchResultIndex, page: Int): List<GallerySummary>? = io {
        val expected = index.pages.getOrNull(page - 1) ?: return@io null
        read(file(index.sessionId, "page-$page"))?.getJSONArray("galleries")?.let { items ->
            List(items.length()) { items.getJSONObject(it).gallery() }.also { galleries ->
                require(galleries.map { it.id } == expected)
            }
        }
    }

    suspend fun writePage(index: SearchResultIndex, page: Int, galleries: List<GallerySummary>): Boolean = io {
        // Offline/error placeholders must not replace a usable page or become permanent cached results.
        if (galleries.any { it.loadError != null }) return@io false
        val byId = galleries.associateBy { it.id }
        val ordered = index.pages[page - 1].map { requireNotNull(byId[it]) }
        write(file(index.sessionId, "page-$page"), JSONObject().put("galleries", JSONArray(ordered.map { it.json() })))
    } ?: false

    suspend fun removeSessions(sessionIds: Set<String>) = io {
        val prefixes = sessionIds.mapTo(mutableSetOf(), ::prefix)
        directory.listFiles()?.filter { it.name.substringBefore('.') in prefixes }?.forEach { it.delete() }
        true
    }

    private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
            catch (_: org.json.JSONException) { null }
        }
    }

    private fun read(file: File): JSONObject? {
        if (!file.isFile || file.length() > maxBytes) return null
        return JSONObject(file.readText()).also { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun write(destination: File, value: JSONObject): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) return false
        trim(maxBytes)
        if (directory.usableSpace - bytes.size < minimumFreeBytes) {
            trim(0)
            if (directory.usableSpace - bytes.size < minimumFreeBytes) return false
        }
        val temporary = File(directory, "${destination.name}.tmp")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            trim(maxBytes)
            return destination.isFile
        } finally { temporary.delete() }
    }

    private fun trim(budget: Long) {
        val files = directory.listFiles()?.filter { it.isFile && it.extension == "json" }
            ?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        files.forEach { file ->
            if (bytes > budget) {
                val length = file.length()
                if (file.delete()) bytes -= length
            }
        }
    }

    private fun prefix(sessionId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(sessionId.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(sessionId: String, part: String) = File(directory, "${prefix(sessionId)}.$part.json")
}

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
private fun GallerySummary.json(): JSONObject = JSONObject().put("id", id).put("title", title)
    .put("artists", JSONArray(artists)).put("series", JSONArray(series)).put("galleryType", galleryType)
    .put("tags", JSONArray(tags.map { JSONObject().put("type", it.type).put("name", it.name).put("negative", it.isNegative) }))
    .put("thumbnailUrl", thumbnailUrl).put("pageCount", pageCount).put("language", language)
    .put("publishedDate", publishedDate).put("loadError", loadError)
private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.gallery(): GallerySummary = GallerySummary(
    id = getLong("id"), title = getString("title"), artists = getJSONArray("artists").strings(),
    series = getJSONArray("series").strings(), galleryType = optionalString("galleryType"),
    tags = getJSONArray("tags").let { tags -> List(tags.length()) {
        tags.getJSONObject(it).let { tag -> GalleryTag(tag.getString("type"), tag.getString("name"), tag.getBoolean("negative")) }
    } }, thumbnailUrl = optionalString("thumbnailUrl"), pageCount = getInt("pageCount"),
    language = optionalString("language"), publishedDate = optionalString("publishedDate"), loadError = optionalString("loadError"),
)
