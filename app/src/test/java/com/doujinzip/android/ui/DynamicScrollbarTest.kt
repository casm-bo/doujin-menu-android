package com.doujinzip.android.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicScrollbarTest {
    @Test fun pixelOffsetMovesThumbWithinTheSameRowAndDragKeepsSubrowPrecision() {
        val before = scrollbarMetrics(10, 10, 100)!!
        val after = scrollbarMetrics(10, 10, 100, 1f)!!
        assertTrue(after.positionFraction > before.positionFraction)
        val target = scrollbarTarget(113f, 1000f, before, 10, 100, 200f, 2)
        assertEquals(6, target.index)
        assertTrue(target.offset > 0)
        assertEquals(ScrollbarTarget(99, 0), scrollbarTarget(1000f, 1000f, before, 10, 100, 200f, 2))
        assertEquals(ScrollbarTarget(0, 0), scrollbarTarget(-100f, 1000f, before, 10, 100, 200f, 2))
    }
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

    @Test
    fun dragPositionMapsToScrollableItemRange() {
        val metrics = ScrollbarMetrics(positionFraction = 0f, sizeFraction = 0.1f)

        assertEquals(45, scrollbarTargetIndex(50f, 100f, metrics, visibleItemCount = 10, totalItemCount = 100))
        assertEquals(0, scrollbarTargetIndex(0f, 100f, metrics, visibleItemCount = 10, totalItemCount = 100))
        assertEquals(90, scrollbarTargetIndex(100f, 100f, metrics, visibleItemCount = 10, totalItemCount = 100))
    }
}
