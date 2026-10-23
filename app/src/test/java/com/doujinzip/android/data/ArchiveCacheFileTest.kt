package com.doujinzip.android.data

import java.nio.file.Files
import java.nio.file.FileSystemException
import java.util.zip.ZipException
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Test

class ArchiveCacheFileTest {
    @Test fun supportedImageExtensionsArePreservedForPagesAndCovers() {
        val root = Files.createTempDirectory("archive-cache-test").toFile()
        try {
            for (extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")) {
                for (prefix in listOf("000001", "a".repeat(64))) {
                    val file = archiveCacheFile(root, prefix, "nested/page.${extension.uppercase()}")
                    assertEquals(root.canonicalFile, file.parentFile)
                    assertEquals("$prefix.$extension", file.name)
                }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun hostileEntryNamesCannotControlTheDestination() {
        val root = Files.createTempDirectory("archive-cache-test").toFile()
        try {
            val entries = listOf("../../escape.jpg", "/absolute/page.PNG", "..\\..\\escape.webp",
                "page./../..", "page.\\..\\..", "page./../../escape", "page.jpg/../../escape",
                "page.\u0000png", "page.unknown", "no-extension", "..")
            entries.forEach { entry ->
                val file = archiveCacheFile(root, "000000", entry)
                assertEquals(entry, root.canonicalFile, file.parentFile)
                assertTrue(entry, file.name.matches(Regex("000000\\.(jpg|jpeg|png|webp|gif|bmp|avif)")))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsAnOutputSymlinkOutsideTheCacheDirectory() {
        val root = Files.createTempDirectory("archive-cache-test").toFile()
        try {
            val cache = root.resolve("pages").apply { mkdir() }
            val outside = root.resolve("outside.jpg").apply { writeText("keep") }
            try {
                Files.createSymbolicLink(cache.resolve("000000.jpg").toPath(), outside.toPath())
            } catch (error: FileSystemException) {
                assumeNoException("Host cannot create symbolic links", error)
            } catch (error: UnsupportedOperationException) {
                assumeNoException("Host does not support symbolic links", error)
            }
            assertThrows(ZipException::class.java) { archiveCacheFile(cache, "000000", "page.jpg") }
            assertEquals("keep", outside.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsAnEscapingPrefix() {
        val root = Files.createTempDirectory("archive-cache-test").toFile()
        try {
            val cache = root.resolve("pages").apply { mkdir() }
            assertThrows(ZipException::class.java) { archiveCacheFile(cache, "../pages-other/escape", "page.jpg") }
        } finally { root.deleteRecursively() }
    }
}
