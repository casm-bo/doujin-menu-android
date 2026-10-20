package com.doujinmenu.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

internal data class CardTag(val info: TagDisplayInfo, val onClick: (() -> Unit)? = null)
internal data class TagPlacement(val index: Int, val x: Int, val row: Int)
internal data class PackedTags(val tags: List<TagPlacement>, val overflow: TagPlacement?)

internal fun packTags(widths: List<Int>, width: Int, rows: Int, gap: Int, overflowWidth: Int, hasMore: Boolean = false): PackedTags {
    if (width <= 0 || rows <= 0) return PackedTags(emptyList(), null)
    val placed = mutableListOf<TagPlacement>()
    var x = 0
    var row = 0
    for ((index, rawWidth) in widths.withIndex()) {
        val itemWidth = rawWidth.coerceIn(0, width)
        if (x > 0 && x + itemWidth > width) { row++; x = 0 }
        if (row >= rows) break
        placed += TagPlacement(index, x, row)
        x += itemWidth + gap
    }
    if (placed.size == widths.size && !hasMore) return PackedTags(placed, null)
    if (placed.size == widths.size && x + overflowWidth > width && row + 1 < rows) row++
    row = minOf(row, rows - 1)
    while (placed.isNotEmpty()) {
        val last = placed.last()
        val nextX = last.x + widths[last.index].coerceIn(0, width) + gap
        if (last.row < row) return PackedTags(placed, TagPlacement(-1, 0, row))
        if (nextX + overflowWidth <= width) return PackedTags(placed, TagPlacement(-1, nextX, row))
        placed.removeAt(placed.lastIndex)
    }
    return PackedTags(emptyList(), TagPlacement(-1, 0, row))
}

@Composable
private fun WorkTags(tags: List<CardTag>, rows: Int, modifier: Modifier) {
    Layout(modifier = modifier, content = {
        tags.take(60).forEach { tag ->
            TagPill(tag.info, large = true, modifier = if (tag.onClick != null) Modifier.clickable(onClick = tag.onClick) else Modifier)
        }
        Text("…", style = MaterialTheme.typography.bodyMedium)
    }) { measurables, constraints ->
        val children = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val rowHeight = ((constraints.maxHeight - (rows - 1) * 3.dp.roundToPx()) / rows).coerceAtLeast(1)
        val overflow = children.last()
        val packed = packTags(children.dropLast(1).map { it.width }, constraints.maxWidth, rows, 4.dp.roundToPx(), overflow.width, tags.size > 60)
        layout(constraints.maxWidth, constraints.maxHeight) {
            packed.tags.forEach { item -> children[item.index].placeRelative(item.x, item.row * (rowHeight + 3.dp.roundToPx())) }
            packed.overflow?.let { overflow.placeRelative(it.x, it.row * (rowHeight + 3.dp.roundToPx())) }
        }
    }
}

@Composable
internal fun WorkCard(
    title: String,
    artist: String,
    metadata: String,
    tags: List<CardTag>,
    footer: String,
    grid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    border: BorderStroke? = null,
    cover: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val rows = if (grid) 3 else 2
    val tagHeight = with(density) { typography.bodyMedium.lineHeight.toDp() } + 8.dp
    val bodyHeight = with(density) {
        typography.titleSmall.lineHeight.toDp() * 2 + typography.bodySmall.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp() * 2
    } + tagHeight * rows + 3.dp * (rows - 1) + 36.dp
    @Composable fun Body(modifier: Modifier) {
        Column(modifier.height(bodyHeight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, style = typography.bodySmall, color = MaterialTheme.colorScheme.primary, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metadata, style = typography.labelSmall, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WorkTags(tags, rows, Modifier.fillMaxWidth().height(tagHeight * rows + 3.dp * (rows - 1)))
            Text(footer, style = typography.labelSmall, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (grid) Column {
            Box(Modifier.fillMaxWidth().aspectRatio(.72f).background(MaterialTheme.colorScheme.surfaceVariant), content = cover)
            Body(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth().height(bodyHeight)) {
            Box(Modifier.width(104.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant), content = cover)
            Body(Modifier.weight(1f))
        }
    }
}
