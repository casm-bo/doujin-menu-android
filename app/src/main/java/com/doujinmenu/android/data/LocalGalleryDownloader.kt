package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.network.HitomiClient
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LocalGalleryDownloader(private val context: Context) {
    private val store = DownloadRequestStore.get(context)
    private val client = HitomiClient()

    suspend fun download(request: DownloadRequest, progress: (Int, Int) -> Unit): String = withContext(Dispatchers.IO) {
        val location = requireNotNull(request.locationUri) { "다운로드 폴더를 선택하세요." }
        val tree = Uri.parse(location)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val finalName = "${request.galleryId}-${request.id}.cbz"
        // A previous process may have completed the rename before recording completion.
        findDocument(tree, finalName)?.let { return@withContext it.toString() }
        val directory = stagingDirectory(context.filesDir, request.id).also { it.mkdirs() }
        fun checkRunning() {
            coroutineContext.ensureActive()
            if (store.requests.value.none { it.id == request.id && it.status == RequestStatus.RUNNING }) {
                throw kotlinx.coroutines.CancellationException("다운로드 일시정지")
            }
        }
        val gallery = client.getGallery(request.galleryId)
        val urls = client.getGalleryPages(request.galleryId)
        require(urls.isNotEmpty()) { "다운로드할 이미지가 없습니다." }
        val pages = urls.mapIndexed { index, url ->
            checkRunning()
            val hash = URL(url).path.substringAfterLast('/').substringBefore('.')
            val destination = File(directory, "$hash.webp")
            if (!destination.exists()) {
                val part = File(directory, "$hash.part")
                try {
                    downloadImage(url, part, ::checkRunning)
                    check(part.renameTo(destination)) { "다운로드 파일을 저장하지 못했습니다." }
                } finally { part.delete() }
            }
            store.change(request.id) { it.copy(downloadedFiles = index + 1, totalFiles = urls.size) }
            progress(index + 1, urls.size)
            destination
        }
        checkRunning()
        val archive = File(directory, "gallery.cbz")
        writeGalleryArchive(archive, gallery, request.id, pages, ::checkRunning)
        checkRunning()
        request.outputUri?.let { old ->
            // Only our previously persisted partial document can be removed here.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(old)) }
        }
        val partial = DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", "$finalName.part")
            ?: throw IOException("다운로드 폴더에 파일을 만들 수 없습니다.")
        store.change(request.id) { it.copy(outputUri = partial.toString()) }
        try {
            context.contentResolver.openOutputStream(partial, "wt")?.use { output ->
                archive.inputStream().use { input -> copyDownloadStream(input, output, ::checkRunning) }
            } ?: throw IOException("다운로드 파일을 쓸 수 없습니다.")
            checkRunning()
            val completed = DocumentsContract.renameDocument(context.contentResolver, partial, finalName)
                ?: throw IOException("완료 파일의 이름을 변경하지 못했습니다. 다른 저장 폴더를 선택하세요.")
            completed.toString()
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, partial) }
            store.change(request.id) { it.copy(outputUri = null) }
            throw error
        }
    }

    private fun findDocument(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return null
    }
}

internal fun stagingDirectory(filesDir: File, id: String): File {
    require(id.matches(Regex("[a-fA-F0-9-]{36}")))
    return File(File(filesDir, "download-staging"), id)
}

private fun downloadImage(url: String, destination: File, checkRunning: () -> Unit) {
    val parsed = URL(url)
    require(parsed.protocol == "https" && parsed.host.matches(Regex("w[12]\\.gold-usergeneratedcontent\\.net")))
    val connection = parsed.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Referer", "https://hitomi.la/")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (connection.responseCode != 200) throw IOException("이미지 요청 실패 (HTTP ${connection.responseCode})")
        val length = connection.contentLengthLong
        destination.outputStream().use { output ->
            connection.inputStream.use { input -> copyDownloadStream(input, output, checkRunning) }
        }
        if (length >= 0 && destination.length() != length) throw IOException("이미지 다운로드가 중단되었습니다.")
        val header = ByteArray(12)
        val valid = destination.inputStream().use { it.read(header) == 12 } &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        if (!valid) throw IOException("올바른 WebP 이미지가 아닙니다.")
    } finally { connection.disconnect() }
}

internal fun copyDownloadStream(input: java.io.InputStream, output: java.io.OutputStream, checkRunning: () -> Unit) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkRunning()
        val count = input.read(buffer)
        if (count < 0) return
        output.write(buffer, 0, count)
    }
}

internal fun writeGalleryArchive(destination: File, gallery: GallerySummary, uuid: String, pages: List<File>, checkRunning: () -> Unit) {
    fun clean(value: String) = value.replace('\n', ' ').replace('\r', ' ')
    val info = listOf(
        "UUID: $uuid", "갤러리 넘버: ${gallery.id}", "제목: ${clean(gallery.title)}",
        "작가: ${clean(gallery.artists.joinToString(", "))}", "시리즈: ${clean(gallery.series.joinToString(", "))}",
        "타입: ${clean(gallery.galleryType.orEmpty())}", "언어: ${clean(gallery.language.orEmpty())}",
        "태그: ${clean(gallery.tags.joinToString(", ") { if (it.type == "tag") it.name else "${it.type}:${it.name}" })}",
    ).joinToString("\n")
    ZipOutputStream(destination.outputStream().buffered()).use { zip ->
        zip.putNextEntry(ZipEntry("info.txt"))
        zip.write(info.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        pages.forEachIndexed { index, page ->
            checkRunning()
            zip.putNextEntry(ZipEntry("${(index + 1).toString().padStart(5, '0')}.webp"))
            page.inputStream().use { copyDownloadStream(it, zip, checkRunning) }
            zip.closeEntry()
        }
    }
}
