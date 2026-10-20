package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*
import org.json.JSONObject

@Composable
internal fun SeriesDetailContent(name: String, books: List<LibraryBook>, state: MainUiState, onList: () -> Unit, onEdit: () -> Unit,
    onOpenBook: (String) -> Unit, onReadBook: (String, Int) -> Unit, onRename: () -> Unit, onFavorite: () -> Unit,
    onRead: (Set<String>) -> Unit, onUnread: (Set<String>) -> Unit, onHidden: (Set<String>, Boolean) -> Unit, onDelete: () -> Unit,
    onSearchFacet: (String) -> Unit, onSearchLanguage: (String) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val sorted = remember(books, state.customSeriesByBookId) { books.sortedWith(compareBy<LibraryBook> { state.customSeriesByBookId[it.id]?.order ?: Int.MAX_VALUE }.thenBy { it.id }) }
    val metadata = remember(sorted) { unionSeriesMetadata(sorted) }
    val ids = sorted.mapTo(linkedSetOf()) { it.id }
    val favorite = name in state.libraryFavoriteSeriesNames
    val read = ids.isNotEmpty() && ids.all { it in state.libraryReadIds }
    val hidden = ids.isNotEmpty() && ids.all { it in state.libraryHiddenIds }
    var options by remember { mutableStateOf(false) }
    var delete by rememberSaveable(name) { mutableStateOf(false) }
    val list = rememberSaveable(name, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            DetailHeader(name, metadata, sorted.firstOrNull()?.let { libraryImageRequest(context, it.coverUri, it.cloudToken) },
                sorted.sumOf { it.pages.size.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), information = strings[R.string.ui_series_episode_count, sorted.size],
                onSearchFacet = onSearchFacet, onSearchLanguage = onSearchLanguage, webtoon = sorted.any { it.webtoon == true }, options = {
                    Box {
                        IconButton(onClick = { options = true }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_detail_options] }) { Text("⋯") }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            listOf(DetailMenuAction(strings[R.string.ui_series_options_edit], ActionSymbol.EDIT, onEdit),
                                DetailMenuAction(strings[R.string.ui_rename_series], ActionSymbol.EDIT, onRename),
                                DetailMenuAction(strings[if (favorite) R.string.ui_remove_favorite else R.string.ui_add_favorite], ActionSymbol.STAR, onFavorite),
                                DetailMenuAction(strings[if (read) R.string.ui_mark_unread else R.string.ui_mark_read], ActionSymbol.READ) { if (read) onUnread(ids) else onRead(ids) },
                                DetailMenuAction(strings[if (hidden) R.string.ui_unhide else R.string.ui_hide], ActionSymbol.HIDE) { onHidden(ids, !hidden) },
                                DetailMenuAction(strings[R.string.ui_delete_series], ActionSymbol.DELETE) { delete = true }).forEach { action ->
                                DropdownMenuItem(text = { Text(action.label) }, leadingIcon = { ActionIcon(action.symbol) }, onClick = { options = false; action.onClick() })
                            }
                        }
                    }
                })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { sorted.firstOrNull()?.let { onReadBook(it.id, 0) } }, enabled = sorted.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(strings[R.string.ui_read_from_beginning]) }
                OutlinedButton(onClick = onList, modifier = Modifier.weight(1f)) { ActionIcon(ActionSymbol.LIST); Text(strings[R.string.ui_list], Modifier.padding(start = 6.dp)) }
            }
        }
        items(sorted.chunked(3), key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { book -> Column(Modifier.weight(1f).clickable { onOpenBook(book.id) }) {
                    DetailImage(libraryImageRequest(context, book.coverUri, book.cloudToken), book.title, Modifier.fillMaxWidth().height(150.dp))
                    Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(strings[R.string.ui_delete_series]) },
        text = { Text(strings[R.string.ui_delete_selected_series_galleries_will_be_kept, 1]) },
        confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text(strings[R.string.ui_delete]) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(strings[R.string.ui_cancel]) } })
}

@Composable
internal fun SeriesOptionsEditor(name: String, books: List<LibraryBook>, assignments: Map<String, CustomSeriesAssignment>, contentPadding: PaddingValues,
    onSave: (Map<String, CustomSeriesAssignment>, List<String>) -> Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val baselineJson = rememberSaveable(name) { JSONObject().apply { assignments.forEach { (id, entry) ->
        put(id, JSONObject().put("name", entry.name).put("order", entry.order).put("modifiedAt", entry.modifiedAt))
    } }.toString() }
    val baseline = remember(baselineJson) { JSONObject(baselineJson).let { json -> json.keys().asSequence().associateWith { id ->
        val entry = json.getJSONObject(id); CustomSeriesAssignment(entry.getString("name"), entry.getInt("order"), entry.getLong("modifiedAt"))
    } } }
    var order by rememberSaveable(name) { mutableStateOf(baseline.entries.filter { it.value.name == name }.sortedWith(compareBy({ it.value.order }, { it.key })).map { it.key }) }
    var selected by rememberSaveable(name) { mutableStateOf(emptySet<String>()) }
    var adding by rememberSaveable(name) { mutableStateOf(false) }
    var chosen by rememberSaveable(name) { mutableStateOf(emptySet<String>()) }
    var failed by remember { mutableStateOf(false) }
    val byId = remember(books) { books.associateBy { it.id } }
    BackHandler { if (adding) { adding = false; chosen = emptySet() } else onCancel() }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text(strings[R.string.ui_cancel]) }
                    Button(onClick = { failed = !onSave(baseline, order); if (!failed) onDone() }, enabled = !adding && order.all { it in byId }) { Text(strings[R.string.ui_save]) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (adding) { order = order + chosen.filter { it !in order }; chosen = emptySet(); adding = false } else adding = true
                    }, modifier = Modifier.weight(1f)) { ActionIcon(ActionSymbol.SERIES); Text(strings[if (adding) R.string.ui_confirm else R.string.ui_add_episodes], Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = { order = order.filterNot { it in selected }; selected = emptySet() }, enabled = !adding && selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        ActionIcon(ActionSymbol.DELETE); Text(strings[R.string.ui_exclude_episodes], Modifier.padding(start = 6.dp))
                    }
                }
                if (adding) TextButton(onClick = { adding = false; chosen = emptySet() }) { Text(strings[R.string.ui_return]) }
            }
        }
        if (failed) Text(strings[R.string.ui_series_edit_conflict], Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (adding) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books.filter { it.id !in order }, key = { it.id }) { book ->
                OutlinedCard(onClick = { chosen = if (book.id in chosen) chosen - book.id else chosen + book.id }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = book.id in chosen, onCheckedChange = null)
                        Column(Modifier.weight(1f)) { Text(book.title); assignments[book.id]?.name?.let { Text(it, style = MaterialTheme.typography.labelSmall) } }
                    }
                }
            }
        } else if (order.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(strings[R.string.ui_empty_series_keeps_files], Modifier.padding(16.dp)) }
        else LibrarySeriesOrderEditor(order.mapNotNull(byId::get), order.mapIndexed { index, id -> id to CustomSeriesAssignment(name, index) }.toMap(),
            onReorder = { order = it }, selectedIds = selected, onSelect = { selected = if (it in selected) selected - it else selected + it })
    }
}
