package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.doujinmenu.android.model.LibraryBook
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class LibraryArchiveExtractor(private val context: Context) {
    suspend fun materialize(book: LibraryBook): LibraryBook = withContext(Dispatchers.IO) {
        val archiveUri = book.pages.firstOrNull()?.archiveUri ?: return@withContext book
        val uri = Uri.parse(archiveUri)
        val (modifiedAt, size) = sourceVersion(uri)
        val reusable = modifiedAt > 0 && size > 0
        val wanted = linkedMapOf<String, Int>()
        book.pages.forEachIndexed { index, page -> page.archiveEntry?.let { wanted.putIfAbsent(it, index) } }
        val outputDirectory = File(context.cacheDir, "library-pages/${archiveFingerprint(archiveUri, modifiedAt, size)}")
            .apply(File::mkdirs)
        fun pageFile(name: String, index: Int) = archiveCacheFile(outputDirectory, "%06d".format(index), name)
        val files = mutableMapOf<String, String>()
        if (reusable && wanted.all { (name, index) -> pageFile(name, index).let { it.isFile && it.length() > 0 } }) {
            return@withContext book.copy(pages = book.pages.map { page -> page.copy(uri = page.archiveEntry?.let { pageFile(it, wanted.getValue(it)).toURI().toString() } ?: page.uri) })
        }
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in wanted) {
                        val file = pageFile(entry.name, wanted.getValue(entry.name))
                        if (!reusable || !file.exists() || file.length() == 0L) {
                            val temporary = File(outputDirectory, "${file.name}.tmp")
                            try {
                                temporary.outputStream().buffered().use(zip::copyTo)
                                check(temporary.renameTo(file)) { "Cannot cache archive page" }
                            } finally { temporary.delete() }
                        }
                        files[entry.name] = file.toURI().toString()
                    }
                    zip.closeEntry()
                }
            }
        }
        book.copy(pages = book.pages.map { page ->
            page.copy(uri = files[page.archiveEntry] ?: page.uri)
        })
    }

    private fun sourceVersion(uri: Uri): Pair<Long, Long> = runCatching {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            file.lastModified() to file.length()
        } else context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else 0L to 0L
        } ?: (0L to 0L)
    }.getOrDefault(0L to 0L)
}
