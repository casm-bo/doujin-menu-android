package com.doujinmenu.android.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

@Stable
internal class InlineSearchState {
    var expanded by mutableStateOf(false)
    var height by mutableStateOf(360.dp)
    var inputBounds by mutableStateOf(Rect.Zero)
}

internal val LocalInlineSearch = staticCompositionLocalOf<InlineSearchState?> { null }

/** The existing toolbar grows in the same layout/window; no dialog or navigation entry. */
@Composable
internal fun InlineSearchLayout(
    state: InlineSearchState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxSize().imePadding().onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val outsideInput = !state.inputBounds.contains(down.position + origin)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (state.expanded && outsideInput) {
                        // Observe without consuming: buttons still run, and normal list gestures remain normal.
                        val dragged = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (dragged || (!change.pressed && !change.isConsumed)) state.expanded = false
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        SideEffect { state.height = (maxHeight - 8.dp).coerceAtLeast(56.dp) }
        CompositionLocalProvider(LocalInlineSearch provides state, content = content)
    }
}
