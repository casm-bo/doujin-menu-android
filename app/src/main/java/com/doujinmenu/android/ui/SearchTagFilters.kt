package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchTagFilter
import com.doujinmenu.android.model.parseBlockedSearchTags

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchTagFilters(presets:Set<SearchTagFilter>,custom:Set<String>,onPreset:(SearchTagFilter)->Unit,onCustom:(Set<String>)->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Text(strings[R.string.search_tag_filter_hint],style=MaterialTheme.typography.bodySmall)
    SearchTagFilter.entries.forEach { filter ->
        Row(Modifier.fillMaxWidth().toggleable(filter in presets,role=Role.Switch,onValueChange={onPreset(filter)}),verticalAlignment=Alignment.CenterVertically) {
            Text(strings[when(filter) { SearchTagFilter.BL -> R.string.search_block_bl;SearchTagFilter.GORE -> R.string.search_block_gore;SearchTagFilter.LOLI -> R.string.search_block_loli }],Modifier.weight(1f))
            Switch(filter in presets,null)
        }
    }
    OutlinedTextField(query,{query=it;invalid=false},label={Text(strings[R.string.search_custom_blocked_tags])},
        placeholder={Text("tag:example, female:example")},singleLine=true,isError=invalid,modifier=Modifier.fillMaxWidth().testTag("custom-blocked-tags"))
    if(invalid) Text(strings[R.string.search_custom_tag_error],color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    TextButton({
        val tags=runCatching { parseBlockedSearchTags(query).also { parseBlockedSearchTags((custom+it).joinToString(" ")) } }.getOrNull()
        if(tags==null) invalid=true else { onCustom(custom+tags);query="";invalid=false }
    },enabled=query.isNotBlank(),modifier=Modifier.testTag("add-blocked-tags")) { Text(strings[R.string.search_add_blocked_tags]) }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        custom.sorted().forEach { tag -> InputChip(false,{onCustom(custom-tag)},label={Text(tag)},trailingIcon={Text("×")}) }
    }
}
