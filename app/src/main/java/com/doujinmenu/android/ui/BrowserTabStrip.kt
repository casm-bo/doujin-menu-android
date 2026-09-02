package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.model.BrowserWorkspace
import com.doujinmenu.android.model.label

@Composable
fun BrowserTabStrip(
    workspace: BrowserWorkspace,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onNew: () -> Unit,
) {
    val activeIndex = workspace.tabs.indexOfFirst { it.id == workspace.activeTabId }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(workspace.tabs, key = { it.id }) { tab ->
                val selected = tab.id == workspace.activeTabId
                Surface(
                    onClick = { onSelect(tab.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.currentPage.label,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                                .widthIn(max = 150.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { onClose(tab.id) }) { Text("×") }
                    }
                }
            }
            item(key = "new-tab") {
                TextButton(onClick = onNew) { Text("+") }
            }
            item(key = "move-left") {
                TextButton(
                    onClick = { onMove(workspace.activeTabId, -1) },
                    enabled = activeIndex > 0,
                ) { Text("‹") }
            }
            item(key = "move-right") {
                TextButton(
                    onClick = { onMove(workspace.activeTabId, 1) },
                    enabled = activeIndex in 0 until workspace.tabs.lastIndex,
                ) { Text("›") }
            }
        }
    }
}
