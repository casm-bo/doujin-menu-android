package com.doujinmenu.android.ui

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BrowserTabPreviewsTest {
    @Test
    fun cacheBudgetEvictsOldestImagesWithoutTouchingOtherFiles() {
        val directory = Files.createTempDirectory("tab-previews-test").toFile()
        try {
            val oldest = directory.resolve("old.jpg").apply { writeBytes(ByteArray(40)); setLastModified(1000) }
            val newest = directory.resolve("new.jpg").apply { writeBytes(ByteArray(60)); setLastModified(2000) }
            val unrelated = directory.resolve("metadata.json").apply { writeText("keep") }
            trimTabPreviewFiles(directory, 60)
            assertFalse(oldest.exists())
            assertTrue(newest.exists())
            assertTrue(unrelated.exists())
            trimTabPreviewFiles(directory, 0)
            assertFalse(newest.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
