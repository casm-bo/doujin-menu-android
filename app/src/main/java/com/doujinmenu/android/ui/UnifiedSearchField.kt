package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchQueryEntry
import com.doujinmenu.android.network.FilterSuggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun UnifiedSearchField(
    query: String, onQueryChange: (String) -> Unit, placeholder: String, onFocus: () -> Unit, onSubmit: () -> Unit,
    history: List<SearchQueryEntry> = emptyList(), suggestions: List<FilterSuggestion> = emptyList(), loading: Boolean = false,
    onRecent: (SearchQueryEntry) -> Unit = {}, onClearHistory: () -> Unit = {}, onSuggestion: (FilterSuggestion) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var editor by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(query.ifBlank { placeholder }, Modifier.weight(1f).clickable { onFocus(); editor = true }.padding(horizontal = 12.dp, vertical = 16.dp)
            .semantics { contentDescription = placeholder }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onSubmit, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_run_search] }) { ActionIcon(ActionSymbol.SEARCH) }
    }
    if (editor) SearchQueryEditor(query, onQueryChange, placeholder, history, suggestions, loading,
        onRecent = { editor = false; onRecent(it) }, onClearHistory = onClearHistory, onSuggestion = onSuggestion,
        onSubmit = { editor = false; onSubmit() }, onDismiss = { editor = false })
}

@Composable
internal fun SearchQueryEditor(query: String, onQueryChange: (String) -> Unit, placeholder: String,
    history: List<SearchQueryEntry>, suggestions: List<FilterSuggestion>, loading: Boolean,
    onRecent: (SearchQueryEntry) -> Unit, onClearHistory: () -> Unit, onSuggestion: (FilterSuggestion) -> Unit,
    onSubmit: () -> Unit, onDismiss: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(query) { if (text.text != query) text = TextFieldValue(query, TextRange(query.length)) }
    LaunchedEffect(Unit) { visible = true; delay(50); requester.requestFocus() }
    fun close() {
        if (closing) return
        closing = true; focus.clearFocus(); visible = false
        scope.launch { delay(180); onDismiss() }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val editorFocus = LocalFocusManager.current
        BackHandler { close() }
        AnimatedVisibility(visible, enter = slideInVertically(tween(220)) { -it / 5 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(180)) { -it / 5 } + fadeOut(tween(160))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .onGloballyPositioned { contentOrigin = it.positionInRoot() }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!inputBounds.contains(down.position + contentOrigin)) editorFocus.clearFocus()
                    }
                }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { close() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_return] }) { ActionIcon(ActionSymbol.BACK) }
                        TextField(text, onValueChange = { value -> text = value; if (query != value.text) onQueryChange(value.text) },
                            modifier = Modifier.weight(1f).focusRequester(requester).onGloballyPositioned { inputBounds = it.boundsInRoot() }
                                .onFocusChanged { if (!it.isFocused) text = text.copy(selection = TextRange.Zero) },
                            singleLine = true, placeholder = { Text(placeholder) }, textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { editorFocus.clearFocus(); onSubmit() }))
                        IconButton(onClick = { editorFocus.clearFocus(); onSubmit() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_run_search] }) { ActionIcon(ActionSymbol.SEARCH) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings[R.string.ui_title_title_artist_artist_tag_tag_series_series_exclude_phrase], Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onQueryChange(""); text = TextFieldValue(); requester.requestFocus() }) { Text(strings[R.string.ui_clear_search]) }
                    }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                        if (query.isBlank() || (suggestions.isEmpty() && !loading)) {
                            RecentSearchHistory(history, onRecent, onClearHistory)
                        } else {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                suggestions.forEach { suggestion -> TextButton(onClick = {
                                    onSuggestion(suggestion)
                                    requester.requestFocus()
                                }, modifier = Modifier.fillMaxWidth()) { Text(suggestion.token, Modifier.fillMaxWidth()) } }
                                if (loading) Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
