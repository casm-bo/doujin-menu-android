package com.doujinzip.android.model

import kotlin.math.ceil

enum class ViewerReadingMode { AUTO, PAGE, WEBTOON }
data class ReaderImageSource(val uri: String, val headers: Map<String, String> = emptyMap())
data class ImageDimensions(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
}

fun webtoonSampleIndices(pageCount: Int): List<Int> =
    (listOf(1, 2, 3, pageCount / 2, pageCount - 1)).filter { it in 1 until pageCount }.distinct()

fun detectReadingMode(bodySamples: List<ImageDimensions>): ViewerReadingMode {
    if (bodySamples.size < 3) return ViewerReadingMode.PAGE
    val ratios = bodySamples.map { it.height.toDouble() / it.width }.sorted()
    return if (ratios[ratios.size / 2] >= 2.5 && ratios.count { it >= 2.2 } * 4 >= ratios.size * 3)
        ViewerReadingMode.WEBTOON else ViewerReadingMode.PAGE
}

data class WebtoonTile(val page: Int, val top: Int, val bottom: Int, val sourceWidth: Int) {
    val sourceHeight get() = bottom - top
}

fun webtoonTiles(page: Int, size: ImageDimensions, displayWidth: Int, tileHeight: Int = 1024): List<WebtoonTile> {
    require(displayWidth > 0 && tileHeight > 0)
    val sourceRows = (size.width.toLong() * tileHeight / displayWidth).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    val count = ceil(size.height.toDouble() / sourceRows).toInt()
    require(count <= 4096) { "Image aspect ratio is too large" }
    return List(count) { index ->
        val top = index.toLong() * sourceRows
        WebtoonTile(page, top.toInt(), minOf(top + sourceRows, size.height.toLong()).toInt(), size.width)
    }
}

fun webtoonPosition(tile: WebtoonTile, scrollOffset: Int, displayWidth: Int): ReaderPosition = ReaderPosition(
    tile.page, (tile.top + scrollOffset.toLong() * tile.sourceWidth / displayWidth.coerceAtLeast(1)).coerceIn(tile.top.toLong(), tile.bottom.toLong()).toInt(),
)
