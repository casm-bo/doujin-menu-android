package com.doujinmenu.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.doujinmenu.android.R
import kotlinx.coroutines.channels.Channel
import kotlin.math.floor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class ScrollbarMetrics(
    val positionFraction: Float,
    val sizeFraction: Float,
)

internal fun scrollbarMetrics(
    firstVisibleItemIndex: Int,
    visibleItemCount: Int,
    totalItemCount: Int,
    fractionalItems: Float = 0f,
): ScrollbarMetrics? {
    if (totalItemCount <= 0 || visibleItemCount >= totalItemCount) return null
    val size = (visibleItemCount.toFloat() / totalItemCount).coerceIn(0.08f, 1f)
    val position = (firstVisibleItemIndex + fractionalItems)
        .div((totalItemCount - visibleItemCount).coerceAtLeast(1))
        .coerceIn(0f, 1f)
    return ScrollbarMetrics(position, size)
}

internal fun scrollbarTargetIndex(
    pointerY: Float,
    viewportHeight: Float,
    metrics: ScrollbarMetrics,
    visibleItemCount: Int,
    totalItemCount: Int,
): Int {
    val thumbHeight = viewportHeight * metrics.sizeFraction
    val availableHeight = (viewportHeight - thumbHeight).coerceAtLeast(1f)
    val position = ((pointerY - thumbHeight / 2f) / availableHeight).coerceIn(0f, 1f)
    return (position * (totalItemCount - visibleItemCount).coerceAtLeast(0)).roundToInt()
}

internal data class ScrollbarTarget(val index: Int, val offset: Int)

internal fun scrollbarTarget(pointerY: Float, height: Float, metrics: ScrollbarMetrics, visible: Int, total: Int, extent: Float, columns: Int): ScrollbarTarget {
    if (total <= 0 || height <= 0) return ScrollbarTarget(0, 0)
    val thumb = height * metrics.sizeFraction
    val fraction = ((pointerY - thumb / 2) / (height - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
    if (fraction >= 1f) return ScrollbarTarget(total - 1, 0)
    val row = fraction * (total - visible).coerceAtLeast(0) / columns.coerceAtLeast(1)
    return ScrollbarTarget((floor(row).toInt() * columns.coerceAtLeast(1)).coerceIn(0, total - 1), ((row - floor(row)) * extent).roundToInt().coerceAtLeast(0))
}

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyListState) {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo.size
    val extent = (info.visibleItemsInfo.map { it.size }.average().takeIf { it.isFinite() }?.toFloat() ?: 1f) + info.mainAxisItemSpacing
    DynamicVerticalScrollbar(
        scrollbarMetrics(state.firstVisibleItemIndex, visible, info.totalItemsCount, state.firstVisibleItemScrollOffset / extent.coerceAtLeast(1f)),
        state.isScrollInProgress, visible, info.totalItemsCount, extent, 1,
    ) { target -> state.scrollToItem(target.index, target.offset) }
}

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyGridState) {
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    val columns = info.visibleItemsInfo.count { it.row == first?.row }.coerceAtLeast(1)
    val extent = (first?.size?.height ?: 1) + info.mainAxisItemSpacing
    DynamicVerticalScrollbar(
        scrollbarMetrics(state.firstVisibleItemIndex, info.visibleItemsInfo.size, info.totalItemsCount, state.firstVisibleItemScrollOffset.toFloat() / extent.coerceAtLeast(1) * columns),
        state.isScrollInProgress, info.visibleItemsInfo.size, info.totalItemsCount, extent.toFloat(), columns,
    ) { target -> state.scrollToItem(target.index, target.offset) }
}

@Composable
private fun BoxScope.DynamicVerticalScrollbar(
    metrics: ScrollbarMetrics?, scrolling: Boolean, visibleItemCount: Int, totalItemCount: Int,
    extent: Float, columns: Int, scrollToItem: suspend (ScrollbarTarget) -> Unit,
) {
    if (metrics == null) return
    val strings = androidx.compose.ui.platform.LocalResources.current
    val hapticFeedback = LocalHapticFeedback.current
    val currentMetrics by rememberUpdatedState(metrics)
    val currentVisible by rememberUpdatedState(visibleItemCount)
    val currentTotal by rememberUpdatedState(totalItemCount)
    val currentExtent by rememberUpdatedState(extent)
    val currentColumns by rememberUpdatedState(columns)
    val currentScroll by rememberUpdatedState(scrollToItem)
    val targets = remember { Channel<ScrollbarTarget>(Channel.CONFLATED) }
    LaunchedEffect(targets) {
        for (target in targets) currentScroll(target)
    }
    DisposableEffect(targets) { onDispose { targets.close() } }
    var dragging by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (scrolling || dragging) .95f else .35f, label = "scrollbarAlpha")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
        .testTag("dynamic-scrollbar").semantics { contentDescription = strings[R.string.ui_scrollbar] }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val height = size.height.toFloat()
                val gestureMetrics = currentMetrics
                val gestureVisible = currentVisible
                val gestureTotal = currentTotal
                val gestureExtent = currentExtent
                val gestureColumns = currentColumns
                val thumb = height * gestureMetrics.sizeFraction
                val top = (height - thumb) * gestureMetrics.positionFraction
                val onThumb = down.position.y in (top - 12.dp.toPx())..(top + thumb + 12.dp.toPx())
                // Track swipes belong to the content until a deliberate long press claims them.
                val start = if (onThumb) down else awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                start.consume()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                val grabOffset = if (onThumb) down.position.y - (top + thumb / 2) else 0f
                var previous: ScrollbarTarget? = null
                fun move(y: Float) {
                    val target = scrollbarTarget(y - grabOffset, height, gestureMetrics, gestureVisible, gestureTotal, gestureExtent, gestureColumns)
                    if (target != previous) { targets.trySend(target); previous = target }
                }
                dragging = true
                try {
                    if (!onThumb) move(start.position.y)
                    drag(start.id) { change -> change.consume(); move(change.position.y) }
                } finally { dragging = false }
            }
        }) {
        val thumbHeight = size.height * metrics.sizeFraction
        val thumbTop = (size.height - thumbHeight) * metrics.positionFraction
        val width = 4.dp.toPx()
        drawRoundRect(color, Offset((size.width - width) / 2, thumbTop), Size(width, thumbHeight), CornerRadius(width / 2), alpha = alpha)
    }
}
