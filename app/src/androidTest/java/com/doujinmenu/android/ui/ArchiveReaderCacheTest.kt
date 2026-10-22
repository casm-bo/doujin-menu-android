package com.doujinmenu.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.data.LibraryArchiveExtractor
import com.doujinmenu.android.model.*
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArchiveReaderCacheTest {
    @Test fun reusesCompletePagesButInvalidatesChangedArchives() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.cacheDir, "archive-fixture-${System.nanoTime()}.cbz")
        fun write(content: String) { ZipOutputStream(archive.outputStream()).use { zip ->
            listOf("2.jpg", "1.jpg").forEach { zip.putNextEntry(ZipEntry(it)); zip.write("$content-$it".toByteArray()); zip.closeEntry() }
        } }
        val created = mutableSetOf<File>()
        try {
            write("first")
            val book = LibraryBook("cache-fixture", "Fixture", locationUri = "fixture", locationName = "Fixture", folderUri = archive.toURI().toString(),
                pages = listOf("1.jpg", "2.jpg").map { LibraryPage("", it, archive.toURI().toString(), it) }, modifiedAt = 1)
            val extractor = LibraryArchiveExtractor(context)
            val first = extractor.materialize(book)
            val firstFile = File(URI(first.pages[0].uri)); created += firstFile.parentFile!!
            assertEquals("first-1.jpg", firstFile.readText())
            firstFile.setLastModified(1)
            assertEquals(first.pages, extractor.materialize(book).pages)
            assertEquals("Complete pages should not be rewritten", 1, firstFile.lastModified())
            write("changed-and-longer")
            val changed = extractor.materialize(book)
            created += File(URI(changed.pages[0].uri)).parentFile!!
            assertNotEquals(first.pages[0].uri, changed.pages[0].uri)
            assertEquals("changed-and-longer-1.jpg", File(URI(changed.pages[0].uri)).readText())
        } finally { archive.delete(); created.forEach { it.deleteRecursively() } }
    }
}
