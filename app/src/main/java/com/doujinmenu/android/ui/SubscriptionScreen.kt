package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchSubscription
import com.doujinmenu.android.security.SubscriptionOptions
import com.doujinmenu.android.security.SubscriptionPreferences
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SubscriptionScreen(state: MainUiState, onClose: () -> Unit, onTab: (Int) -> Unit,
    onRefresh: () -> Unit, onRead: (Long?) -> Unit, onEdit: (SearchSubscription?) -> Unit,
    onSave: (SearchSubscription) -> Unit, onResolve: (String,Boolean) -> Unit, onSearch: (SearchSubscription) -> Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    var deleting by remember { mutableStateOf<SearchSubscription?>(null) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClose) { ActionIcon(ActionSymbol.BACK) }
            Text(strings[R.string.sub_title],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onRefresh,enabled=!state.subscriptionsBusy) { ActionIcon(ActionSymbol.REFRESH) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(state.subscriptionTab==0,{onTab(0)},label={Text(strings[R.string.sub_notifications]+" (${state.subscriptionNotices.count { !it.read }})")})
            FilterChip(state.subscriptionTab==1,{onTab(1)},label={Text(strings[R.string.sub_title])})
        }
        if(state.subscriptionsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.subscriptionError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        if(state.subscriptionTab==0) {
            TextButton({onRead(null)},enabled=state.subscriptionNotices.any { !it.read }) { Text(strings[R.string.sub_all_read]) }
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                if(state.subscriptionNotices.isEmpty()) item { Text(strings[R.string.sub_empty]) }
                items(state.subscriptionNotices,key={it.galleryId}) { notice ->
                    WorkCard(title=notice.title,artist=notice.artist,metadata=notice.subscriptions.joinToString(", "),tags=emptyList(),
                        footer=strings[if(notice.read) R.string.sub_read else R.string.sub_unread],grid=false,onClick={onRead(notice.galleryId)},
                        border=if(!notice.read) androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null,cover={ AsyncImage(notice.thumbnail,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) })
                }
            }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            item { Text(strings[R.string.sub_hint],style=MaterialTheme.typography.bodySmall) }
            item { Button({onEdit(SearchSubscription(name="",query=""))}) { Text(strings[R.string.sub_add]) } }
            if(state.subscriptions.none { !it.value.deleted }) item { Text(strings[R.string.sub_no_searches]) }
            items(state.subscriptions.filter { !it.value.deleted || it.conflict != null },key={it.value.id}) { record ->
                val value=record.value
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(value.name,style=MaterialTheme.typography.titleMedium)
                    if(value.deleted) Text(strings[R.string.sub_deleted],color=MaterialTheme.colorScheme.error)
                    Text(value.query,style=MaterialTheme.typography.bodySmall)
                    if(value.languages.isNotEmpty()) Text(value.languages.sorted().joinToString(", "),style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(strings[R.string.sub_monitor],Modifier.weight(1f))
                        Switch(value.enabled,{onSave(value.copy(enabled=it))},enabled=record.conflict==null,modifier=Modifier.testTag("subscription-monitor"))
                    }
                    Text(if(record.checkedAt==0L) strings[R.string.sub_never] else strings[R.string.sub_checked,DateFormat.getDateTimeInstance().format(Date(record.checkedAt))],style=MaterialTheme.typography.bodySmall)
                    record.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    if(record.mutationId != null) Text(strings[R.string.sub_pending],style=MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton({onSearch(value)}) { Text(strings[R.string.ui_search]) }
                        TextButton({onEdit(value)},enabled=record.conflict==null) { Text(strings[R.string.sub_edit]) }
                        TextButton({deleting=value},enabled=record.conflict==null) { Text(strings[R.string.ui_delete]) }
                    }
                    record.conflict?.let { remote ->
                        Text(strings[R.string.sub_conflict],color=MaterialTheme.colorScheme.error)
                        Text("PC: ${remote.name}\n${remote.query}\n${remote.languages.joinToString(", ")}")
                        Text(strings[if(remote.deleted) R.string.sub_deleted else if(remote.enabled) R.string.sub_monitor_on else R.string.sub_monitor_off])
                        Row { TextButton({onResolve(value.id,true)}) { Text(strings[R.string.sub_keep_phone]) }; TextButton({onResolve(value.id,false)}) { Text(strings[R.string.sub_use_pc]) } }
                    }
                } }
            }
        }
    }
    state.subscriptionEditor?.let { value -> SubscriptionEditor(value,state.subscriptionError,{onEdit(null)},onSave) }
    deleting?.let { value -> AlertDialog(onDismissRequest={deleting=null},title={Text(strings[R.string.ui_delete])},text={Text(value.name)},
        confirmButton={TextButton({onSave(value.copy(deleted=true));deleting=null}) { Text(strings[R.string.ui_delete]) }},
        dismissButton={TextButton({deleting=null}) { Text(strings[R.string.ui_cancel]) }}) }
}

@Composable
private fun SubscriptionEditor(value:SearchSubscription,externalError:String?,onDismiss:()->Unit,onSave:(SearchSubscription)->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var query by rememberSaveable(value.id) { mutableStateOf(value.query) }
    var languages by rememberSaveable(value.id) { mutableStateOf(value.languages.sorted().joinToString(", ")) }
    var enabled by rememberSaveable(value.id) { mutableStateOf(value.enabled) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(strings[R.string.sub_title])},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text(strings[R.string.sub_name])},singleLine=true)
            CompositionLocalProvider(LocalInlineSearch provides remember { InlineSearchState() }) {
                UnifiedSearchField(query,{query=it},strings[R.string.ui_search],{}, {})
            }
            OutlinedTextField(languages,{languages=it},label={Text(strings[R.string.sub_languages])})
            Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_monitor],Modifier.weight(1f)); Switch(enabled,{enabled=it},modifier=Modifier.testTag("subscription-editor-monitor")) }
            Text(strings[R.string.sub_hint],style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            externalError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton({
        val edited=value.copy(name=name.trim().ifBlank { query.trim() },query=query.trim(),languages=languages.split(',').map { it.trim() }.filter(String::isNotBlank).toSet(),enabled=enabled)
        try { edited.validate();onSave(edited) } catch(e:IllegalArgumentException) { error=e.message }
    }) { Text(strings[R.string.ui_save]) }},dismissButton={TextButton(onDismiss) { Text(strings[R.string.ui_cancel]) }})
}

@Composable
internal fun SubscriptionSettings(options:SubscriptionOptions,onChange:(SubscriptionOptions)->Unit,onManage:()->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(strings[R.string.sub_interval],style=MaterialTheme.typography.titleMedium)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SubscriptionPreferences.intervals.forEach { minutes -> FilterChip(options.intervalMinutes==minutes,{onChange(options.copy(intervalMinutes=minutes))},label={Text(strings[R.string.sub_minutes,minutes])}) }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_system],Modifier.weight(1f)); Switch(options.notifications,{onChange(options.copy(notifications=it))}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_hide_locked],Modifier.weight(1f)); Switch(options.hideWhenLocked,{onChange(options.copy(hideWhenLocked=it))},modifier=Modifier.testTag("subscription-hide-locked")) }
        Text(strings[if(options.hideWhenLocked) R.string.sub_hide_hint else R.string.sub_expose_hint],style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onManage) { Text(strings[R.string.sub_title]) }
    }
}
