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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchQueryEntry
import com.doujinmenu.android.network.FilterSuggestion
import kotlinx.coroutines.delay

@Composable
internal fun UnifiedSearchField(
    query: String, onQueryChange: (String) -> Unit, placeholder: String, onFocus: () -> Unit, onSubmit: () -> Unit,
    history: List<SearchQueryEntry> = emptyList(), suggestions: List<FilterSuggestion> = emptyList(), loading: Boolean = false,
    onRecent: (SearchQueryEntry) -> Unit = {}, onClearHistory: () -> Unit = {}, onSuggestion: (FilterSuggestion) -> Unit = {},
) {
    val state = LocalInlineSearch.current ?: remember { InlineSearchState() }
    val strings = androidx.compose.ui.platform.LocalResources.current
    val focus = LocalFocusManager.current
    val changeQuery by rememberUpdatedState(onQueryChange)
    val boundQuery by rememberUpdatedState(query)
    val submit by rememberUpdatedState(onSubmit)
    var input by remember { mutableStateOf<EditText?>(null) }
    fun close() { state.expanded = false }
    fun focusInput() {
        input?.let { view ->
            view.requestFocus()
            view.post { (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0) }
        }
    }
    LaunchedEffect(state.expanded, input) {
        if (state.expanded && input != null) { delay(50); focusInput() }
        else if (!state.expanded) {
            input?.let { view ->
                (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
            focus.clearFocus()
        }
    }
    BackHandler(state.expanded) { close() }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220)).clipToBounds()
        .testTag(if (state.expanded) "search-editor" else "search-collapsed")) {
        Row(Modifier.fillMaxWidth().height(56.dp).onGloballyPositioned { state.inputBounds = it.boundsInRoot() },
            verticalAlignment = Alignment.CenterVertically) {
            if (state.expanded) Box(Modifier.width(48.dp)) {
                IconButton(onClick = { close() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_return] }) { ActionIcon(ActionSymbol.BACK) }
            }
            val colors = MaterialTheme.colorScheme
            val textSize = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
            Surface(Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(percent = 50), color = colors.surfaceContainerHigh) {
                if (state.expanded) {
                    // Keep platform IME/range selection and continuous insertion-handle edge scrolling.
                    AndroidView(factory = { context -> SearchQueryInput(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setSingleLine(true)
                        setHorizontallyScrolling(true)
                        background = null
                        val padding = (16 * resources.displayMetrics.density).toInt()
                        setPadding(padding, padding / 3, padding, padding * 3 / 4)
                        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        setOnFocusChangeListener { _, focused -> if (focused) setSelection(text?.length ?: 0) }
                        doAfterTextChanged { if (it.toString() != boundQuery) changeQuery(it.toString()) }
                        setOnEditorActionListener { _, action, event ->
                            if (action == EditorInfo.IME_ACTION_SEARCH || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                                close(); clearFocus(); submit(); true
                            } else false
                        }
                        input = this
                    } }, modifier = Modifier.fillMaxWidth().height(56.dp), update = { view ->
                        view.hint = placeholder
                        view.setTextColor(colors.onSurface.toArgb())
                        view.caretColor = colors.primary.toArgb()
                        view.setHintTextColor(colors.onSurfaceVariant.toArgb())
                        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                        if (view.text.toString() != query) { view.setText(query); view.setSelection(query.length) }
                    }, onRelease = { if (input === it) input = null })
                } else {
                    Text(query.ifBlank { placeholder }, Modifier.fillMaxWidth().clickable { onFocus(); state.expanded = true }
                        .padding(horizontal = 12.dp, vertical = 16.dp).semantics { contentDescription = placeholder },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = { close(); onSubmit() }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_run_search] }) { ActionIcon(ActionSymbol.SEARCH) }
        }
        if (state.expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = state.panelMaxHeight)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings[R.string.ui_title_title_artist_artist_tag_tag_series_series_exclude_phrase], Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { input?.setText(""); onQueryChange(""); focusInput() }) { Text(strings[R.string.ui_clear_search]) }
            }
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("search-options")) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (query.isBlank() || (suggestions.isEmpty() && !loading)) {
                    RecentSearchHistory(history, { close(); onRecent(it) }, onClearHistory)
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
