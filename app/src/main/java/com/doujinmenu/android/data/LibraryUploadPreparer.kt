package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import com.doujinmenu.android.model.LibraryBook
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryUploadPreparer(private val context: Context) {
    suspend fun prepare(book: LibraryBook): PreparedLibraryUpload = withContext(Dispatchers.IO) {
        val syncId = requireNotNull(book.syncId) { "UUID가 없는 파일은 업로드할 수 없습니다." }
        val directory = File(context.cacheDir, "library-uploads").apply(File::mkdirs)
        val safeStem = book.title
            .replace(Regex("[<>:\"/\\\\|?*\\p{Cc}]"), "_")
            .trim().trimEnd('.', ' ')
            .take(120)
            .ifBlank { syncId }
        val output = File(directory, "$syncId.cbz")
        val archiveUri = book.pages.firstOrNull()?.archiveUri
        if (archiveUri != null) {
            context.contentResolver.openInputStream(Uri.parse(archiveUri))?.buffered()?.use { input ->
                output.outputStream().buffered().use(input::copyTo)
            } ?: error("압축 파일을 읽을 수 없습니다.")
        } else {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                book.pages.forEachIndexed { index, page ->
                    val entryName = page.name.substringAfterLast('/').ifBlank { "%04d.jpg".format(index + 1) }
                    zip.putNextEntry(ZipEntry(entryName))
                    context.contentResolver.openInputStream(Uri.parse(page.uri))?.buffered()?.use { input ->
                        input.copyTo(zip)
                    } ?: error("${page.name} 파일을 읽을 수 없습니다.")
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("info.txt"))
                val info = buildString {
                    appendLine("UUID: $syncId")
                    appendLine("제목: ${book.title.replace('\n', ' ').replace('\r', ' ')}")
                    book.metadata.hitomiId?.let { appendLine("갤러리 번호: $it") }
                    book.metadata.artists.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("작가: ${it.joinToString(", ")}") }
                    book.metadata.groups.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("그룹: ${it.joinToString(", ")}") }
                    book.metadata.galleryType?.let { appendLine("종류: $it") }
                    book.metadata.series.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("시리즈: ${it.joinToString(", ")}") }
                    book.metadata.characters.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("캐릭터: ${it.joinToString(", ")}") }
                    book.metadata.tags.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("태그: ${it.joinToString(", ")}") }
                    book.metadata.language?.let { appendLine("언어: $it") }
                }
                zip.write(info.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        PreparedLibraryUpload(output, "$safeStem.cbz", syncId)
    }
}

data class PreparedLibraryUpload(
    val file: File,
    val fileName: String,
    val syncId: String,
)
