package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.StorageLocation
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {
    private val contentResolver = context.contentResolver
    suspend fun scan(locations: List<StorageLocation>): ScanResult = withContext(Dispatchers.IO) {
        val books = mutableListOf<LibraryBook>()
        val errors = mutableListOf<String>()
        locations.forEach { location ->
            runCatching { scanLocation(location, books) }
                .onFailure { errors += "${location.displayName}: ${it.message ?: "폴더를 읽을 수 없습니다."}" }
        }
        ScanResult(books.distinctBy(LibraryBook::id), errors)
    }

    private fun scanLocation(location: StorageLocation, books: MutableList<LibraryBook>) {
        val treeUri = Uri.parse(location.uri)
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        scanDirectory(treeUri, rootId, location, location.displayName, books)
    }

    private fun scanDirectory(
        treeUri: Uri,
        documentId: String,
        location: StorageLocation,
        directoryName: String,
        books: MutableList<LibraryBook>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val children = queryChildren(childrenUri)
        val imagePages = children.asSequence()
            .filter { !it.isDirectory && isSupportedImage(it.mimeType, it.name) }
            .sortedWith(compareBy<DocumentEntry> { naturalSortKey(it.name) }.thenBy { it.name })
            .map { LibraryPage(it.uri.toString(), it.name) }
            .toList()

        if (imagePages.isNotEmpty()) {
            val info = children.firstOrNull { !it.isDirectory && it.name.equals("info.txt", true) }
                ?.let { readInfo(it.uri) } ?: ParsedInfoTxt()
            val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
            books += LibraryBook(
                id = info.uuid?.let(::stableFileId) ?: folderUri,
                title = info.title ?: directoryName.ifBlank { location.displayName },
                locationUri = location.uri,
                locationName = location.displayName,
                folderUri = folderUri,
                pages = imagePages,
                modifiedAt = children.maxOfOrNull(DocumentEntry::modifiedAt) ?: 0L,
                metadata = info.metadata,
            )
        }

        children.filter { !it.isDirectory && isArchive(it.name) }.forEach { archive ->
            scanArchive(archive, location)?.let(books::add)
        }

        children.filter(DocumentEntry::isDirectory).forEach { child ->
            scanDirectory(treeUri, child.documentId, location, child.name, books)
        }
    }

    private fun scanArchive(entry: DocumentEntry, location: StorageLocation): LibraryBook? {
        val pageEntries = mutableListOf<String>()
        var info = ParsedInfoTxt()
        var coverUri = ""
        contentResolver.openInputStream(entry.uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    if (!item.isDirectory && isSupportedImage("", item.name)) {
                        pageEntries += item.name
                        if (coverUri.isEmpty()) coverUri = cacheArchiveCover(entry, item.name, zip)
                    } else if (!item.isDirectory && item.name.equals("info.txt", true)) {
                        info = InfoTxtParser.parse(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
        }
        if (pageEntries.isEmpty()) return null
        val sorted = pageEntries.sortedBy(::naturalSortKey)
        val pages = sorted.mapIndexed { index, name ->
            LibraryPage(
                uri = if (index == 0) coverUri else "",
                name = name,
                archiveUri = entry.uri.toString(),
                archiveEntry = name,
            )
        }
        return LibraryBook(
            id = info.uuid?.let(::stableFileId) ?: entry.uri.toString(),
            title = info.title ?: entry.name.substringBeforeLast('.'),
            locationUri = location.uri,
            locationName = location.displayName,
            folderUri = entry.uri.toString(),
            pages = pages,
            modifiedAt = entry.modifiedAt,
            metadata = info.metadata,
            coverUriOverride = coverUri,
        )
    }

    private fun cacheArchiveCover(entry: DocumentEntry, imageName: String, zip: ZipInputStream): String {
        val extension = imageName.substringAfterLast('.', "jpg").take(5)
        val directory = File(context.cacheDir, "library-covers").apply(File::mkdirs)
        val file = File(directory, "${entry.uri.toString().hashCode()}-${entry.modifiedAt}.$extension")
        if (!file.exists()) file.outputStream().buffered().use(zip::copyTo)
        return file.toURI().toString()
    }

    private fun readInfo(uri: Uri): ParsedInfoTxt = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            InfoTxtParser.parse(reader.readText())
        }
    }.getOrNull() ?: ParsedInfoTxt()

    private fun queryChildren(childrenUri: Uri): List<DocumentEntry> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return buildList {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
                val modifiedIndex = cursor.getColumnIndexOrThrow(projection[3])
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    add(
                        DocumentEntry(
                            documentId = documentId,
                            name = cursor.getString(nameIndex).orEmpty(),
                            mimeType = cursor.getString(mimeIndex).orEmpty(),
                            modifiedAt = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                            uri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, documentId),
                        ),
                    )
                }
            }
        }
    }

    private fun isSupportedImage(mimeType: String, name: String): Boolean =
        mimeType.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun isArchive(name: String) = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

    private fun stableFileId(uuid: String) = "file:${uuid.trim().lowercase()}"

    private data class DocumentEntry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val modifiedAt: Long,
        val uri: Uri,
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz")

        internal fun naturalSortKey(value: String): String =
            Regex("(\\d+|\\D+)").findAll(value.lowercase()).map { part ->
                val text = part.value
                if (text.firstOrNull()?.isDigit() == true) text.padStart(20, '0') else text
            }.joinToString("\u0000")
    }
}

data class ScanResult(
    val books: List<LibraryBook>,
    val errors: List<String>,
)
