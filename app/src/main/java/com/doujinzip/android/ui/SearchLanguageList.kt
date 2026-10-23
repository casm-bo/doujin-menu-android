package com.doujinzip.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R
import com.doujinzip.android.network.AvailableLanguage

@Composable
internal fun SearchLanguageList(state: MainUiState, selected: Set<String>, onToggle: (String) -> Unit,
    onAny: () -> Unit, onRetry: () -> Unit, onRemove: ((String) -> Unit)? = null) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Text(strings[if(selected.isEmpty()) R.string.ui_any_search_all_languages else R.string.ui_search_each_selected_language_and_merge_results],
        style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(strings[R.string.ui_available_selected_multiple_selection,state.availableLanguages.size,selected.size],style=MaterialTheme.typography.labelMedium)
    if(state.isLoadingLanguages) LoadingRow(strings[R.string.ui_loading_all_languages])
    state.languageLoadError?.let { error ->
        Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        TextButton(onRetry) { Text(strings[R.string.ui_try_again]) }
    }
    val languages=remember(state.availableLanguages,state.customLanguages,selected) {
        val known=state.availableLanguages.mapTo(hashSetOf()) { it.code }
        state.availableLanguages + (state.customLanguages + selected - known).sorted().map {
            AvailableLanguage(it,it.replace('_',' ').replaceFirstChar { char -> char.uppercase() })
        }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=220.dp)) {
        item(key="any") {
            Row(Modifier.fillMaxWidth().toggleable(selected.isEmpty(),role=Role.Checkbox,onValueChange={onAny()}),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(selected.isEmpty(),null,Modifier.padding(12.dp));Text(strings[R.string.ui_any_all_languages],style=MaterialTheme.typography.bodyMedium)
            }
        }
        items(languages,key={it.code}) { language ->
            Row(Modifier.fillMaxWidth().toggleable(language.code in selected,role=Role.Checkbox,onValueChange={onToggle(language.code)}),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(language.code in selected,null,Modifier.padding(12.dp))
                Column(Modifier.weight(1f).padding(vertical=4.dp)) {
                    Text(language.label,style=MaterialTheme.typography.bodyMedium)
                    Text(language.code,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(onRemove!=null && language.code in state.customLanguages) TextButton({onRemove(language.code)}) { Text(strings[R.string.ui_delete]) }
            }
        }
    }
}
