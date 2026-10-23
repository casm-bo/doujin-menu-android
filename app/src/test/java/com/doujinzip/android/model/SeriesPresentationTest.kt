package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class SeriesPresentationTest {
    @Test fun identityIsBoundedUnicodeSafeAndIndependentOfEpisodeIdentity() {
        val name = "시리즈 / A:B"
        val key = seriesPresentationKey(name)
        assertEquals(key, seriesPresentationKey(name))
        assertNotEquals(key, seriesPresentationKey("Other"))
        assertNotEquals(key, seriesCoverPresentationKey(name))
        assertTrue(seriesCoverPresentationKey(name).matches(Regex("sync:series_cover_[a-f0-9]{64}")))
        assertTrue(key.matches(Regex("sync:series_[a-f0-9]{64}")))
        val value = GalleryPresentation(key, name, LibraryMetadata(series = listOf("Original IP")))
        assertEquals(listOf("Original IP"), value.metadata.series)
        assertTrue(runCatching { seriesPresentationKey(" ") }.isFailure)
    }
}
