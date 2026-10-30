package com.doujinzip.android.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doujinzip.android.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun BrowserWorkspace.adjacentSwipeTab(step: Int): BrowserTab? {
    if (step != -1 && step != 1) return null
    val ordered = overviewItems.flatMap { item -> when (item) {
        is BrowserOverviewItem.Tab -> listOf(item.tab)
        is BrowserOverviewItem.Group -> item.tabs
    } }
    val index = ordered.indexOfFirst { it.id == activeTabId }
    return if (index < 0) null else ordered.getOrNull(index + step)
}

internal fun tabSwipeStep(distance: Float, velocity: Float, width: Float, minVelocity: Float): Int {
    if (width <= 0 || !distance.isFinite() || !velocity.isFinite()) return 0
    val farEnough = abs(distance) >= width * .22f
    val fling = abs(distance) >= width * .03f && abs(velocity) >= minVelocity && distance * velocity > 0
    return if (!farEnough && !fling) 0 else if (distance < 0) 1 else -1
}

internal class TabSwipeState(private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f); private set
    var width by mutableFloatStateOf(0f)
    var animating by mutableStateOf(false); private set
    private var job: Job? = null
    fun drag(distance: Float, hasNeighbor: Boolean) {
        if (!animating) offset = if (hasNeighbor) distance.coerceIn(-width, width) else (distance * .18f).coerceIn(-width * .08f, width * .08f)
    }
    fun cancel() { job?.cancel(); offset = 0f; animating = false }
    fun settle(target: String?, current: () -> Boolean, select: (String) -> Unit) {
        if (animating) return
        animating = true
        job = scope.launch {
            try {
                if (target == null) {
                    animate(offset, 0f, animationSpec = spring(stiffness = 500f, dampingRatio = .85f)) { value, _ -> offset = value }
                } else {
                    if (!current()) return@launch
                    val direction = if (offset < 0) -1 else 1
                    select(target)
                    offset = -direction * width
                    // Let navigation and the target screen compose before sliding it into view.
                    withFrameNanos { }
                    animate(offset, 0f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
                }
            } finally { offset = 0f; animating = false }
        }
    }
}

internal val LocalTabSwipe = staticCompositionLocalOf<TabSwipeState?> { null }

@Composable
internal fun TabSwipeHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { TabSwipeState(scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) state.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); state.cancel() }
    }
    Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background).onSizeChanged { state.width = it.width.toFloat() }) {
        CompositionLocalProvider(LocalTabSwipe provides state) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = state.offset }.pointerInput(state.animating) {
                if (state.animating) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }) { content() }
        }
    }
}
