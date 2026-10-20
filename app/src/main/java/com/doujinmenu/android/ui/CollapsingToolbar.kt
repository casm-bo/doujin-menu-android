package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsingToolbar(behavior: TopAppBarScrollBehavior, content: @Composable () -> Unit) {
    Box(Modifier.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        behavior.state.heightOffsetLimit = -placeable.height.toFloat()
        val offset = behavior.state.heightOffset.roundToInt()
        layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.placeRelative(0, offset) }
    }) { Column { content() } }
}

internal fun galleryToolsTwoRows(width: Int, modesWidth: Int, toolsWidth: Int, gap: Int): Boolean =
    modesWidth + toolsWidth + gap > width

@Composable
internal fun GalleryToolsRow(modes: @Composable () -> Unit, tools: @Composable () -> Unit) {
    Layout(content = {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { modes() }
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) { tools() }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val left = measurables[0].measure(loose)
        val right = measurables[1].measure(loose)
        val twoRows = galleryToolsTwoRows(constraints.maxWidth, left.width, right.width, 8.dp.roundToPx())
        val height = if (twoRows) left.height + right.height else maxOf(left.height, right.height)
        layout(constraints.maxWidth, height) {
            left.placeRelative(0, if (twoRows) 0 else (height - left.height) / 2)
            right.placeRelative(constraints.maxWidth - right.width, if (twoRows) left.height else (height - right.height) / 2)
        }
    }
}
