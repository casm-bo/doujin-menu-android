package com.doujinmenu.android.network

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import com.doujinmenu.android.model.SearchResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Direct resource protocol, compatible with node-hitomi 9 (no companion server). */
class HitomiClient {
    private val searches = linkedMapOf<String, Pair<Long, List<Long>>>()

    suspend fun search(searchQuery: String, page: Int = 1): SearchResult = withContext(Dispatchers.IO) {
        require(page > 0)
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        val terms = query.split(Regex("\\s+")).filter(String::isNotBlank)
        val directId = terms.firstNotNullOfOrNull { it.removePrefix("id:").toLongOrNull()?.takeIf { id -> id > 0 } }
        if (directId != null) return@withContext SearchResult(if (page == 1) listOf(directId) else emptyList(), false)
        if (terms.size == 1 && ':' in terms[0] && !terms[0].startsWith('-')) {
            val start = (page.toLong() - 1) * 30 * 4
            val ids = decodeHitomiIds(readResource(hitomiNozomiPath(terms[0]), start..start + 123, missingIsEmpty = true))
            return@withContext SearchResult(ids.take(30), ids.size > 30)
        }
        val cached = synchronized(searches) { searches[query] }
        val ids = if (cached != null && System.currentTimeMillis() - cached.first < 300_000) cached.second else {
            var result: MutableSet<Long>? = null
            val negative = mutableListOf<String>()
            for (term in terms) {
                coroutineContext.ensureActive()
                if (term.startsWith('-')) { negative += term.drop(1); continue }
                val matches = termIds(term)
                if (result == null) result = matches.toMutableSet() else result.retainAll(matches.toSet())
                if (result.isEmpty()) break
            }
            if (result == null) result = nozomi("/n/index-all.nozomi").toMutableSet()
            for (term in negative) result.removeAll(termIds(term).toSet())
            result.sortedDescending().also { found ->
                synchronized(searches) {
                    if (searches.size >= 3) searches.remove(searches.keys.first())
                    searches[query] = System.currentTimeMillis() to found
                }
            }
        }
        val start = ((page.toLong() - 1) * 30).coerceAtMost(ids.size.toLong()).toInt()
        SearchResult(ids.subList(start, minOf(start + 30, ids.size)), start + 30 < ids.size)
    }

    private suspend fun termIds(term: String): List<Long> {
        if (':' !in term) return titleIds(term)
        return nozomi(hitomiNozomiPath(term))
    }

    private suspend fun nozomi(path: String): List<Long> = decodeHitomiIds(readResource(path, missingIsEmpty = true))

    private suspend fun titleIds(term: String): List<Long> {
        val version = readResource("/galleriesindex/version").toString(Charsets.UTF_8).trim()
        require(version.matches(Regex("[0-9]+"))) { "검색 인덱스 버전이 올바르지 않습니다." }
        val key = MessageDigest.getInstance("SHA-256").digest(term.toByteArray(Charsets.UTF_8)).copyOf(4)
        var address = 0L
        val visited = mutableSetOf<Long>()
        repeat(64) {
            require(address >= 0 && visited.add(address)) { "검색 인덱스가 올바르지 않습니다." }
            val node = parseHitomiIndexNode(readResource("/galleriesindex/galleries.$version.index", address..address + 463))
            val index = node.keys.indexOfFirst { compareHitomiKeys(key, it) <= 0 }.let { if (it < 0) node.keys.size else it }
            if (index < node.keys.size && compareHitomiKeys(key, node.keys[index]) == 0) {
                val (offset, length) = node.data[index]
                require(offset >= 0 && length >= 4 && length <= 64 * 1024 * 1024)
                return decodeHitomiIds(readResource("/galleriesindex/galleries.$version.data", offset + 4..offset + length - 1))
            }
            address = node.children[index]
            if (address == 0L) return emptyList()
        }
        throw IOException("검색 인덱스 탐색 한도를 초과했습니다.")
    }

    suspend fun getGallery(id: Long): GallerySummary = withContext(Dispatchers.IO) { parseDirectGallery(rawGallery(id), id) }

    private suspend fun rawGallery(id: Long): JSONObject {
        require(id > 0)
        val text = readResource("/galleries/$id.js").toString(Charsets.UTF_8)
        require(text.trimStart().startsWith("var galleryinfo")) { "작품 정보 응답이 올바르지 않습니다." }
        return JSONObject(text.substringAfter('=').trim().removeSuffix(";"))
    }

    suspend fun getGalleryPages(id: Long): List<String> = withContext(Dispatchers.IO) {
        val raw = rawGallery(id)
        val context = parseHitomiImageContext(readResource("/gg.js").toString(Charsets.UTF_8))
        val files = raw.getJSONArray("files")
        List(files.length()) { index -> context.imageUrl(files.getJSONObject(index).getString("hash")) }
    }

    private suspend fun readResource(path: String, range: LongRange? = null, missingIsEmpty: Boolean = false): ByteArray =
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val connection = URL("https://ltn.gold-usergeneratedcontent.net$path").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Referer", "https://hitomi.la/")
                range?.let {
                    connection.setRequestProperty("Range", "bytes=${it.first}-${it.last}")
                    connection.setRequestProperty("Accept-Encoding", "identity")
                }
                val status = connection.responseCode
                if (status in listOf(404, 416) && missingIsEmpty) return@withContext byteArrayOf()
                if (status !in listOf(200, 206)) throw IOException("Hitomi 요청 실패 (HTTP $status)")
                if (range != null && (status != 206 || !connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes ${range.first}-"))) {
                    throw IOException("Hitomi 검색 범위 응답이 올바르지 않습니다.")
                }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 64 * 1024 * 1024) { "Hitomi 응답이 너무 큽니다." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
        }
}

internal fun decodeHitomiIds(bytes: ByteArray): List<Long> {
    require(bytes.size % 4 == 0) { "작품 목록 응답이 올바르지 않습니다." }
    val buffer = ByteBuffer.wrap(bytes)
    return List(bytes.size / 4) { buffer.int.toLong() and 0xffffffffL }.filter { it > 0 }
}

internal data class HitomiIndexNode(val keys: List<ByteArray>, val data: List<Pair<Long, Int>>, val children: List<Long>)

internal fun parseHitomiIndexNode(bytes: ByteArray): HitomiIndexNode {
    val buffer = ByteBuffer.wrap(bytes)
    val count = buffer.int.also { require(it in 0..16) }
    val keys = List(count) {
        val size = buffer.int.also { require(it in 1..31) }
        ByteArray(size).also(buffer::get)
    }
    val dataCount = buffer.int.also { require(it == count) }
    val data = List(dataCount) { buffer.long to buffer.int }
    return HitomiIndexNode(keys, data, List(17) { buffer.long })
}

internal fun compareHitomiKeys(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val comparison = (a[i].toInt() and 255).compareTo(b[i].toInt() and 255)
        if (comparison != 0) return comparison
    }
    return 0
}

internal data class HitomiImageContext(val cases: Set<Int>, val defaultValue: Int, val prefix: String) {
    fun imageUrl(hash: String): String {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "이미지 해시가 올바르지 않습니다." }
        val code = (hash.takeLast(1) + hash.takeLast(3).take(2)).toInt(16)
        val suffix = if ((code in cases) == (defaultValue == 0)) "2" else "1"
        return "https://w$suffix.gold-usergeneratedcontent.net/$prefix$code/$hash.webp"
    }
}

internal fun parseHitomiImageContext(script: String): HitomiImageContext {
    val cases = Regex("case\\s+(\\d+)\\s*:").findAll(script).map { it.groupValues[1].toInt() }.toSet()
    val defaultValue = Regex("var\\s+o\\s*=\\s*([01])\\s*;").find(script)?.groupValues?.get(1)?.toInt()
    val prefix = Regex("\\bb\\s*:\\s*['\"]([0-9]+/)['\"]").find(script)?.groupValues?.get(1)
    require(cases.isNotEmpty() && defaultValue != null && prefix != null) { "이미지 주소 규칙을 읽을 수 없습니다." }
    return HitomiImageContext(cases, defaultValue, prefix)
}

internal fun parseDirectGallery(raw: JSONObject, id: Long): GallerySummary {
    fun names(key: String, field: String): List<String> {
        val array = raw.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it).optString(field) }.filter(String::isNotBlank)
    }
    val files = raw.getJSONArray("files")
    val hash = files.optJSONObject(0)?.optString("hash").orEmpty()
    val tags = raw.optJSONArray("tags")
    return GallerySummary(
        id = id, title = raw.optString("title", "Gallery #$id"),
        artists = names("artists", "artist"), series = names("parodys", "parody"),
        galleryType = raw.optString("type").takeIf(String::isNotBlank),
        tags = List(tags?.length() ?: 0) { index ->
            val tag = tags!!.getJSONObject(index)
            GalleryTag(when { tag.optInt("male") == 1 -> "male"; tag.optInt("female") == 1 -> "female"; else -> "tag" }, tag.getString("tag"))
        },
        thumbnailUrl = hash.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.let {
            "https://tn.gold-usergeneratedcontent.net/webpbigtn/${it.takeLast(1)}/${it.takeLast(3).take(2)}/$it.webp"
        },
        pageCount = files.length(), language = raw.optString("language_localname").ifBlank { raw.optString("language") },
        publishedDate = raw.optString("datepublished").ifBlank { raw.optString("date") }.takeIf(String::isNotBlank),
    )
}

internal fun hitomiNozomiPath(term: String): String {
    val type = term.substringBefore(':')
    val value = term.substringAfter(':').replace('_', ' ')
    require(type in setOf("language", "artist", "group", "series", "character", "type", "tag", "male", "female")) {
        "지원하지 않는 검색 필터: $type"
    }
    require(value.isNotBlank()) { "검색 필터 값이 비어 있습니다." }
    val encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return when (type) {
        "language" -> "/n/index-$encoded.nozomi"
        "male", "female" -> "/n/tag/$type:$encoded-all.nozomi"
        else -> "/n/$type/$encoded-all.nozomi"
    }
}
