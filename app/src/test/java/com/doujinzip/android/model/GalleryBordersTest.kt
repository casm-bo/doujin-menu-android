package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class GalleryBordersTest {
    @Test fun favoriteOverridesSeriesAndFallsBackAfterRemoval() {
        val borders = GalleryBorders()
        assertEquals(borders.favorite, borders.styleFor(series = true, favorite = true))
        assertEquals(borders.favorite, borders.styleFor(series = false, favorite = true))
        assertEquals(borders.series, borders.styleFor(series = true, favorite = false))
        assertEquals(borders.normal, borders.styleFor(series = false, favorite = false))
    }
    @Test fun `normal and series styles are independent and colors are opaque`() {
        val original = GalleryBorders()
        val changed = original.copy(normal = GalleryBorderStyle(4f, parseBorderColor("#12ab34")!!))
        assertEquals(original.series, changed.series)
        assertEquals(0xFF12AB34, changed.normal.colorArgb)
        assertNull(parseBorderColor("bad-input"))
        assertTrue(runCatching { GalleryBorderStyle(Float.NaN) }.isFailure)
        assertTrue(runCatching { GalleryBorderStyle(7f) }.isFailure)
    }
}
