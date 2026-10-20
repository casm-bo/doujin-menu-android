package com.doujinmenu.android.ui

import org.junit.Assert.*
import org.junit.Test

class WorkCardTest {
    @Test fun shortTagsShareRowsAndOverflowReservesRealSpace() {
        val all = packTags(listOf(20, 20, 20), 100, 2, 4, 12)
        assertEquals(listOf(0, 0, 0), all.tags.map { it.row })
        assertNull(all.overflow)
        val more = packTags(listOf(45, 45, 45, 45, 45), 100, 2, 4, 12)
        assertEquals(3, more.tags.size)
        assertEquals(TagPlacement(-1, 49, 1), more.overflow)
    }
    @Test fun longTagsAndLargeFontNeverPlaceEllipsisOutsideBounds() {
        for (width in listOf(40, 100, 300)) {
            val result = packTags(listOf(500, 500, 500, 500), width, 2, 4, 16)
            assertTrue(result.tags.all { it.x < width && it.row in 0..1 })
            assertNotNull(result.overflow)
            assertTrue(result.overflow!!.x + 16 <= width)
        }
        assertEquals(emptyList<TagPlacement>(), packTags(emptyList(), 100, 3, 4, 12).tags)
    }
}
