package com.doujinmenu.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicScrollbarTest {
    @Test
    fun metricsTrackViewportWithinScrollableContent() {
        val metrics = scrollbarMetrics(firstVisibleItemIndex = 45, visibleItemCount = 10, totalItemCount = 100)!!

        assertEquals(0.5f, metrics.positionFraction, 0.001f)
        assertEquals(0.1f, metrics.sizeFraction, 0.001f)
    }

    @Test
    fun scrollbarIsHiddenWhenAllItemsFit() {
        assertNull(scrollbarMetrics(firstVisibleItemIndex = 0, visibleItemCount = 5, totalItemCount = 5))
    }
}
