package com.doujinmenu.android.ui

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
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("최근 기록 검색") },
                singleLine = true, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { options = true }) { Text("⋯") }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text(if (state.historyRecording) "기록 일시 중지" else "기록 다시 시작") }, onClick = { onRecordingChange(!state.historyRecording); options = false })
                    DropdownMenuItem(text = { Text("전체 기록 삭제") }, enabled = state.recentHistory.isNotEmpty(), onClick = { options = false; clearDialog = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("최근 ${RECENT_HISTORY_LIMIT}개 저장") }, onClick = {}, enabled = false)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sourceName == null, onClick = { sourceName = null }, label = { Text("전체") })
            HistorySource.entries.forEach { source ->
                FilterChip(selected = sourceName == source.name, onClick = { sourceName = source.name }, label = { Text(source.label) })
            }
        }
        if (!state.historyRecording) Text("기록 일시 중지 중 · 읽기 진도는 계속 저장됩니다.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (state.recentHistory.isEmpty()) "열어본 갤러리가 여기에 표시됩니다." else "조건에 맞는 기록이 없습니다.")
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.key }) { entry ->
                HistoryCard(entry, state, onOpen = { onOpen(entry, it) }, onRemove = { onRemove(entry.key) })
            }
        }
    }
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false }, title = { Text("최근 기록 전체 삭제") },
        text = { Text("열어본 기록을 모두 삭제합니다. 책 파일과 읽기 진도는 유지됩니다.") },
        confirmButton = { TextButton(onClick = { onClear(); clearDialog = false }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("취소") } },
    )
}

@Composable
private fun HistoryCard(entry: RecentHistoryEntry, state: MainUiState, onOpen: (Boolean) -> Unit, onRemove: () -> Unit) {
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
                Text(entry.source.label, style = MaterialTheme.typography.labelSmall)
                AsyncImage(model = image, contentDescription = "${entry.title} 표지", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preferredLocalizedTitle(entry.title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (entry.artist.isNotBlank()) Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("${entry.source.label} · ${entry.action.label} · $time", style = MaterialTheme.typography.labelSmall)
                entry.page?.let { page ->
                    TextButton(onClick = { onOpen(true) }, contentPadding = PaddingValues(0.dp), enabled = !state.isBusy) {
                        Text("${page + 1}${entry.pageCount.takeIf { it > 0 }?.let { " / $it" }.orEmpty()}페이지 · 이어 읽기")
                    }
                }
            }
            Box {
                IconButton(onClick = { options = true }) { Text("⋯") }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("기록 삭제") }, onClick = { options = false; onRemove() })
                }
            }
        }
    }
}
