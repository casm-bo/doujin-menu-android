package com.doujinzip.android.ui

import com.doujinzip.android.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinzip.android.model.BrowserPage
import com.doujinzip.android.model.BrowserWorkspace
import kotlinx.coroutines.launch

internal fun BrowserPage.localizedToolbarTitle(strings: android.content.res.Resources): String = when (this) {
    is BrowserPage.Search -> (if (currentPage > 0 || submittedQueries.isNotEmpty()) submittedQuery.ifBlank { submittedQueries.joinToString(" · ") } else query)
        .ifBlank { preferredLanguages.joinToString(" · ").ifBlank { strings[R.string.ui_new_search] } }
    is BrowserPage.LibraryHome -> strings[R.string.ui_gallery]
    is BrowserPage.LibrarySeries -> name
    is BrowserPage.OnlineGallery -> title.ifBlank { strings[R.string.ui_gallery_d16837, galleryId] }
    is BrowserPage.OnlineReader -> title.ifBlank { strings[R.string.ui_gallery_d16837, galleryId] }
    is BrowserPage.LibraryBook -> title.ifBlank { strings[R.string.ui_gallery] }
    is BrowserPage.LibraryReader -> title.ifBlank { strings[R.string.ui_gallery] }
}

@Composable
fun BrowserTabStrip(
    workspace: BrowserWorkspace,
    onNew: () -> Unit,
    onShowOverview: () -> Unit,
    previews: BrowserTabPreviewCache,
    onBack: (() -> Unit)? = null,
    titleOverride: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
    onSelect: (String) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val search = LocalInlineSearch.current
    val scope = rememberCoroutineScope()
    val swipe = LocalTabSwipe.current
    val latestWorkspace by rememberUpdatedState(workspace)
    val latestSelect by rememberUpdatedState(onSelect)
    val minVelocity = with(LocalDensity.current) { 900.dp.toPx() }
    val swipeEnabled = swipe != null && workspace.tabs.size > 1 && search?.expanded != true
    val gesture = if (!swipeEnabled) Modifier else Modifier.pointerInput(swipe, swipeEnabled) {
        val state = requireNotNull(swipe)
        val tracker = VelocityTracker()
        var distance = 0f
        var origin = latestWorkspace.activeTab
        var accepted = false
        var previousId: String? = null
        var nextId: String? = null
        fun finish(cancelled: Boolean) {
            if (!accepted) return
            accepted = false
            val step = if (cancelled) 0 else tabSwipeStep(distance, tracker.calculateVelocity().x, state.width, minVelocity)
            val target = when (step) { -1 -> previousId; 1 -> nextId; else -> null }
            state.settle(target,
                current = { latestWorkspace.activeTabId == origin.id && latestWorkspace.activeTab.currentPage.key == origin.currentPage.key && latestWorkspace.tabs.any { it.id == target } },
                select = { latestSelect(it) })
        }
        try { detectHorizontalDragGestures(
            onDragStart = {
                accepted = !state.animating
                if (accepted) {
                    val snapshot = latestWorkspace
                    origin = snapshot.activeTab
                    previousId = snapshot.adjacentSwipeTab(-1)?.id
                    nextId = snapshot.adjacentSwipeTab(1)?.id
                    // Capture the departing screen during the drag, never gate selection on disk I/O.
                    scope.launch { previews.captureNow() }
                    distance = 0f; tracker.resetTracking(); tracker.addPosition(android.os.SystemClock.uptimeMillis(), Offset.Zero)
                }
            },
            onHorizontalDrag = { change, amount ->
                if (accepted) {
                    change.consume(); distance += amount
                    tracker.addPosition(change.uptimeMillis, Offset(distance, 0f))
                    state.drag(distance, if (distance < 0) nextId != null else previousId != null)
                }
            }, onDragEnd = { finish(false) }, onDragCancel = { finish(true) },
        ) } finally { finish(true) }
    }
    fun captureThen(action: () -> Unit) { scope.launch { previews.captureNow(); action() } }

    Surface(modifier = if (titleContent == null) Modifier.statusBarsPadding() else Modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().testTag("tab-toolbar").then(gesture).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = { captureThen(onBack) },
                modifier = Modifier.semantics { contentDescription = strings[R.string.ui_back] }) { Text("‹") }
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                if (titleContent != null) titleContent() else Text(titleOverride ?: workspace.activeTab.currentPage.localizedToolbarTitle(strings),
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            if (search?.expanded != true) {
            Row {
            IconButton(onClick = { captureThen(onNew) }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_new_tab] }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { captureThen(onShowOverview) },
                modifier = Modifier.semantics { contentDescription = strings[R.string.ui_show_all_tabs, (workspace.tabs.size)] }) {
                Surface(shape = RoundedCornerShape(7.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)) {
                    Text(workspace.tabs.size.toString(), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            extraActions()
            }
            }
        }
    }
}
