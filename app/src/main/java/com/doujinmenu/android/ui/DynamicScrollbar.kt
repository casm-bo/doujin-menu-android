package com.doujinmenu.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class ScrollbarMetrics(
    val positionFraction: Float,
    val sizeFraction: Float,
)

internal fun scrollbarMetrics(
    firstVisibleItemIndex: Int,
    visibleItemCount: Int,
    totalItemCount: Int,
): ScrollbarMetrics? {
    if (totalItemCount <= 0 || visibleItemCount >= totalItemCount) return null
    val size = (visibleItemCount.toFloat() / totalItemCount).coerceIn(0.08f, 1f)
    val position = firstVisibleItemIndex.toFloat()
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

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyListState) {
    val visibleItemCount = state.layoutInfo.visibleItemsInfo.size
    val totalItemCount = state.layoutInfo.totalItemsCount
    DynamicVerticalScrollbar(
        metrics = scrollbarMetrics(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            visibleItemCount = visibleItemCount,
            totalItemCount = totalItemCount,
        ),
        scrolling = state.isScrollInProgress,
        visibleItemCount = visibleItemCount,
        totalItemCount = totalItemCount,
        scrollToItem = { index -> state.scrollToItem(index) },
    )
}

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyGridState) {
    val visibleItemCount = state.layoutInfo.visibleItemsInfo.size
    val totalItemCount = state.layoutInfo.totalItemsCount
    DynamicVerticalScrollbar(
        metrics = scrollbarMetrics(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            visibleItemCount = visibleItemCount,
            totalItemCount = totalItemCount,
        ),
        scrolling = state.isScrollInProgress,
        visibleItemCount = visibleItemCount,
        totalItemCount = totalItemCount,
        scrollToItem = { index -> state.scrollToItem(index) },
    )
}

@Composable
private fun BoxScope.DynamicVerticalScrollbar(
    metrics: ScrollbarMetrics?,
    scrolling: Boolean,
    visibleItemCount: Int,
    totalItemCount: Int,
    scrollToItem: suspend (Int) -> Unit,
) {
    if (metrics == null) return
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val currentMetrics by rememberUpdatedState(metrics)
    val currentVisibleItemCount by rememberUpdatedState(visibleItemCount)
    val currentTotalItemCount by rememberUpdatedState(totalItemCount)
    val currentScrollToItem by rememberUpdatedState(scrollToItem)
    var dragging by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val alpha by animateFloatAsState(
        if (scrolling || dragging) 0.95f else 0.35f,
        label = "scrollbarAlpha",
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val target = scrollbarTargetIndex(
                            pointerY = change.position.y,
                            viewportHeight = size.height.toFloat(),
                            metrics = currentMetrics,
                            visibleItemCount = currentVisibleItemCount,
                            totalItemCount = currentTotalItemCount,
                        )
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch { currentScrollToItem(target) }
                    },
                )
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        val thumbHeight = size.height * metrics.sizeFraction
        val thumbTop = (size.height - thumbHeight) * metrics.positionFraction
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f),
            alpha = alpha,
        )
    }
}
