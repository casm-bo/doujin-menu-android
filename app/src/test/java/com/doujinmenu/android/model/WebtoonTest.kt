package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class WebtoonTest {
    @Test fun classificationUsesSeveralBodyPagesAndFallsBackWhenUncertain() {
        assertEquals(ViewerReadingMode.WEBTOON, detectReadingMode(listOf(3000, 4000, 5000, 4500).map { ImageDimensions(1000, it) }))
        assertEquals(ViewerReadingMode.PAGE, detectReadingMode(List(5) { ImageDimensions(1000, 1500) }))
        assertEquals(ViewerReadingMode.PAGE, detectReadingMode(listOf(6000, 1400, 1300, 4000, 1000).map { ImageDimensions(1000, it) }))
        assertEquals(ViewerReadingMode.PAGE, detectReadingMode(List(3) { ImageDimensions(1600, 900) }))
        assertEquals(ViewerReadingMode.PAGE, detectReadingMode(List(2) { ImageDimensions(1000, 9000) }))
        assertEquals(ViewerReadingMode.PAGE, detectReadingMode(emptyList()))
        assertFalse(webtoonSampleIndices(20).contains(0))
        assertEquals(listOf(1, 2), webtoonSampleIndices(3))
        assertTrue(webtoonSampleIndices(0).isEmpty())
    }
    @Test fun tilesCoverEntireImageWithoutGapsAndBoundDisplayHeight() {
        for (width in listOf(360, 1080, 1600)) {
            val tiles = webtoonTiles(4, ImageDimensions(1200, 60_000), width)
            assertEquals(0, tiles.first().top)
            assertEquals(60_000, tiles.last().bottom)
            tiles.zipWithNext().forEach { (before, after) -> assertEquals(before.bottom, after.top) }
            assertTrue(tiles.all { it.sourceHeight * width.toDouble() / it.sourceWidth <= 1024 })
        }
    }
    @Test fun savedSourceOffsetSurvivesViewportWidthChanges() {
        val tiles = webtoonTiles(49, ImageDimensions(1200, 60_000), 600)
        val position = webtoonPosition(tiles[10], 100, 600)
        val rotated = webtoonTiles(49, ImageDimensions(1200, 60_000), 1200)
        val target = rotated.last { it.top <= position.offset }
        val offset = position.offset - target.top
        assertEquals(position, webtoonPosition(target, offset, 1200))
        assertEquals(49, position.page)
    }
}
