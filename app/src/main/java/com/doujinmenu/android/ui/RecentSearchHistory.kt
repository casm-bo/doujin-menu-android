package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchQueryEntry

@Composable
internal fun RecentSearchHistory(entries: List<SearchQueryEntry>, onSelect: (SearchQueryEntry) -> Unit, onClear: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(strings[R.string.ui_recent_searches], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text(strings[R.string.ui_clear_history]) }
    }
    if (entries.isEmpty()) Text(strings[R.string.ui_no_recent_searches], style = MaterialTheme.typography.bodyMedium)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        items(entries) { entry ->
            TextButton(onClick = { onSelect(entry) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(entry.query.ifBlank { strings[R.string.ui_all_galleries] }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.languages.isNotEmpty()) Text(entry.languages.sorted().joinToString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
