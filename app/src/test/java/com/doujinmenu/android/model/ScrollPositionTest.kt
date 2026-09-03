package com.doujinmenu.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollPositionTest {
    @Test
    fun anchorFollowsItemWhenOrderChanges() {
        val keys = listOf("book:new", "book:a", "book:b", "book:c")

        assertEquals(2, resolveScrollIndex("book:b", fallbackIndex = 1, itemKeys = keys))
    }

    @Test
    fun missingAnchorUsesSafeFallback() {
        assertEquals(2, resolveScrollIndex("book:removed", fallbackIndex = 9, itemKeys = listOf("a", "b", "c")))
    }
}
