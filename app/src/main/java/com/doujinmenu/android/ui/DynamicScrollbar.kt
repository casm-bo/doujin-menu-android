package com.doujinmenu.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

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

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyListState) {
    DynamicVerticalScrollbar(
        metrics = scrollbarMetrics(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            visibleItemCount = state.layoutInfo.visibleItemsInfo.size,
            totalItemCount = state.layoutInfo.totalItemsCount,
        ),
        scrolling = state.isScrollInProgress,
    )
}

@Composable
internal fun BoxScope.DynamicVerticalScrollbar(state: LazyGridState) {
    DynamicVerticalScrollbar(
        metrics = scrollbarMetrics(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            visibleItemCount = state.layoutInfo.visibleItemsInfo.size,
            totalItemCount = state.layoutInfo.totalItemsCount,
        ),
        scrolling = state.isScrollInProgress,
    )
}

@Composable
private fun BoxScope.DynamicVerticalScrollbar(
    metrics: ScrollbarMetrics?,
    scrolling: Boolean,
) {
    if (metrics == null) return
    val alpha by animateFloatAsState(if (scrolling) 0.9f else 0.35f, label = "scrollbarAlpha")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(8.dp)
            .padding(horizontal = 2.dp, vertical = 4.dp),
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
