package com.doujinmenu.android.data

import com.doujinmenu.android.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GalleryPresentationTest {
    @Test fun snapshotsPreserveExplicitClearsDatesAndNullableValues() {
        val value = GalleryPresentation("hitomi:123", "Edited title", LibraryMetadata(tags = emptyList(), publishedDate = "2020-01-02"), webtoon = true)
        assertEquals(value, presentationFromJson(value.toJson()))
        val metadata = metadataFromJson(JSONObject("{\"hitomiId\":null,\"language\":null,\"galleryType\":null}"))
        assertNull(metadata.hitomiId); assertNull(metadata.language); assertNull(metadata.galleryType)
        assertTrue(runCatching { value.copy(coverHash = "../cover") }.isFailure)
        assertTrue(runCatching { value.copy(key = "../../file") }.isFailure)
    }
}
