package com.doujinmenu.android.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Stable
internal class InlineSearchState {
    var expanded by mutableStateOf(false)
    var panelMaxHeight by mutableStateOf(320.dp)
    var inputBounds by mutableStateOf(Rect.Zero)
    var editorBounds by mutableStateOf(Rect.Zero)
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
    val view = LocalView.current
    BoxWithConstraints(modifier.fillMaxSize().imePadding().onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(state, view) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val position = down.position + origin
                val outsideInput = !state.inputBounds.contains(position)
                val outsideEditor = !state.editorBounds.contains(position)
                var keyboardHidden = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (state.expanded && outsideInput) {
                        // Observe without consuming: buttons still run, and normal list gestures remain normal.
                        val dragged = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (outsideEditor) {
                            if (dragged || (!change.pressed && !change.isConsumed)) state.expanded = false
                        } else if (dragged && !keyboardHidden) {
                            // Scrolling search options dismisses only the IME; the draft/caret stay active.
                            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .hideSoftInputFromWindow(view.windowToken, 0)
                            keyboardHidden = true
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        // Give history more room while retaining browsing space, including with the IME open.
        SideEffect { state.panelMaxHeight = ((maxHeight - 56.dp) * .65f).coerceIn(0.dp, 320.dp) }
        CompositionLocalProvider(LocalInlineSearch provides state, content = content)
    }
}
