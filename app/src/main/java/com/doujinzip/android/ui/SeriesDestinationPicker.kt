package com.doujinzip.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R

@Composable
internal fun SeriesDestinationPicker(
    state: MainUiState, sourceCount: Int, list: Boolean, columns: Int, padding: PaddingValues,
    onAppend: (String) -> Boolean, onDone: () -> Unit, onCancel: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val names = remember(state.customSeriesByBookId) { state.customSeriesByBookId.values.map { it.name }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val members = remember(state.libraryBooks, state.customSeriesByBookId) {
        state.libraryBooks.filter { it.id in state.customSeriesByBookId }.groupBy { state.customSeriesByBookId.getValue(it.id).name }
            .mapValues { (_, books) -> books.sortedBy { state.customSeriesByBookId.getValue(it.id).order } }
    }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings[R.string.ui_add_to_series], style = MaterialTheme.typography.titleMedium)
                    Text(strings[R.string.ui_selected, sourceCount], style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onCancel) { Text(strings[R.string.ui_cancel]) }
            }
        }
        if (names.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings[R.string.ui_no_existing_series]) }
        else LazyVerticalGrid(GridCells.Fixed(if (list) 1 else columns), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(names, key = { it }) { name ->
                SeriesCard(name, members[name].orEmpty(), list, 1f, favorite = name in state.libraryFavoriteSeriesNames,
                    read = false, selectionMode = true, selected = target == name,
                    onClick = { target = name }, onLongPress = {}, onToggleFavorite = {},
                    presentation = state.presentations[com.doujinzip.android.model.seriesPresentationKey(name)]?.value,
                    coverPresentation = state.presentations[com.doujinzip.android.model.seriesCoverPresentationKey(name)]?.value)
            }
        }
    }
    target?.let { name -> AlertDialog(onDismissRequest = { target = null },
        title = { Text(strings[R.string.ui_add_to_series]) }, text = { Text(strings[R.string.ui_confirm_add_to_series, sourceCount, name]) },
        confirmButton = { TextButton(enabled = name in names, onClick = { if (onAppend(name)) onDone() }) { Text(strings[R.string.ui_confirm]) } },
        dismissButton = { TextButton(onClick = { target = null }) { Text(strings[R.string.ui_cancel]) } }) }
}
