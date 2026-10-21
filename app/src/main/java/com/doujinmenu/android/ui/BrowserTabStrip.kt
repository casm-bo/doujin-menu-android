package com.doujinmenu.android.ui

import com.doujinmenu.android.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
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
import com.doujinmenu.android.model.BrowserPage
import com.doujinmenu.android.model.BrowserWorkspace
import com.doujinmenu.android.model.label
import kotlinx.coroutines.launch

internal val BrowserPage.toolbarTitle: String
    get() = if (this is BrowserPage.Search) {
        (if (currentPage > 0 || submittedQueries.isNotEmpty()) {
            submittedQuery.ifBlank { submittedQueries.joinToString(" · ") }
        } else query).ifBlank {
            preferredLanguages.joinToString(" · ").ifBlank { "새 검색" }
        }
    } else label

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
    titleContent: (@Composable () -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val search = LocalInlineSearch.current
    val expansion by animateFloatAsState(if (search?.expanded == true) 1f else 0f, tween(220), label = "search toolbar width")
    val scope = rememberCoroutineScope()
    fun captureThen(action: () -> Unit) { scope.launch { previews.captureNow(); action() } }

    Surface(modifier = if (titleContent == null) Modifier.statusBarsPadding() else Modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp * (1f - expansion), vertical = 4.dp), verticalAlignment = Alignment.Top) {
            if (onBack != null) IconButton(onClick = { captureThen(onBack) },
                modifier = Modifier.semantics { contentDescription = strings[R.string.ui_back] }) { Text("‹") }
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp * (1f - expansion)),
                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                if (titleContent != null) titleContent() else Text(workspace.activeTab.currentPage.localizedToolbarTitle(strings),
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.clipToBounds().layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                layout((child.width * (1f - expansion)).roundToInt(), child.height) { child.placeRelative(0, 0) }
            }) {
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
