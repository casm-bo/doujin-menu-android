package com.doujinmenu.android.ui

import android.content.Context
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
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
    val scope = rememberCoroutineScope()
    val changeQuery by rememberUpdatedState(onQueryChange)
    val submit by rememberUpdatedState(onSubmit)
    var input by remember { mutableStateOf<EditText?>(null) }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    fun focusInput() {
        input?.let { view ->
            view.requestFocus()
            view.post { (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0) }
        }
    }
    LaunchedEffect(input) { if (input != null) { delay(50); focusInput() } }
    fun close() {
        if (closing) return
        closing = true; input?.clearFocus(); focus.clearFocus(); visible = false
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
                        if (!inputBounds.contains(down.position + contentOrigin)) { input?.clearFocus(); editorFocus.clearFocus() }
                    }
                }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { close() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_return] }) { ActionIcon(ActionSymbol.BACK) }
                        val colors = MaterialTheme.colorScheme
                        val textSize = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
                        // Keep platform IME/range selection; the insertion handle adds continuous edge scrolling.
                        Surface(Modifier.weight(1f).heightIn(min = 56.dp).onGloballyPositioned { inputBounds = it.boundsInRoot() },
                            shape = RoundedCornerShape(percent = 50), color = colors.surfaceContainerHigh) {
                            AndroidView(factory = { context -> SearchQueryInput(context).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                setSingleLine(true)
                                setHorizontallyScrolling(true)
                                background = null
                                val padding = (16 * resources.displayMetrics.density).toInt()
                                setPadding(padding, padding / 3, padding, padding * 3 / 4)
                                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                setOnFocusChangeListener { _, focused -> if (focused) setSelection(text?.length ?: 0) }
                                doAfterTextChanged { changeQuery(it.toString()) }
                                setOnEditorActionListener { _, action, event ->
                                    if (action == EditorInfo.IME_ACTION_SEARCH || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                                        clearFocus(); submit(); true
                                    } else false
                                }
                                input = this
                            } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), update = { view ->
                                view.hint = placeholder
                                view.setTextColor(colors.onSurface.toArgb())
                                view.caretColor = colors.primary.toArgb()
                                view.setHintTextColor(colors.onSurfaceVariant.toArgb())
                                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                                if (view.text.toString() != query) { view.setText(query); view.setSelection(query.length) }
                            })
                        }
                        IconButton(onClick = { input?.clearFocus(); editorFocus.clearFocus(); onSubmit() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_run_search] }) { ActionIcon(ActionSymbol.SEARCH) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings[R.string.ui_title_title_artist_artist_tag_tag_series_series_exclude_phrase], Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { input?.setText(""); onQueryChange(""); focusInput() }) { Text(strings[R.string.ui_clear_search]) }
                    }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                        if (query.isBlank() || (suggestions.isEmpty() && !loading)) {
                            RecentSearchHistory(history, onRecent, onClearHistory)
                        } else {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                suggestions.forEach { suggestion -> TextButton(onClick = {
                                    onSuggestion(suggestion)
                                    focusInput()
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
