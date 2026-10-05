package com.doujinmenu.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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

@Composable
fun BrowserTabStrip(
    workspace: BrowserWorkspace,
    onNew: () -> Unit,
    onShowOverview: () -> Unit,
    previews: BrowserTabPreviewCache,
    onBack: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    fun captureThen(action: () -> Unit) { scope.launch { previews.captureNow(); action() } }

    Surface(modifier = Modifier.statusBarsPadding(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = { captureThen(onBack) },
                modifier = Modifier.semantics { contentDescription = "뒤로" }) { Text("‹") }
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(workspace.activeTab.currentPage.toolbarTitle,
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { captureThen(onNew) }, modifier = Modifier.semantics { contentDescription = "새 탭" }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { captureThen(onShowOverview) },
                modifier = Modifier.semantics { contentDescription = "모든 탭 보기, ${workspace.tabs.size}개" }) {
                Surface(shape = RoundedCornerShape(7.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)) {
                    Text(workspace.tabs.size.toString(), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            extraActions()
        }
    }
}
