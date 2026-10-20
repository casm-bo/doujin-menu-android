package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import com.doujinmenu.android.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    onLoadMetadata: suspend (Long) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val preferences = remember(context) { com.doujinmenu.android.security.AppearancePreferenceStore(context) }
    var grid by rememberSaveable { mutableStateOf(preferences.loadHistoryGrid()) }
    var viewMenu by remember { mutableStateOf(false) }
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
                IconButton(onClick = { viewMenu = true }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_view_58d697] }) { ActionIcon(ActionSymbol.GRID) }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    listOf(false to R.string.ui_list_view, true to R.string.ui_grid_view).forEach { (value, label) ->
                        ChoiceMenuItem(strings[label], grid == value, onClick = { grid = value; preferences.saveHistoryGrid(value); viewMenu = false })
                    }
                }
            }
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
        if (entries.isEmpty()) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text(if (state.recentHistory.isEmpty()) strings[R.string.ui_galleries_you_open_will_appear_here] else strings[R.string.ui_no_matching_history])
        } else LazyVerticalGrid(columns = GridCells.Fixed(if (grid) 2 else 1), modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.key }) { entry ->
                HistoryCard(entry, state, grid, onLoadMetadata, onOpen = { onOpen(entry, it) }, onRemove = { onRemove(entry.key) })
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

internal fun historyTags(entry: RecentHistoryEntry, state: MainUiState): List<CardTag> =
    entry.findBook(state.libraryBooks)?.metadata?.tags?.map { CardTag(tagDisplayInfo(it)) }
        ?: state.galleryCache[entry.galleryId]?.tags?.map { CardTag(tagDisplayInfo(it.name, it.type)) }.orEmpty()

@Composable
private fun HistoryCard(entry: RecentHistoryEntry, state: MainUiState, grid: Boolean, onLoadMetadata: suspend (Long) -> Unit, onOpen: (Boolean) -> Unit, onRemove: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val book = remember(entry, state.libraryBooks) { entry.findBook(state.libraryBooks) }
    LaunchedEffect(entry.key) {
        if (book == null && entry.galleryId != null && entry.galleryId !in state.galleryCache) {
            kotlinx.coroutines.delay(200)
            onLoadMetadata(entry.galleryId)
        }
    }
    val image = remember(book?.coverUri, book?.cloudToken, entry.thumbnail) {
        if (book != null) libraryImageRequest(context, book.coverUri, book.cloudToken)
        else entry.thumbnail?.let { uri -> entry.galleryId?.let { hitomiImageRequest(context, uri, it, crossfade = false, preview = true) } }
    }
    val time = remember(entry.viewedAt) {
        DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.viewedAt))
    }
    var options by remember { mutableStateOf(false) }
    WorkCard(title = preferredLocalizedTitle(entry.title), artist = entry.artist,
        metadata = "${strings[entry.source.labelResource]} · ${strings[entry.action.labelResource]}",
        tags = historyTags(entry, state), footer = time + (entry.page?.let { " · ${it + 1}/${entry.pageCount}" } ?: ""),
        grid = grid, onClick = { onOpen(false) },
        border = galleryCardBorder(favorite = book?.id in state.libraryFavoriteIds)) {
        AsyncImage(model = image, contentDescription = strings[R.string.ui_cover, entry.title], modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.align(Alignment.TopEnd)) {
            FilledIconButton(onClick = { options = true }) { Text("⋯") }
            DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                DropdownMenuItem(text = { Text(strings[R.string.ui_clear_history]) }, leadingIcon = { ActionIcon(ActionSymbol.DELETE) }, onClick = { options = false; onRemove() })
            }
        }
        if (entry.page != null) Button(onClick = { onOpen(true) }, enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)) {
            Text(strings[R.string.ui_continue_reading], maxLines = 1)
        }
    }
}
