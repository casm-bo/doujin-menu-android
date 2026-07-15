package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import com.doujinmenu.android.model.LibraryBook
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryArchiveExtractor(private val context: Context) {
    suspend fun materialize(book: LibraryBook): LibraryBook = withContext(Dispatchers.IO) {
        val archiveUri = book.pages.firstOrNull()?.archiveUri ?: return@withContext book
        val wanted = book.pages.mapNotNull { it.archiveEntry }.toSet()
        val outputDirectory = File(context.cacheDir, "library-pages/${book.id.hashCode()}")
            .apply(File::mkdirs)
        val files = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(Uri.parse(archiveUri))?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in wanted) {
                        val index = book.pages.indexOfFirst { it.archiveEntry == entry.name }
                        val extension = entry.name.substringAfterLast('.', "jpg").take(5)
                        val file = File(outputDirectory, "%06d.%s".format(index, extension))
                        if (!file.exists() || file.length() == 0L) {
                            file.outputStream().buffered().use(zip::copyTo)
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
}
