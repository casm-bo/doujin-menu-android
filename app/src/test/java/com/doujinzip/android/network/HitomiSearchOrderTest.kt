package com.doujinzip.android.network

import org.junit.Assert.*
import org.junit.Test

class HitomiSearchOrderTest {
    @Test fun `sort directives are removed from resource terms`() {
        val order = parseHitomiSearchOrder("language:korean sort:oldest -tag:guro")
        assertEquals(listOf("language:korean", "-tag:guro"), order.terms)
        assertEquals(listOf(1L, 3L, 8L), order.sortIds(listOf(8L, 1L, 3L)))
    }
    @Test fun `newest default and last directive win`() {
        assertEquals(listOf(8L, 3L, 1L), parseHitomiSearchOrder("artist:sample").sortIds(listOf(1L, 8L, 3L)))
        assertFalse(parseHitomiSearchOrder("sort:oldest sort:newest").oldest)
        assertTrue(parseHitomiSearchOrder("SORT:OLDEST").oldest)
    }
}
