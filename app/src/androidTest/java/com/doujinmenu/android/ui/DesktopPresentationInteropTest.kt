package com.doujinmenu.android.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.data.*
import com.doujinmenu.android.model.*
import com.doujinmenu.android.network.CompanionClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with a disposable desktop server's desktopInteropPort and desktopInteropCode. */
class DesktopPresentationInteropTest {
    @Test fun productionDesktopAndPhoneRoundTripMetadataCoversAndConflicts() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val port = args.getString("desktopInteropPort")?.toIntOrNull()
        val code = args.getString("desktopInteropCode")
        assumeTrue("Requires a disposable desktop fixture", port != null && code != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = CompanionClient()
        val baseUrl = "http://127.0.0.1:$port"
        val pairing = client.pair(baseUrl, requireNotNull(code), "Synthetic interop phone")
        val profile = DesktopProfile("interop", "Synthetic desktop", baseUrl, pairing.token)
        val databaseName = "desktop-interop-${System.nanoTime()}.db"
        val file = File(context.cacheDir, "interop-cover.png")
        val bitmap = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            LibraryIdentityStore(context, databaseName).use { identity ->
                val store = PresentationStore(identity, profile.id)
                val covers = PresentationCovers(context)
                val sync = PresentationSync(client, store, covers)
                val seriesKey = seriesPresentationKey("Synthetic interop series")
                val coverKey = seriesCoverPresentationKey("Synthetic interop series")
                sync.sync(profile)
                val gallery = requireNotNull(store.get("hitomi:123"))
                assertEquals("PC gallery", gallery.value.title)
                assertEquals("PC series", store.get(seriesKey)?.value?.title)
                val seriesCover = requireNotNull(store.get(coverKey)?.value?.coverHash)
                assertTrue(covers.contains(seriesCover))
                val phoneCover = covers.import(ReaderImageSource(file.toURI().toString()))
                store.saveLocal(gallery.value.copy(title = "Phone gallery", coverHash = phoneCover,
                    metadata = gallery.value.metadata.copy(artists = listOf("phone_artist"), tags = listOf("phone_tag"))))
                store.saveLocal(requireNotNull(store.get(seriesKey)).value.copy(title = "Phone series",
                    metadata = LibraryMetadata(tags = listOf("series_tag"))))
                sync.sync(profile)
                val finalGallery = requireNotNull(store.get("hitomi:123"))
                assertFalse(finalGallery.dirty)
                assertNull(finalGallery.conflict)
                assertEquals("PC final gallery", finalGallery.value.title)
                assertEquals(listOf("phone_artist"), finalGallery.value.metadata.artists)
                assertEquals(phoneCover, finalGallery.value.coverHash)
                assertArrayEquals(covers.bytes(phoneCover), client.transferCover(profile, phoneCover))
                assertEquals("PC final series", store.get(seriesKey)?.value?.title)
                assertEquals(listOf("series_tag"), store.get(seriesKey)?.value?.metadata?.tags)
                assertEquals(seriesCover, store.get(coverKey)?.value?.coverHash)

                store.saveLocal(finalGallery.value.copy(title = "Pending phone edit"))
                val remote = client.savePresentation(profile, PresentationRecord(finalGallery.value.copy(title = "Concurrent edit")))
                assertEquals("applied", remote.status)
                sync.sync(profile)
                val conflict = requireNotNull(store.get("hitomi:123"))
                assertEquals("Pending phone edit", conflict.value.title)
                assertEquals("Concurrent edit", conflict.conflict?.title)
                store.saveLocal(conflict.value, resolveConflict = true)
                sync.sync(profile)
                assertEquals("Pending phone edit", store.get("hitomi:123")?.value?.title)
                assertFalse(requireNotNull(store.get("hitomi:123")).dirty)
                assertNull(store.get("hitomi:123")?.conflict)
            }
        } finally { file.delete(); context.deleteDatabase(databaseName) }
    }
}
