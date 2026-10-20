package com.doujinmenu.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.data.*
import com.doujinmenu.android.model.*
import org.junit.Assert.*
import org.junit.Test

class PresentationStoreTest {
    @Test fun externalJpegOrientationIsAppliedToTheSavedCover() = kotlinx.coroutines.runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = java.io.File(context.cacheDir, "rotated-cover-test.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(800, 400, android.graphics.Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        android.media.ExifInterface(source.absolutePath).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
        }
        val store = PresentationCovers(context)
        var hash: String? = null
        try {
            hash = store.import(ReaderImageSource(source.toURI().toString()))
            val size = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(store.file(hash).absolutePath, size)
            assertTrue(size.outHeight > size.outWidth)
        } finally { source.delete(); hash?.let { store.file(it).delete() } }
    }
    @Test fun selectedCoverIsCopiedBoundedAndVerifiedIndependentlyOfItsSource() = kotlinx.coroutines.runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = java.io.File(context.cacheDir, "external-cover-test.png")
        val bitmap = android.graphics.Bitmap.createBitmap(1000, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = PresentationCovers(context)
        var hash: String? = null
        try {
            hash = store.import(ReaderImageSource(source.toURI().toString()))
            source.delete()
            assertTrue(store.contains(hash))
            val size = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val bytes = store.bytes(hash)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
            assertTrue(size.outWidth <= 512 && size.outHeight <= 1024)
            assertTrue(runCatching { store.put(hash, byteArrayOf(1, 2, 3)) }.isFailure)
            assertTrue(runCatching { store.file("../escape") }.isFailure)
        } finally { source.delete(); hash?.let { store.file(it).delete() } }
    }
    @Test fun newerEditsSurviveLateAcknowledgementsConflictsAndRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "presentation-test-${System.nanoTime()}.db"
        val identity = LibraryIdentityStore(context, name)
        try {
            val store = PresentationStore(identity)
            val value = GalleryPresentation("hitomi:42", "First", LibraryMetadata(tags = listOf("source")))
            val first = store.saveLocal(value)
            val second = store.saveLocal(value.copy(title = "Second", metadata = LibraryMetadata(tags = emptyList())))
            store.acknowledge(value.key, first.mutationId, value.copy(version = 1, revision = 1))
            assertEquals("Second", store.get(value.key)!!.value.title)
            assertTrue(store.get(value.key)!!.dirty)
            assertEquals(1, store.get(value.key)!!.value.version)
            val remote = value.copy(title = "PC edited", version = 3, revision = 5)
            store.receive(remote)
            store.receive(remote.copy(version = 2, title = "Older"))
            store.acknowledge(value.key, second.mutationId, value.copy(version = 2, revision = 4))
            assertEquals(remote, store.get(value.key)!!.conflict)
            assertTrue(store.pending().isEmpty())
            val resolved = store.saveLocal(store.get(value.key)!!.value, resolveConflict = true)
            assertEquals(3, resolved.value.version)
            store.acknowledge(value.key, resolved.mutationId, resolved.value.copy(version = 4, revision = 6))
            assertFalse(store.get(value.key)!!.dirty)
            store.saveCursor("pc", 6)
            identity.close()
            LibraryIdentityStore(context, name).use { reopened ->
                val saved = PresentationStore(reopened)
                assertEquals("Second", saved.get(value.key)!!.value.title)
                assertTrue(saved.get(value.key)!!.value.metadata.tags.isEmpty())
                assertEquals(6, saved.cursor("pc"))
            }
        } finally { identity.close(); context.deleteDatabase(name) }
    }

    @Test fun upgradingScanDatabaseKeepsIdentityAndRescansCannotOverwriteUserEdits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "presentation-upgrade-${System.nanoTime()}.db"
        try {
            val original = LibraryIdentityStore(context, name).use { old ->
                val value = old.resolve("folder:fixture", "fixture", ParsedInfoTxt(title = "File title"), 1)
                old.writableDatabase.execSQL("DROP TABLE gallery_presentations")
                old.writableDatabase.execSQL("DROP TABLE presentation_sync_cursor")
                old.writableDatabase.version = 1
                value
            }
            LibraryIdentityStore(context, name).use { upgraded ->
                val scanned = upgraded.resolve("folder:fixture", "fixture", ParsedInfoTxt(title = "Updated file title"), 2)
                assertEquals(original.syncId, scanned.syncId)
                val store = PresentationStore(upgraded)
                val edit = GalleryPresentation("sync:${scanned.syncId}", "User title", LibraryMetadata(tags = emptyList()))
                store.saveLocal(edit)
                upgraded.resolve("folder:fixture", "fixture", ParsedInfoTxt(title = "File changed again"), 3)
                assertEquals(edit, store.get(edit.key)!!.value)
            }
        } finally { context.deleteDatabase(name) }
    }
}
