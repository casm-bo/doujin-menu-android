package com.doujinmenu.android.data

import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.GalleryTag
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class LocalGalleryDownloaderTest {
    @Test fun `completed archive is readable by existing metadata parser and preserves page order`() {
        val directory = Files.createTempDirectory("local-download-test").toFile()
        try {
            val pages = List(12) { index -> File(directory, "source-$index").apply { writeText("page $index") } }
            val archive = File(directory, "test.cbz")
            val gallery = GallerySummary(42, "Example\n제목: injected", listOf("a"), listOf("b"), "manga",
                listOf(GalleryTag("female", "sample")), null, 12, "korean")
            writeGalleryArchive(archive, gallery, "stable-uuid", pages) {}
            ZipFile(archive).use { zip ->
                val info = InfoTxtParser.parse(zip.getInputStream(zip.getEntry("info.txt")).reader().readText())
                assertEquals("42", info.metadata.hitomiId)
                assertEquals("stable-uuid", info.uuid)
                assertEquals("Example 제목: injected", info.title)
                assertEquals(listOf("female:sample"), info.metadata.tags)
                assertEquals("page 11", zip.getInputStream(zip.getEntry("00012.webp")).reader().readText())
                assertEquals(13, zip.size())
            }
        } finally { directory.deleteRecursively() }
    }

    @Test(expected = IllegalArgumentException::class) fun `staging cannot escape its app directory`() {
        stagingDirectory(File("root"), "../../outside")
    }

    @Test fun `pause stops copying before another chunk is written`() {
        val output = java.io.ByteArrayOutputStream()
        var checks = 0
        try {
            copyDownloadStream(ByteArray(200_000).inputStream(), output) {
                if (++checks == 2) throw kotlinx.coroutines.CancellationException("paused")
            }
            fail("Copy must stop")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(65_536, output.size())
        }
    }
}
