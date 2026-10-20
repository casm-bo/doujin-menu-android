package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import com.doujinmenu.android.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.HistorySource
import com.doujinmenu.android.model.RECENT_HISTORY_LIMIT
import com.doujinmenu.android.model.RecentHistoryEntry
import com.doujinmenu.android.model.findBook
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecentHistoryScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onOpen: (RecentHistoryEntry, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onRecordingChange: (Boolean) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val entries = remember(state.recentHistory, query, sourceName) {
        state.recentHistory.filter { entry ->
            (sourceName == null || entry.source.name == sourceName) &&
                (query.isBlank() || entry.title.contains(query, true) || entry.artist.contains(query, true) || entry.galleryId?.toString() == query)
        }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(strings[R.string.ui_search_recent_history]) },
                singleLine = true, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { options = true }) { Text("⋯") }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text(if (state.historyRecording) strings[R.string.ui_pause_history] else strings[R.string.ui_resume_history]) }, leadingIcon = { ActionIcon(if (state.historyRecording) ActionSymbol.PAUSE else ActionSymbol.PLAY) }, onClick = { onRecordingChange(!state.historyRecording); options = false })
                    DropdownMenuItem(text = { Text(strings[R.string.ui_clear_all_history]) }, leadingIcon = { ActionIcon(ActionSymbol.DELETE) }, enabled = state.recentHistory.isNotEmpty(), onClick = { options = false; clearDialog = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(strings[R.string.ui_keeps_latest_entries, (RECENT_HISTORY_LIMIT)]) }, onClick = {}, enabled = false)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sourceName == null, onClick = { sourceName = null }, leadingIcon = { ActionIcon(ActionSymbol.GRID) }, label = { Text(strings[R.string.ui_all]) })
            HistorySource.entries.forEach { source ->
                FilterChip(selected = sourceName == source.name, onClick = { sourceName = source.name }, leadingIcon = { ActionIcon(when (source) { HistorySource.SEARCH -> ActionSymbol.SEARCH; HistorySource.DEVICE -> ActionSymbol.DEVICE; HistorySource.PC -> ActionSymbol.PC }) }, label = { Text(strings[source.labelResource]) })
            }
        }
        if (!state.historyRecording) Text(strings[R.string.ui_history_paused_reading_progress_is_still_saved], Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (state.recentHistory.isEmpty()) strings[R.string.ui_galleries_you_open_will_appear_here] else strings[R.string.ui_no_matching_history])
        } else LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.key }) { entry ->
                HistoryCard(entry, state, onOpen = { onOpen(entry, it) }, onRemove = { onRemove(entry.key) })
            }
        }
    }
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false }, title = { Text(strings[R.string.ui_clear_entire_recent_history]) },
        text = { Text(strings[R.string.ui_deletes_browsing_history_book_files_and_reading_progress_are_k]) },
        confirmButton = { TextButton(onClick = { onClear(); clearDialog = false }) { Text(strings[R.string.ui_delete]) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(strings[R.string.ui_cancel]) } },
    )
}

@Composable
private fun HistoryCard(entry: RecentHistoryEntry, state: MainUiState, onOpen: (Boolean) -> Unit, onRemove: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val book = entry.findBook(state.libraryBooks)
    val image = if (book != null) libraryImageRequest(context, book.coverUri, book.cloudToken)
        else entry.thumbnail?.let { uri -> if (entry.source == HistorySource.SEARCH && entry.galleryId != null) hitomiImageRequest(context, uri, entry.galleryId) else null }
    val time = remember(entry.viewedAt) {
        DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.viewedAt))
    }
    var options by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { onOpen(false) }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(72.dp).height(100.dp), contentAlignment = Alignment.Center) {
                Text(strings[entry.source.labelResource], style = MaterialTheme.typography.labelSmall)
                AsyncImage(model = image, contentDescription = strings[R.string.ui_cover, (entry.title)], modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preferredLocalizedTitle(entry.title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (entry.artist.isNotBlank()) Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("${strings[entry.source.labelResource]} · ${strings[entry.action.labelResource]} · $time", style = MaterialTheme.typography.labelSmall)
                entry.page?.let { page ->
                    TextButton(onClick = { onOpen(true) }, contentPadding = PaddingValues(0.dp), enabled = !state.isBusy) {
                        Text(strings[R.string.ui_page_continue, (page + 1), (entry.pageCount.takeIf { it > 0 }?.let { " / $it" }.orEmpty())])
                    }
                }
            }
            Box {
                IconButton(onClick = { options = true }) { Text("⋯") }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text(strings[R.string.ui_clear_history]) }, leadingIcon = { ActionIcon(ActionSymbol.DELETE) }, onClick = { options = false; onRemove() })
                }
            }
        }
    }
}
