package com.doujinmenu.android.data

import com.doujinmenu.android.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GalleryPresentationTest {
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
        val metadata = metadataFromJson(JSONObject("{\"hitomiId\":null,\"language\":null,\"galleryType\":null}"))
        assertNull(metadata.hitomiId); assertNull(metadata.language); assertNull(metadata.galleryType)
        assertTrue(runCatching { value.copy(coverHash = "../cover") }.isFailure)
        assertTrue(runCatching { value.copy(key = "../../file") }.isFailure)
    }
}
