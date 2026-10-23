package com.doujinzip.android.ui

import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.LibraryMetadata
import com.doujinzip.android.model.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryGalleryMatchTest {
    @Test fun searchIndexPreservesDevicePreferenceAndHiddenCopiesInOnePass() {
        val cloud = book("cloud", "12345", true)
        val device = book("device", "12345", false)
        val other = book("other", "67890", true)
        val invalid = book("invalid", "not-a-gallery-id", false)
        val books = listOf(cloud, device, other, invalid)
        assertEquals(mapOf(12345L to device, 67890L to other), preferredLibraryBooksByGalleryId(books, emptySet()))
        assertEquals(mapOf(12345L to cloud), preferredLibraryBooksByGalleryId(books, setOf("device", "other")))
        assertEquals(device, preferredLibraryBooksByGalleryId(listOf(device, cloud), emptySet())[12345])
    }
    @Test
    fun matchesSearchGalleryToCloudLibraryBookByHitomiId() {
        val cloud = book(id = "cloud:12345", hitomiId = "12345", isCloud = true)

        assertEquals(cloud, preferredLibraryBookForGalleryId(listOf(cloud), 12345L))
        assertNull(preferredLibraryBookForGalleryId(listOf(cloud), 999L))
    }

    @Test
    fun prefersDeviceCopyWhenGalleryExistsInBothLocations() {
        val cloud = book(id = "cloud:12345", hitomiId = "12345", isCloud = true)
        val device = book(id = "device:12345", hitomiId = "12345", isCloud = false)

        assertEquals(device, preferredLibraryBookForGalleryId(listOf(cloud, device), 12345L))
    }

    private fun book(id: String, hitomiId: String, isCloud: Boolean) = LibraryBook(
        id = id,
        title = id,
        locationUri = id,
        locationName = id,
        folderUri = id,
        pages = listOf(LibraryPage("content://$id/1", "1")),
        modifiedAt = 0L,
        metadata = LibraryMetadata(hitomiId = hitomiId),
        isCloud = isCloud,
    )
}
