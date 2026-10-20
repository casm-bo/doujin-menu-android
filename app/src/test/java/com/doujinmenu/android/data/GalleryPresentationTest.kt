package com.doujinmenu.android.data

import com.doujinmenu.android.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GalleryPresentationTest {
    @Test fun sourceUploadDateSurvivesAnOlderMetadataSnapshot() {
        val gallery = GallerySummary(123, "Source", emptyList(), emptyList(), null, emptyList(), null, 1, null, publishedDate = "2020-01-02")
        val old = gallery.presentation().copy(title = "Edited", metadata = LibraryMetadata(hitomiId = "123"))
        val display = gallery.withPresentation(old)
        assertEquals("Edited", display.title)
        assertEquals("2020-01-02", display.publishedDate)
        assertEquals("2021-02-03", gallery.withPresentation(old.copy(metadata = old.metadata.copy(publishedDate = "2021-02-03"))).publishedDate)
    }
    @Test fun metadataOverridesDoNotTouchPageSourcesAndPreserveClearedTags() {
        val book = LibraryBook("id", "Raw", locationUri = "device", locationName = "Device", folderUri = "folder",
            pages = listOf(LibraryPage("file:/original.png", "1")), modifiedAt = 1, syncId = "uuid", metadata = LibraryMetadata(tags = listOf("raw"), publishedDate = "2020-01-02"))
        val edit = book.presentation().copy(title = "Edited", metadata = LibraryMetadata(tags = emptyList()))
        val display = book.withPresentation(edit, "file:/user-cover.image")
        assertEquals(book.pages, display.pages)
        assertTrue(display.metadata.tags.isEmpty())
        assertEquals("2020-01-02", display.metadata.publishedDate)
        assertEquals("file:/user-cover.image", display.coverUri)
        assertEquals(listOf("artist", "other"), com.doujinmenu.android.ui.parseEditedFacets(" artist \nartist\n\nother"))
    }
    @Test fun snapshotsPreserveExplicitClearsDatesAndNullableValues() {
        val value = GalleryPresentation("hitomi:123", "Edited title", LibraryMetadata(tags = emptyList(), publishedDate = "2020-01-02"), webtoon = true)
        assertEquals(value, presentationFromJson(value.toJson()))
        val book = LibraryBook("id", "Raw", locationUri = "device", locationName = "Device", folderUri = "folder", pages = emptyList(), modifiedAt = 1, syncId = "uuid")
        val withTag = book.withPresentation(value)
        assertEquals(true, withTag.webtoon)
        assertTrue(withTag.metadata.tags.isEmpty())
        assertEquals(true, withTag.presentation().webtoon)
        assertEquals(false, book.withPresentation(value.copy(webtoon = false)).webtoon)
        val metadata = metadataFromJson(JSONObject("{\"hitomiId\":null,\"language\":null,\"galleryType\":null}"))
        assertNull(metadata.hitomiId); assertNull(metadata.language); assertNull(metadata.galleryType)
        assertTrue(runCatching { value.copy(coverHash = "../cover") }.isFailure)
        assertTrue(runCatching { value.copy(key = "../../file") }.isFailure)
    }
}
