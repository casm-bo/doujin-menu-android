package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryMetadata
import com.doujinmenu.android.model.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryGalleryMatchTest {
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
