package com.doujinmenu.android.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import com.doujinmenu.android.model.ReaderPosition
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun previewGridState(
    pageKey: String,
    initial: ReaderPosition,
    pageCount: Int,
    onSave: (ReaderPosition) -> Unit,
): LazyGridState {
    val state = remember(pageKey) { LazyGridState() }
    val saved = remember(pageKey) { initial }
    var restored by remember(pageKey) { mutableStateOf(false) }
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(pageKey, pageCount) {
        if (!restored && (saved.page == 0 || pageCount >= saved.page)) {
            // Item zero is the full-span header; thumbnail indices remain stable as its height changes.
            state.scrollToItem(saved.page.coerceAtMost(pageCount), saved.offset)
            restored = true
        }
    }
    LaunchedEffect(pageKey, restored) {
        if (restored) snapshotFlow { Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .filter { !it.first }.distinctUntilChanged().collect { (_, index, offset) -> save(ReaderPosition(index, offset)) }
    }
    DisposableEffect(pageKey, restored) {
        onDispose { if (restored) save(ReaderPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) }
    }
    return state
}
