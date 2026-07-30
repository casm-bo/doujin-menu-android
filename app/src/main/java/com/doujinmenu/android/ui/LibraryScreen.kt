package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.LibraryVisibilityFilter
import com.doujinmenu.android.security.LibraryPreferenceStore

private enum class LibraryViewMode { GRID, LIST }

private fun thumbnailBadgeScale(columns: Int): Float = when (columns) {
    2 -> 1f
    3 -> 0.72f
    else -> 0.56f
}

private const val LIST_THUMBNAIL_BADGE_SCALE = 0.62f

private sealed interface LibraryMainEntry {
    val title: String
    val modifiedAt: Long
    data class Book(val book: LibraryBook) : LibraryMainEntry {
        override val title: String = book.title
        override val modifiedAt: Long = book.modifiedAt
    }
    data class Series(val name: String, val books: List<LibraryBook>) : LibraryMainEntry {
        override val title: String = name
        override val modifiedAt: Long = books.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
    }
}

private fun LibraryBook.primaryArtist(): String? =
    metadata.artists.firstOrNull { it.isNotBlank() }?.trim()

private fun compareArtists(left: String?, right: String?, descending: Boolean): Int {
    if (left == null) return if (right == null) 0 else 1
    if (right == null) return -1
    val result = String.CASE_INSENSITIVE_ORDER.compare(left, right)
    return if (descending) -result else result
}

private fun libraryBookArtistComparator(descending: Boolean): Comparator<LibraryBook> =
    Comparator { left, right ->
        compareArtists(left.primaryArtist(), right.primaryArtist(), descending)
            .takeIf { it != 0 }
            ?: String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
    }

private fun LibraryMainEntry.primaryArtist(): String? = when (this) {
    is LibraryMainEntry.Book -> book.primaryArtist()
    is LibraryMainEntry.Series -> books.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
}

private fun libraryMainEntryArtistComparator(descending: Boolean): Comparator<LibraryMainEntry> =
    Comparator { left, right ->
        compareArtists(left.primaryArtist(), right.primaryArtist(), descending)
            .takeIf { it != 0 }
            ?: String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
    }

private fun seriesGroupArtistComparator(
    descending: Boolean,
): Comparator<Pair<String, List<LibraryBook>>> = Comparator { left, right ->
    val leftArtist = left.second.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
    val rightArtist = right.second.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
    compareArtists(leftArtist, rightArtist, descending)
        .takeIf { it != 0 }
        ?: String.CASE_INSENSITIVE_ORDER.compare(left.first, right.first)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
    onAddFavorites: (Set<String>) -> Unit,
    onMarkRead: (Set<String>) -> Unit,
    onMarkUnread: (Set<String>) -> Unit,
    onAssignSeries: (Set<String>, String) -> Unit,
    onReorderSeriesBooks: (List<String>) -> Unit,
    onSetBooksHidden: (Set<String>, Boolean) -> Unit,
    onDeleteBooks: (Set<String>) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onRemoveBooksFromSeries: (Set<String>) -> Unit,
    onRenameSeries: (String, String) -> Unit,
    onMergeSeries: (List<String>, String) -> Unit,
    onDeleteSeries: (Set<String>) -> Unit,
    onAutoCreateSeries: () -> Unit,
    onSeriesModeChange: (Boolean) -> Unit,
    onSelectedSeriesChange: (String?) -> Unit,
    onBackToSearch: () -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val libraryPreferences = remember(context) { LibraryPreferenceStore(context) }
    var viewModeName by rememberSaveable {
        mutableStateOf(libraryPreferences.loadLibraryViewMode())
    }
    var gridColumns by rememberSaveable {
        mutableIntStateOf(libraryPreferences.loadLibraryGridColumns())
    }
    val seriesMode = state.librarySeriesMode
    val selectedSeries = state.selectedLibrarySeries
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedSeriesNames by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var seriesSelectionMode by rememberSaveable { mutableStateOf(false) }
    var firstSelectedSeriesName by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesDialog by rememberSaveable { mutableStateOf(false) }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var editingSeriesOrder by rememberSaveable { mutableStateOf(false) }
    var addingToSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSeriesSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var renameBookDialog by rememberSaveable { mutableStateOf(false) }
    var renameSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var mergeSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var renameInput by rememberSaveable { mutableStateOf("") }
    val viewMode = runCatching { LibraryViewMode.valueOf(viewModeName) }
        .getOrDefault(LibraryViewMode.GRID)
    val books = remember(
        state.libraryBooks,
        state.libraryQuery,
        state.libraryFavoritesOnly,
        state.libraryReadFilter,
        state.libraryVisibilityFilter,
        state.libraryHiddenIds,
        state.librarySort,
        state.selectedLibraryLocationUris,
        state.libraryFavoriteIds,
        state.libraryFavoriteSeriesNames,
        state.libraryReadIds,
    ) { visibleLibraryBooks(state) }
    val visibilityBooks = remember(
        state.libraryBooks,
        state.libraryHiddenIds,
        state.libraryVisibilityFilter,
    ) { state.libraryBooksInVisibility() }
    val shownBooks = books.filterBySeries(selectedSeries, state.customSeriesByBookId)
    val mainEntries = remember(books, visibilityBooks, state.customSeriesByBookId, state.librarySort) {
        libraryMainEntries(books, visibilityBooks, state.customSeriesByBookId, state.librarySort)
    }
    val shownEntries = remember(shownBooks) { shownBooks.map { LibraryMainEntry.Book(it) } }
    val pullToRefreshState = rememberPullToRefreshState()
    val mainGridState = rememberLazyGridState()
    val mainListState = rememberLazyListState()
    val seriesBooksGridState = rememberLazyGridState()
    val seriesBooksListState = rememberLazyListState()
    val seriesOverviewGridState = rememberLazyGridState()
    val seriesOverviewListState = rememberLazyListState()
    val filterSignature = libraryFilterSignature(state)
    var previousFilterSignature by rememberSaveable { mutableStateOf(filterSignature) }
    val openSeries: (String) -> Unit = { name ->
        onSeriesModeChange(true)
        onSelectedSeriesChange(name)
    }
    val toggleBookSelection: (String) -> Unit = { id -> selectedIds = selectedIds.toggled(id) }
    val toggleSeriesSelection: (String, Set<String>) -> Unit = { name, ids ->
        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids
        else selectedIds.also { pendingSeriesSelection = name }
    }
    val longPressBook: (String) -> Unit = { id ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!selectionMode) {
            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
        }
        selectionMode = true
        selectedIds = selectedIds + id
    }
    val longPressSeries: (String, String) -> Unit = { name, firstTitle ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!selectionMode) seriesName = firstTitle
        selectionMode = true
        pendingSeriesSelection = name
    }

    LaunchedEffect(filterSignature) {
        if (filterSignature != previousFilterSignature) {
            mainGridState.scrollToItem(0)
            mainListState.scrollToItem(0)
            seriesBooksGridState.scrollToItem(0)
            seriesBooksListState.scrollToItem(0)
            seriesOverviewGridState.scrollToItem(0)
            seriesOverviewListState.scrollToItem(0)
        }
        previousFilterSignature = filterSignature
    }

    BackHandler {
        when {
            selectionMode -> {
                addingToSeries?.let { series ->
                    onSeriesModeChange(true)
                    onSelectedSeriesChange(series)
                }
                addingToSeries = null
                selectionMode = false
                selectedIds = emptySet()
            }
            seriesSelectionMode -> {
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
                firstSelectedSeriesName = null
            }
            editingSeriesOrder -> editingSeriesOrder = false
            selectedSeries != null -> onSelectedSeriesChange(null)
            seriesMode -> onSeriesModeChange(false)
            else -> onBackToSearch()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isLibraryPullRefreshing,
        onRefresh = { if (!editingSeriesOrder) onRefresh() },
        state = pullToRefreshState,
        indicator = {
            if (!editingSeriesOrder) {
                PullToRefreshDefaults.Indicator(
                    isRefreshing = state.isLibraryPullRefreshing,
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize().animateContentSize()) {
            OutlinedTextField(
                value = state.libraryQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text("검색 조건") },
                placeholder = { Text("title:제목 artist:작가 tag:태그 series:시리즈") },
                trailingIcon = {
                    if (state.libraryQuery.isNotEmpty()) {
                        TextButton(onClick = { onQueryChange("") }) { Text("×") }
                    }
                },
                singleLine = true,
            )
            if (seriesSelectionMode && seriesMode && selectedSeries == null) {
                LibrarySelectionToolbar(
                    selectedCount = selectedSeriesNames.size,
                    allSelected = seriesNames(books, state.customSeriesByBookId).let {
                        it.isNotEmpty() && selectedSeriesNames.containsAll(it)
                    },
                    onSelectAll = {
                        val all = seriesNames(books, state.customSeriesByBookId)
                        if (selectedSeriesNames.containsAll(all)) {
                            selectedSeriesNames = emptySet()
                            firstSelectedSeriesName = null
                        } else {
                            selectedSeriesNames = all
                            firstSelectedSeriesName = all.firstOrNull()
                        }
                    },
                    onFavorite = null,
                    onRead = null,
                    onSeries = null,
                    onMerge = if (selectedSeriesNames.size >= 2) ({
                        renameInput = firstSelectedSeriesName ?: selectedSeriesNames.first()
                        mergeSeriesDialog = true
                    }) else null,
                    onRename = selectedSeriesNames.singleOrNull()?.let { series ->
                        {
                            renameInput = series
                            renameSeriesDialog = true
                        }
                    },
                    onDelete = { deleteDialog = true },
                    onClose = {
                        seriesSelectionMode = false
                        selectedSeriesNames = emptySet()
                        firstSelectedSeriesName = null
                    },
                )
            } else if (selectionMode) {
                val allSelectedRead = selectedIds.isNotEmpty() && selectedIds.all { it in state.libraryReadIds }
                val allSelectedHidden = selectedIds.isNotEmpty() && selectedIds.all { it in state.libraryHiddenIds }
                LibrarySelectionToolbar(
                    selectedCount = selectedIds.size,
                    allSelected = shownBooks.isNotEmpty() && selectedIds.containsAll(shownBooks.map { it.id }),
                    onSelectAll = {
                        selectedIds = if (selectedIds.containsAll(shownBooks.map { it.id })) emptySet()
                        else shownBooks.mapTo(linkedSetOf(), LibraryBook::id)
                    },
                    onFavorite = if (addingToSeries == null) ({ onAddFavorites(selectedIds) }) else null,
                    onRead = if (addingToSeries == null) ({
                        if (allSelectedRead) onMarkUnread(selectedIds) else onMarkRead(selectedIds)
                    }) else null,
                    readLabel = if (allSelectedRead) "읽지 않음" else "읽음",
                    onSeries = if (addingToSeries == null && selectedSeries == null) {
                        ({ seriesDialog = true })
                    } else null,
                    seriesLabel = "시리즈 추가",
                    onSave = addingToSeries?.let { target ->
                        {
                            val previousIds = state.customSeriesByBookId
                                .filterValues { it.name == target }.keys
                            onRemoveBooksFromSeries(previousIds - selectedIds)
                            onAssignSeries(selectedIds, target)
                            addingToSeries = null
                            selectionMode = false
                            selectedIds = emptySet()
                            onSeriesModeChange(true)
                            onSelectedSeriesChange(target)
                        }
                    },
                    onRename = if (addingToSeries == null) selectedIds.singleOrNull()?.let { id ->
                        {
                            renameInput = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                            renameBookDialog = true
                        }
                    } else null,
                    onHide = if (addingToSeries == null) ({
                        onSetBooksHidden(selectedIds, !allSelectedHidden)
                        selectionMode = false
                        selectedIds = emptySet()
                    }) else null,
                    hideLabel = if (allSelectedHidden) "숨김 해제" else "숨김",
                    onDelete = if (addingToSeries == null) ({ deleteDialog = true }) else null,
                    onClose = {
                        addingToSeries?.let { series ->
                            onSeriesModeChange(true)
                            onSelectedSeriesChange(series)
                        }
                        addingToSeries = null
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                )
            } else {
                LibraryToolbar(
                    state = state,
                    onToggleFavoritesFilter = onToggleFavoritesFilter,
                    onReadFilterChange = onReadFilterChange,
                    onVisibilityFilterChange = onVisibilityFilterChange,
                    onSortChange = onSortChange,
                    onLocationChange = onLocationChange,
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    seriesMode = seriesMode,
                    onSeriesModeChange = {
                        onSeriesModeChange(it)
                        editingSeriesOrder = false
                        selectionMode = false
                        seriesSelectionMode = false
                        selectedIds = emptySet()
                        selectedSeriesNames = emptySet()
                    },
                    onAutoCreateSeries = onAutoCreateSeries,
                    onViewModeChange = {
                        viewModeName = it.name
                        libraryPreferences.saveLibraryViewMode(it.name)
                    },
                    onGridColumnsChange = {
                        gridColumns = it
                        libraryPreferences.saveLibraryGridColumns(it)
                    },
                )
            }
            if (seriesMode && selectedSeries != null && !selectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = {
                        onSelectedSeriesChange(null)
                        editingSeriesOrder = false
                    }) { Text("<") }
                    if (shownBooks.any { state.customSeriesByBookId[it.id]?.name == selectedSeries }) {
                        TextButton(onClick = { editingSeriesOrder = !editingSeriesOrder }) {
                            Text(if (editingSeriesOrder) "순서 편집 완료" else "순서 편집")
                        }
                        TextButton(
                            enabled = !editingSeriesOrder,
                            onClick = {
                                val target = selectedSeries.orEmpty()
                                selectedIds = state.customSeriesByBookId
                                    .filterValues { it.name == target }.keys
                                addingToSeries = target
                                editingSeriesOrder = false
                                selectionMode = true
                                onSelectedSeriesChange(null)
                                onSeriesModeChange(false)
                            },
                        ) { Text("시리즈 추가") }
                    }
                }
            }

            when {
                state.isLibraryScanning && state.libraryBooks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.libraryLocations.isEmpty() && state.desktopLibraryLocations.isEmpty() -> LibraryEmpty(
                    "설정에서 스캔할 라이브러리 폴더를 먼저 추가하세요.",
                )
                books.isEmpty() -> LibraryEmpty("조건에 맞는 책이 없습니다.")
                seriesMode && selectedSeries == null -> LibrarySeriesOverview(
                    books = books,
                    allBooks = visibilityBooks,
                    customSeries = state.customSeriesByBookId,
                    favoriteSeriesNames = state.libraryFavoriteSeriesNames,
                    readBookIds = state.libraryReadIds,
                    viewMode = viewMode,
                    columns = gridColumns,
                    sort = state.librarySort,
                    gridState = seriesOverviewGridState,
                    listState = seriesOverviewListState,
                    selectionMode = seriesSelectionMode,
                    selectedSeries = selectedSeriesNames,
                    onOpenSeries = onSelectedSeriesChange,
                    onToggleFavorite = onToggleSeriesFavorite,
                    onToggleSelection = { name ->
                        val wasSelected = name in selectedSeriesNames
                        selectedSeriesNames = selectedSeriesNames.toggled(name)
                        firstSelectedSeriesName = when {
                            !wasSelected && firstSelectedSeriesName == null -> name
                            wasSelected && firstSelectedSeriesName == name -> selectedSeriesNames.firstOrNull()
                            else -> firstSelectedSeriesName
                        }
                    },
                    onLongPress = { name ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!seriesSelectionMode) firstSelectedSeriesName = name
                        seriesSelectionMode = true
                        selectedSeriesNames = selectedSeriesNames + name
                    },
                )
                editingSeriesOrder && selectedSeries != null -> LibrarySeriesOrderEditor(
                    books = shownBooks,
                    customSeries = state.customSeriesByBookId,
                    onReorder = onReorderSeriesBooks,
                )
                viewMode == LibraryViewMode.GRID -> LibraryGrid(
                    entries = if (seriesMode) shownEntries else mainEntries,
                    state = state,
                    columns = gridColumns,
                    gridState = if (seriesMode) seriesBooksGridState else mainGridState,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = openSeries,
                    onToggleBook = toggleBookSelection,
                    onToggleSeries = toggleSeriesSelection,
                    onLongPressBook = longPressBook,
                    onLongPressSeries = longPressSeries,
                    onToggleFavorite = onToggleFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    onToggleRead = onToggleRead,
                )
                else -> LibraryList(
                    entries = if (seriesMode) shownEntries else mainEntries,
                    state = state,
                    listState = if (seriesMode) seriesBooksListState else mainListState,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = openSeries,
                    onToggleBook = toggleBookSelection,
                    onToggleSeries = toggleSeriesSelection,
                    onLongPressBook = longPressBook,
                    onLongPressSeries = longPressSeries,
                    onToggleFavorite = onToggleFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    onToggleRead = onToggleRead,
                )
            }
            state.libraryScanError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            state.librarySyncError
                ?.takeIf { it != state.libraryScanError }
                ?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
        }
    }

    if (seriesDialog) {
        AlertDialog(
            onDismissRequest = { seriesDialog = false },
            title = { Text("시리즈로 묶기") },
            text = {
                OutlinedTextField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = { Text("시리즈 이름") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAssignSeries(selectedIds, seriesName)
                        selectedIds = emptySet()
                        selectionMode = false
                        seriesName = ""
                        seriesDialog = false
                    },
                    enabled = seriesName.isNotBlank(),
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { seriesDialog = false }) { Text("취소") } },
        )
    }
    pendingSeriesSelection?.let { pendingSeries ->
        AlertDialog(
            onDismissRequest = { pendingSeriesSelection = null },
            title = { Text("시리즈 선택") },
            text = { Text("이 시리즈의 모든 갤러리가 선택됩니다. 새 시리즈로 저장하면 기존 시리즈는 해제됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = state.customSeriesByBookId
                        .filterValues { it.name == pendingSeries }.keys
                    selectedIds = selectedIds + ids
                    pendingSeriesSelection = null
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeriesSelection = null }) { Text("취소") }
            },
        )
    }
    if (deleteDialog) {
        val deletingSeries = seriesSelectionMode && seriesMode && selectedSeries == null
        val removingFromSeries = selectionMode && seriesMode && selectedSeries != null
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = {
                Text(when {
                    deletingSeries -> "시리즈 삭제"
                    removingFromSeries -> "시리즈에서 제외"
                    else -> "갤러리에서 삭제"
                })
            },
            text = {
                Text(when {
                    deletingSeries -> "선택한 ${selectedSeriesNames.size}개 시리즈를 삭제할까요? 갤러리는 삭제되지 않습니다."
                    removingFromSeries -> "선택한 ${selectedIds.size}개 갤러리를 이 시리즈에서 제외할까요?"
                    else -> "선택한 ${selectedIds.size}개 항목을 삭제할까요? 데스크톱 항목은 원본을 휴지통으로 이동한 뒤 DB에서 삭제하며, 로컬 항목은 파일 또는 폴더를 영구 삭제합니다."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        deletingSeries -> onDeleteSeries(selectedSeriesNames)
                        removingFromSeries -> onRemoveBooksFromSeries(selectedIds)
                        else -> onDeleteBooks(selectedIds)
                    }
                    selectedIds = emptySet()
                    selectedSeriesNames = emptySet()
                    selectionMode = false
                    seriesSelectionMode = false
                    deleteDialog = false
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("취소") } },
        )
    }
    if (renameBookDialog) {
        RenameDialog(
            title = "갤러리 이름 변경",
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { renameBookDialog = false },
            onConfirm = {
                selectedIds.singleOrNull()?.let { onRenameBook(it, renameInput) }
                renameBookDialog = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }
    if (renameSeriesDialog) {
        val oldName = selectedSeriesNames.singleOrNull()
        RenameDialog(
            title = "시리즈 이름 변경",
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { renameSeriesDialog = false },
            onConfirm = {
                oldName?.let { onRenameSeries(it, renameInput) }
                renameSeriesDialog = false
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
            },
        )
    }
    if (mergeSeriesDialog) {
        RenameDialog(
            title = "시리즈 합치기",
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { mergeSeriesDialog = false },
            onConfirm = {
                onMergeSeries(selectedSeriesNames.toList(), renameInput)
                mergeSeriesDialog = false
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
                firstSelectedSeriesName = null
            },
        )
    }
}

@Composable
private fun RenameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("새 이름") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text("변경") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun LibraryToolbar(
    state: MainUiState,
    onToggleFavoritesFilter: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String) -> Unit,
    viewMode: LibraryViewMode,
    gridColumns: Int,
    seriesMode: Boolean,
    onSeriesModeChange: (Boolean) -> Unit,
    onAutoCreateSeries: () -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (seriesMode) {
            Button(
                onClick = { onSeriesModeChange(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                SeriesArchiveIcon()
                Text("시리즈", modifier = Modifier.padding(start = 7.dp))
            }
            if (state.selectedLibrarySeries == null) {
                OutlinedButton(
                    onClick = onAutoCreateSeries,
                    enabled = !state.isAutoCreatingSeries,
                ) {
                    if (state.isAutoCreatingSeries) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = if (state.isAutoCreatingSeries) "분석 중" else "자동생성",
                        modifier = Modifier.padding(start = if (state.isAutoCreatingSeries) 7.dp else 0.dp),
                    )
                }
            }
        } else {
            OutlinedButton(onClick = { onSeriesModeChange(true) }) {
                SeriesArchiveIcon()
                Text("시리즈", modifier = Modifier.padding(start = 7.dp))
            }
        }
        LibraryFilterMenu(
            favoritesOnly = state.libraryFavoritesOnly,
            readFilter = state.libraryReadFilter,
            visibilityFilter = state.libraryVisibilityFilter,
            onToggleFavorites = onToggleFavoritesFilter,
            onReadFilterChange = onReadFilterChange,
            onVisibilityFilterChange = onVisibilityFilterChange,
        )
        LibrarySortControl(sort = state.librarySort, onSortChange = onSortChange)
        LibraryViewMenu(
            viewMode = viewMode,
            gridColumns = gridColumns,
            locations = state.libraryLocations + state.desktopLibraryLocations,
            selectedLocationUris = state.selectedLibraryLocationUris,
            onToggleLocation = onLocationChange,
            onViewModeChange = onViewModeChange,
            onGridColumnsChange = onGridColumnsChange,
        )
    }
}

@Composable
private fun SeriesArchiveIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val slotColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    Canvas(modifier = modifier.size(18.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.08f, size.height * 0.27f),
            size = Size(size.width * 0.84f, size.height * 0.66f),
            cornerRadius = CornerRadius(size.width * 0.15f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height * 0.12f),
            size = Size(size.width, size.height * 0.23f),
            cornerRadius = CornerRadius(size.width * 0.12f),
        )
        drawRoundRect(
            color = slotColor,
            topLeft = Offset(size.width * 0.36f, size.height * 0.61f),
            size = Size(size.width * 0.28f, size.height * 0.08f),
            cornerRadius = CornerRadius(size.width * 0.04f),
        )
    }
}

@Composable
private fun LibrarySelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)?,
    onRead: (() -> Unit)?,
    onSeries: (() -> Unit)?,
    onMerge: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    readLabel: String = "읽음",
    seriesLabel: String = "시리즈",
    onRename: (() -> Unit)?,
    onHide: (() -> Unit)? = null,
    hideLabel: String = "숨김",
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${selectedCount}개 선택", fontWeight = FontWeight.Bold)
        TextButton(onClick = onSelectAll) { Text(if (allSelected) "전체선택 취소" else "전체선택") }
        onFavorite?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text("♥", fontSize = 24.sp) } }
        onRead?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text(readLabel) } }
        onSeries?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text(seriesLabel) } }
        onMerge?.let { action -> TextButton(onClick = action) { Text("합치기") } }
        onSave?.let { action -> TextButton(onClick = action) { Text("저장") } }
        onRename?.let { action -> TextButton(onClick = action) { Text("이름변경") } }
        onHide?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text(hideLabel) } }
        onDelete?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text("삭제") } }
        TextButton(onClick = onClose) { Text("취소") }
    }
}

@Composable
private fun LibraryFilterMenu(
    favoritesOnly: Boolean,
    readFilter: LibraryReadFilter,
    visibilityFilter: LibraryVisibilityFilter,
    onToggleFavorites: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = favoritesOnly || readFilter != LibraryReadFilter.ALL ||
        visibilityFilter != LibraryVisibilityFilter.VISIBLE
    Box {
        if (active) {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { FilterFunnelIcon(); Text("필터", modifier = Modifier.padding(start = 7.dp)) }
        } else {
            OutlinedButton(onClick = { expanded = true }) {
                FilterFunnelIcon()
                Text("필터", modifier = Modifier.padding(start = 7.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (favoritesOnly) "✓ 즐겨찾기" else "즐겨찾기") },
                onClick = onToggleFavorites,
            )
            HorizontalDivider()
            LibraryReadFilter.entries.forEach { filter ->
                val label = when (filter) {
                    LibraryReadFilter.ALL -> "전체"
                    LibraryReadFilter.UNREAD -> "안읽음"
                    LibraryReadFilter.READ -> "읽음"
                }
                DropdownMenuItem(
                    text = { Text(if (readFilter == filter) "✓ $label" else label) },
                    onClick = { expanded = false; onReadFilterChange(filter) },
                )
            }
            HorizontalDivider()
            LibraryVisibilityFilter.entries.forEach { filter ->
                val label = when (filter) {
                    LibraryVisibilityFilter.VISIBLE -> "표시 항목"
                    LibraryVisibilityFilter.HIDDEN -> "숨김 항목"
                    LibraryVisibilityFilter.ALL -> "표시+숨김 전체"
                }
                DropdownMenuItem(
                    text = { Text(if (visibilityFilter == filter) "✓ $label" else label) },
                    onClick = { expanded = false; onVisibilityFilterChange(filter) },
                )
            }
        }
    }
}

@Composable
private fun LibrarySortControl(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val titleSelected = sort == LibrarySort.TITLE_ASC || sort == LibrarySort.TITLE_DESC
    val artistSelected = sort == LibrarySort.ARTIST_ASC || sort == LibrarySort.ARTIST_DESC
    val modifiedAtSelected = sort == LibrarySort.NEWEST || sort == LibrarySort.OLDEST
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            ) {
                SortListIcon()
                Text("정렬", modifier = Modifier.padding(start = 7.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (titleSelected) "✓ 제목" else "제목") },
                    onClick = {
                        expanded = false
                        onSortChange(if (sort.isAscending()) LibrarySort.TITLE_ASC else LibrarySort.TITLE_DESC)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (artistSelected) "✓ 작가" else "작가") },
                    onClick = {
                        expanded = false
                        onSortChange(if (sort.isAscending()) LibrarySort.ARTIST_ASC else LibrarySort.ARTIST_DESC)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (modifiedAtSelected) "✓ 수정일" else "수정일") },
                    onClick = {
                        expanded = false
                        onSortChange(if (sort.isAscending()) LibrarySort.OLDEST else LibrarySort.NEWEST)
                    },
                )
            }
        }
        OutlinedButton(
            onClick = { onSortChange(sort.reversed()) },
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            SortDirectionIcon(ascending = sort.isAscending())
        }
    }
}

@Composable
private fun FilterFunnelIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(19.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.16f)
            lineTo(size.width * 0.92f, size.height * 0.16f)
            lineTo(size.width * 0.62f, size.height * 0.51f)
            lineTo(size.width * 0.62f, size.height * 0.86f)
            lineTo(size.width * 0.39f, size.height * 0.72f)
            lineTo(size.width * 0.39f, size.height * 0.51f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun SortListIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val lineColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier.size(19.dp)) {
        drawRoundRect(color, cornerRadius = CornerRadius(size.width * 0.24f))
        listOf(0.34f to 0.72f, 0.5f to 0.62f, 0.66f to 0.5f).forEach { (y, end) ->
            drawLine(
                color = lineColor,
                start = Offset(size.width * 0.27f, size.height * y),
                end = Offset(size.width * end, size.height * y),
                strokeWidth = size.width * 0.075f,
            )
        }
    }
}

@Composable
private fun SortDirectionIcon(ascending: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(22.dp)) {
        val ys = listOf(0.28f, 0.5f, 0.72f)
        val widths = if (ascending) listOf(0.28f, 0.43f, 0.58f) else listOf(0.58f, 0.43f, 0.28f)
        ys.zip(widths).forEach { (y, width) ->
            drawLine(
                color,
                Offset(size.width * 0.05f, size.height * y),
                Offset(size.width * width, size.height * y),
                strokeWidth = size.width * 0.07f,
            )
        }
        val x = size.width * 0.79f
        drawLine(color, Offset(x, size.height * 0.18f), Offset(x, size.height * 0.82f), size.width * 0.07f)
        val arrowY = if (ascending) size.height * 0.18f else size.height * 0.82f
        val direction = if (ascending) 1f else -1f
        drawLine(color, Offset(x, arrowY), Offset(x - size.width * 0.14f, arrowY + direction * size.height * 0.14f), size.width * 0.07f)
        drawLine(color, Offset(x, arrowY), Offset(x + size.width * 0.14f, arrowY + direction * size.height * 0.14f), size.width * 0.07f)
    }
}

@Composable
private fun LibraryViewMenu(
    viewMode: LibraryViewMode,
    gridColumns: Int,
    locations: List<com.doujinmenu.android.model.StorageLocation>,
    selectedLocationUris: Set<String>?,
    onToggleLocation: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("⋯") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (locations.isNotEmpty()) {
                locations.forEach { location ->
                    DropdownMenuItem(
                        text = {
                            Text((if (location.isCloud) "클라우드 · " else "") + location.displayName)
                        },
                        leadingIcon = {
                            LibrarySelectionMark(
                                selected = selectedLocationUris == null || location.uri in selectedLocationUris,
                            )
                        },
                        onClick = { onToggleLocation(location.uri) },
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(if (viewMode == LibraryViewMode.GRID) "✓ 그리드 보기" else "그리드 보기") },
                onClick = { onViewModeChange(LibraryViewMode.GRID) },
            )
            DropdownMenuItem(
                text = { Text(if (viewMode == LibraryViewMode.LIST) "✓ 리스트 보기" else "리스트 보기") },
                onClick = { expanded = false; onViewModeChange(LibraryViewMode.LIST) },
            )
            HorizontalDivider()
            listOf(2 to "크게", 3 to "중간", 4 to "작게").forEach { (columns, label) ->
                DropdownMenuItem(
                    text = { Text(if (gridColumns == columns) "✓ $label" else label) },
                    onClick = {
                        expanded = false
                        onGridColumnsChange(columns)
                        onViewModeChange(LibraryViewMode.GRID)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val selectedColor = Color(0xFF356AE6)
    val idleColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.size(22.dp)) {
        drawCircle(
            color = if (selected) selectedColor else idleColor,
            radius = size.minDimension * 0.4f,
            style = Stroke(width = size.minDimension * 0.12f),
        )
        if (selected) {
            drawCircle(selectedColor, radius = size.minDimension * 0.21f)
        }
    }
}

@Composable
private fun LibraryGrid(
    entries: List<LibraryMainEntry>,
    state: MainUiState,
    columns: Int,
    gridState: LazyGridState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> "book:${entry.book.id}"
                is LibraryMainEntry.Series -> "series:${entry.name}"
            }
        }) { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> LibraryBookCard(
                    book = entry.book,
                    badgeScale = thumbnailBadgeScale(columns),
                    favorite = entry.book.id in state.libraryFavoriteIds,
                    read = entry.book.id in state.libraryReadIds,
                    progress = state.libraryProgress[entry.book.id] ?: 0,
                    selected = entry.book.id in selectedIds,
                    selectionMode = selectionMode,
                    onOpen = { if (selectionMode) onToggleBook(entry.book.id) else onOpenBook(entry.book.id) },
                    onLongPress = { onLongPressBook(entry.book.id) },
                    onToggleFavorite = { onToggleFavorite(entry.book.id) },
                    onToggleRead = { onToggleRead(entry.book.id) },
                )
                is LibraryMainEntry.Series -> {
                    val ids = entry.books.mapTo(linkedSetOf(), LibraryBook::id)
                    SeriesCard(
                        name = entry.name,
                        books = entry.books,
                        listMode = false,
                        badgeScale = thumbnailBadgeScale(columns),
                        favorite = entry.name in state.libraryFavoriteSeriesNames,
                        read = entry.books.isNotEmpty() && entry.books.all { it.id in state.libraryReadIds },
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
                        onToggleFavorite = { onToggleSeriesFavorite(entry.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    entries: List<LibraryMainEntry>,
    state: MainUiState,
    listState: LazyListState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listItems(entries, key = { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> "book:${entry.book.id}"
                is LibraryMainEntry.Series -> "series:${entry.name}"
            }
        }) { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> LibraryBookListItem(
                    book = entry.book,
                    favorite = entry.book.id in state.libraryFavoriteIds,
                    read = entry.book.id in state.libraryReadIds,
                    progress = state.libraryProgress[entry.book.id] ?: 0,
                    selected = entry.book.id in selectedIds,
                    selectionMode = selectionMode,
                    onOpen = { if (selectionMode) onToggleBook(entry.book.id) else onOpenBook(entry.book.id) },
                    onLongPress = { onLongPressBook(entry.book.id) },
                    onToggleFavorite = { onToggleFavorite(entry.book.id) },
                    onToggleRead = { onToggleRead(entry.book.id) },
                )
                is LibraryMainEntry.Series -> {
                    val ids = entry.books.mapTo(linkedSetOf(), LibraryBook::id)
                    SeriesCard(
                        name = entry.name,
                        books = entry.books,
                        listMode = true,
                        badgeScale = LIST_THUMBNAIL_BADGE_SCALE,
                        favorite = entry.name in state.libraryFavoriteSeriesNames,
                        read = entry.books.isNotEmpty() && entry.books.all { it.id in state.libraryReadIds },
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
                        onToggleFavorite = { onToggleSeriesFavorite(entry.name) },
                    )
                }
            }
        }
    }
}

private fun libraryMainEntries(
    visibleBooks: List<LibraryBook>,
    allBooks: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    sort: LibrarySort,
): List<LibraryMainEntry> {
    val visibleSeries = visibleBooks.mapNotNullTo(linkedSetOf()) { customSeries[it.id]?.name }
    val independent = visibleBooks.filter { customSeries[it.id] == null }.map { LibraryMainEntry.Book(it) }
    val series = visibleSeries.map { name ->
        LibraryMainEntry.Series(
            name,
            allBooks.filter { customSeries[it.id]?.name == name }
                .sortedBy { customSeries[it.id]?.order ?: Int.MAX_VALUE },
        )
    }
    val entries = independent + series
    return when (sort) {
        LibrarySort.TITLE_ASC -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.TITLE_DESC -> entries.sortedWith(compareByDescending<LibraryMainEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.ARTIST_ASC -> entries.sortedWith(libraryMainEntryArtistComparator(descending = false))
        LibrarySort.ARTIST_DESC -> entries.sortedWith(libraryMainEntryArtistComparator(descending = true))
        LibrarySort.NEWEST -> entries.sortedByDescending(LibraryMainEntry::modifiedAt)
        LibrarySort.OLDEST -> entries.sortedBy(LibraryMainEntry::modifiedAt)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
    book: LibraryBook,
    badgeScale: Float = 1f,
    favorite: Boolean,
    read: Boolean,
    progress: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress)) {
        Box {
            AsyncImage(
                model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                contentDescription = "${book.title} 표지",
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                colorFilter = selectionColorFilter(selectionMode),
            )
            if (!selectionMode) {
                LibraryThumbnailBadges(
                    read = read,
                    isCloud = book.isCloud,
                    favorite = favorite,
                    onToggleFavorite = onToggleFavorite,
                    scale = badgeScale,
                )
            }
            if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center), badgeScale)
        }
        LibraryBookText(book, progress, Modifier.padding(10.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookListItem(
    book: LibraryBook,
    favorite: Boolean,
    read: Boolean,
    progress: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().height(132.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(94.dp).fillMaxHeight()) {
                AsyncImage(
                    model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                    contentDescription = "${book.title} 표지",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = selectionColorFilter(selectionMode),
                )
                if (!selectionMode) {
                    LibraryThumbnailBadges(
                        read = read,
                        isCloud = book.isCloud,
                        favorite = favorite,
                        onToggleFavorite = onToggleFavorite,
                        scale = LIST_THUMBNAIL_BADGE_SCALE,
                    )
                }
                if (selectionMode) {
                    SelectionIndicator(selected, Modifier.align(Alignment.Center), LIST_THUMBNAIL_BADGE_SCALE)
                }
            }
            LibraryBookText(book, progress, Modifier.padding(12.dp))
        }
    }
}

@Composable
internal fun BoxScope.LibraryThumbnailBadges(
    read: Boolean,
    isCloud: Boolean?,
    favorite: Boolean,
    showFavorite: Boolean = true,
    onToggleFavorite: () -> Unit,
    scale: Float,
) {
    if (read) {
        ThumbnailBadge("읽음", Modifier.align(Alignment.TopStart), scale)
    }
    if (isCloud == true) {
        ThumbnailIconBadge(Modifier.align(Alignment.TopEnd), scale) { CloudOutlineIcon(scale = scale) }
    } else if (isCloud == false) {
        ThumbnailBadge("기기", Modifier.align(Alignment.TopEnd), scale)
    }
    if (showFavorite) Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp * scale),
        shape = RoundedCornerShape(10.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(42.dp * scale),
            contentPadding = PaddingValues(0.dp),
        ) {
            FavoriteHeartIcon(favorite = favorite, modifier = Modifier.size(22.dp * scale))
        }
    }
}

@Composable
private fun FavoriteHeartIcon(favorite: Boolean, modifier: Modifier = Modifier) {
    val color = Color(0xFFE53935).copy(alpha = if (favorite) 1f else 0.82f)
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.9f)
            cubicTo(
                size.width * 0.42f, size.height * 0.8f,
                size.width * 0.08f, size.height * 0.59f,
                size.width * 0.08f, size.height * 0.31f,
            )
            cubicTo(
                size.width * 0.08f, size.height * 0.12f,
                size.width * 0.23f, size.height * 0.05f,
                size.width * 0.37f, size.height * 0.09f,
            )
            cubicTo(
                size.width * 0.44f, size.height * 0.11f,
                size.width * 0.48f, size.height * 0.17f,
                size.width * 0.5f, size.height * 0.22f,
            )
            cubicTo(
                size.width * 0.55f, size.height * 0.1f,
                size.width * 0.66f, size.height * 0.06f,
                size.width * 0.76f, size.height * 0.09f,
            )
            cubicTo(
                size.width * 0.91f, size.height * 0.13f,
                size.width * 0.97f, size.height * 0.27f,
                size.width * 0.91f, size.height * 0.44f,
            )
            cubicTo(
                size.width * 0.84f, size.height * 0.63f,
                size.width * 0.61f, size.height * 0.8f,
                size.width * 0.5f, size.height * 0.9f,
            )
            close()
        }
        if (favorite) drawPath(path, color)
        else drawPath(path, color, style = Stroke(width = 3.4.dp.toPx()))
    }
}

@Composable
private fun ThumbnailIconBadge(
    modifier: Modifier,
    scale: Float,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Box(
            modifier = Modifier.width(35.dp * scale).height(27.dp * scale),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun CloudOutlineIcon(modifier: Modifier = Modifier, scale: Float = 1f) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(18.dp * scale)) {
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.78f)
            cubicTo(size.width * 0.06f, size.height * 0.78f, 0f, size.height * 0.65f, size.width * 0.04f, size.height * 0.52f)
            cubicTo(size.width * 0.07f, size.height * 0.41f, size.width * 0.16f, size.height * 0.35f, size.width * 0.27f, size.height * 0.36f)
            cubicTo(size.width * 0.33f, size.height * 0.17f, size.width * 0.49f, size.height * 0.09f, size.width * 0.64f, size.height * 0.19f)
            cubicTo(size.width * 0.71f, size.height * 0.23f, size.width * 0.75f, size.height * 0.29f, size.width * 0.77f, size.height * 0.37f)
            cubicTo(size.width * 0.92f, size.height * 0.39f, size.width, size.height * 0.5f, size.width * 0.97f, size.height * 0.63f)
            cubicTo(size.width * 0.95f, size.height * 0.73f, size.width * 0.87f, size.height * 0.78f, size.width * 0.77f, size.height * 0.78f)
            close()
        }
        drawPath(path, color, style = Stroke(width = 2.2.dp.toPx()))
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    Surface(
        modifier = modifier.size(42.dp * scale).border(
            width = 3.dp * scale,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            shape = CircleShape,
        ),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Text("✓", color = Color.White, fontSize = 25.sp * scale, fontWeight = FontWeight.Bold)
        }
    }
}

private fun selectionColorFilter(enabled: Boolean): ColorFilter? = if (enabled) {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
} else null

@Composable
private fun ThumbnailBadge(text: String, modifier: Modifier, scale: Float) {
    Surface(
        modifier = modifier.padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp * scale, vertical = 4.dp * scale),
            fontSize = 11.sp * scale,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LibraryBookText(book: LibraryBook, progress: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (book.metadata.artists.isNotEmpty()) {
            Text(
                book.metadata.artists.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (book.metadata.tags.isNotEmpty()) {
            Text(
                book.metadata.tags.take(4).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${book.pages.size}장 · ${book.locationName}" +
                if (progress > 0) " · ${progress + 1}장부터" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySeriesOverview(
    books: List<LibraryBook>,
    allBooks: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    favoriteSeriesNames: Set<String>,
    readBookIds: Set<String>,
    viewMode: LibraryViewMode,
    columns: Int,
    sort: LibrarySort,
    gridState: LazyGridState,
    listState: LazyListState,
    selectionMode: Boolean,
    selectedSeries: Set<String>,
    onOpenSeries: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val groups = remember(books, allBooks, customSeries, sort) {
        val grouped = seriesNames(books, customSeries).map { seriesName ->
            seriesName to allBooks.filter { customSeries[it.id]?.name == seriesName }
                .sortedWith(
                    compareBy<LibraryBook> {
                        customSeries[it.id]?.takeIf { assignment -> assignment.name == seriesName }?.order
                            ?: Int.MAX_VALUE
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                )
        }
        when (sort) {
            LibrarySort.TITLE_ASC -> grouped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
            LibrarySort.TITLE_DESC -> grouped.sortedWith(compareByDescending<Pair<String, List<LibraryBook>>, String>(String.CASE_INSENSITIVE_ORDER) { it.first })
            LibrarySort.ARTIST_ASC -> grouped.sortedWith(seriesGroupArtistComparator(descending = false))
            LibrarySort.ARTIST_DESC -> grouped.sortedWith(seriesGroupArtistComparator(descending = true))
            LibrarySort.NEWEST -> grouped.sortedByDescending { (_, seriesBooks) ->
                seriesBooks.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
            }
            LibrarySort.OLDEST -> grouped.sortedBy { (_, seriesBooks) ->
                seriesBooks.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
            }
        }
    }
    if (groups.isEmpty()) {
        LibraryEmpty("등록된 시리즈가 없습니다. 항목을 길게 눌러 시리즈로 묶어보세요.")
        return
    }
    if (viewMode == LibraryViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.first }) { (name, seriesBooks) ->
                SeriesCard(
                    name = name,
                    books = seriesBooks,
                    listMode = false,
                    badgeScale = thumbnailBadgeScale(columns),
                    favorite = name in favoriteSeriesNames,
                    read = seriesBooks.isNotEmpty() && seriesBooks.all { it.id in readBookIds },
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
                    onToggleFavorite = { onToggleFavorite(name) },
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listItems(groups, key = { it.first }) { (name, seriesBooks) ->
                SeriesCard(
                    name = name,
                    books = seriesBooks,
                    listMode = true,
                    badgeScale = LIST_THUMBNAIL_BADGE_SCALE,
                    favorite = name in favoriteSeriesNames,
                    read = seriesBooks.isNotEmpty() && seriesBooks.all { it.id in readBookIds },
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
                    onToggleFavorite = { onToggleFavorite(name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesCard(
    name: String,
    books: List<LibraryBook>,
    listMode: Boolean,
    badgeScale: Float,
    favorite: Boolean,
    read: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val cover = books.firstOrNull()
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth()
            .then(if (listMode) Modifier.height(142.dp) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        if (listMode) {
            Row(modifier = Modifier.fillMaxSize()) {
                SeriesCover(
                    book = cover,
                    books = books,
                    context = context,
                    favorite = favorite,
                    read = read,
                    onToggleFavorite = onToggleFavorite,
                    selectionMode = selectionMode,
                    selected = selected,
                    badgeScale = badgeScale,
                    modifier = Modifier.width(100.dp).fillMaxHeight(),
                )
                SeriesText(name, books, Modifier.padding(14.dp))
            }
        } else {
            Column {
                SeriesCover(
                    book = cover,
                    books = books,
                    context = context,
                    favorite = favorite,
                    read = read,
                    onToggleFavorite = onToggleFavorite,
                    selectionMode = selectionMode,
                    selected = selected,
                    badgeScale = badgeScale,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                )
                SeriesText(name, books, Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun SeriesCover(
    book: LibraryBook?,
    books: List<LibraryBook>,
    context: android.content.Context,
    favorite: Boolean,
    read: Boolean,
    onToggleFavorite: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    badgeScale: Float,
    modifier: Modifier,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (book != null) {
            AsyncImage(
                model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                contentDescription = "${book.title} 시리즈 커버",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = selectionColorFilter(selectionMode),
            )
        }
        if (!selectionMode) {
            SeriesThumbnailBadges(
                read = read,
                source = when {
                    books.all(LibraryBook::isCloud) -> SeriesSource.CLOUD
                    books.none(LibraryBook::isCloud) -> SeriesSource.LOCAL
                    else -> SeriesSource.MIXED
                },
                favorite = favorite,
                onToggleFavorite = onToggleFavorite,
                scale = badgeScale,
            )
        }
        if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center), badgeScale)
    }
}

private enum class SeriesSource { CLOUD, LOCAL, MIXED }

@Composable
private fun BoxScope.SeriesThumbnailBadges(
    read: Boolean,
    source: SeriesSource,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    scale: Float,
) {
    if (read) ThumbnailBadge("읽음", Modifier.align(Alignment.TopStart), scale)
    when (source) {
        SeriesSource.CLOUD ->
            ThumbnailIconBadge(Modifier.align(Alignment.TopEnd), scale) { CloudOutlineIcon(scale = scale) }
        SeriesSource.LOCAL -> ThumbnailBadge("기기", Modifier.align(Alignment.TopEnd), scale)
        SeriesSource.MIXED -> ThumbnailBadge("혼합", Modifier.align(Alignment.TopEnd), scale)
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        SeriesStackIcon(Modifier.padding(6.dp * scale).size(20.dp * scale))
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp * scale),
        shape = RoundedCornerShape(10.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(42.dp * scale),
            contentPadding = PaddingValues(0.dp),
        ) {
            FavoriteHeartIcon(favorite = favorite, modifier = Modifier.size(22.dp * scale))
        }
    }
}

@Composable
private fun SeriesStackIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            topLeft = Offset(size.width * 0.04f, size.height * 0.08f),
            size = Size(size.width * 0.7f, size.height * 0.82f),
            cornerRadius = CornerRadius(size.width * 0.13f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.26f, size.height * 0.18f),
            size = Size(size.width * 0.7f, size.height * 0.82f),
            cornerRadius = CornerRadius(size.width * 0.13f),
        )
    }
}

@Composable
private fun SeriesText(name: String, books: List<LibraryBook>, modifier: Modifier) {
    val artists = books.flatMap { it.metadata.artists }.distinctBy { it.lowercase() }
    val tags = books.flatMap { it.metadata.tags }.distinctBy { it.lowercase() }
    val source = when {
        books.all(LibraryBook::isCloud) -> "클라우드"
        books.none(LibraryBook::isCloud) -> "로컬"
        else -> "클라우드 · 로컬"
    }
    Column(modifier = modifier) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (artists.isNotEmpty()) {
            Text(
                artists.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tags.isNotEmpty()) {
            Text(
                tags.take(4).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${books.size}화 · $source",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySeriesOrderEditor(
    books: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    onReorder: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val booksById = remember(books) { books.associateBy(LibraryBook::id) }
    val itemHeights = remember { mutableMapOf<String, Int>() }
    var orderedBooks by remember(books, customSeries) {
        mutableStateOf(
            books.sortedWith(
                compareBy<LibraryBook> { customSeries[it.id]?.order ?: Int.MAX_VALUE }
                    .thenBy(LibraryBook::id),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listItems(orderedBooks, key = LibraryBook::id) { book ->
            val movable = customSeries[book.id] != null
            val index = orderedBooks.indexOf(book)
            var accumulatedDrag by remember(book.id) { mutableFloatStateOf(0f) }
            var dragging by remember(book.id) { mutableStateOf(false) }
            var dragStartOrder by remember(book.id) { mutableStateOf<List<String>?>(null) }
            var dragOrder by remember(book.id) { mutableStateOf<List<String>?>(null) }
            val currentOrderedBooks by rememberUpdatedState(orderedBooks)
            val dragScale by animateFloatAsState(
                targetValue = if (dragging) 1.025f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "seriesReorderScale",
            )
            val settledDragOffset by animateFloatAsState(
                targetValue = if (dragging) accumulatedDrag else 0f,
                animationSpec = tween(160, easing = LinearOutSlowInEasing),
                label = "seriesReorderOffset",
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { itemHeights[book.id] = it.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .animateItem(
                        placementSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
                    )
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                        translationY = if (dragging) accumulatedDrag else settledDragOffset
                        shadowElevation = if (dragging) 10.dp.toPx() else 0f
                    }
                    .pointerInput(book.id, movable) {
                    if (movable) {
                        val fallbackHeight = 56.dp.toPx()
                        val itemSpacing = 8.dp.toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accumulatedDrag = 0f
                                dragging = true
                                val ids = currentOrderedBooks.map(LibraryBook::id)
                                dragStartOrder = ids
                                dragOrder = ids
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                dragging = false
                                val finalOrder = dragOrder
                                if (finalOrder != null && finalOrder != dragStartOrder) {
                                    onReorder(finalOrder)
                                }
                                dragStartOrder = null
                                dragOrder = null
                            },
                            onDragCancel = {
                                dragging = false
                                dragStartOrder?.let { ids ->
                                    orderedBooks = ids.mapNotNull(booksById::get)
                                }
                                dragStartOrder = null
                                dragOrder = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount.y
                                var ids = dragOrder ?: currentOrderedBooks.map(LibraryBook::id)
                                var moved = false
                                while (true) {
                                    val from = ids.indexOf(book.id)
                                    val direction = when {
                                        accumulatedDrag > 0f -> 1
                                        accumulatedDrag < 0f -> -1
                                        else -> 0
                                    }
                                    val to = from + direction
                                    if (direction == 0 || from < 0 || to !in ids.indices) break
                                    val neighborId = ids[to]
                                    val crossingDistance = (
                                        (itemHeights[book.id] ?: fallbackHeight.toInt()) +
                                            (itemHeights[neighborId] ?: fallbackHeight.toInt())
                                        ) / 2f + itemSpacing
                                    if (kotlin.math.abs(accumulatedDrag) < crossingDistance) break
                                    val reordered = ids.toMutableList()
                                    reordered.removeAt(from)
                                    reordered.add(to, book.id)
                                    ids = reordered
                                    accumulatedDrag -= direction * crossingDistance
                                    moved = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (moved) {
                                    dragOrder = ids
                                    orderedBooks = ids.mapNotNull(booksById::get)
                                }
                            },
                        )
                    }
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(book.title, modifier = Modifier.weight(1f))
                    Text(if (movable) "☰" else "고정", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun LibraryBook.effectiveSeries(
    customSeries: Map<String, CustomSeriesAssignment>,
): List<String> = customSeries[id]?.name?.let(::listOf).orEmpty()

private fun seriesNames(
    books: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
): Set<String> = books.flatMapTo(linkedSetOf()) { it.effectiveSeries(customSeries) }

private fun List<LibraryBook>.filterBySeries(
    series: String?,
    customSeries: Map<String, CustomSeriesAssignment>,
): List<LibraryBook> = if (series == null) {
    this
} else {
    filter { series in it.effectiveSeries(customSeries) }
        .sortedWith(
            compareBy<LibraryBook> { customSeries[it.id]?.takeIf { assignment -> assignment.name == series }?.order ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun LibraryEmpty(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> FilterMenu(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

private fun LibrarySort.isAscending(): Boolean =
    this == LibrarySort.TITLE_ASC || this == LibrarySort.ARTIST_ASC || this == LibrarySort.OLDEST

private fun LibrarySort.reversed(): LibrarySort = when (this) {
    LibrarySort.TITLE_ASC -> LibrarySort.TITLE_DESC
    LibrarySort.TITLE_DESC -> LibrarySort.TITLE_ASC
    LibrarySort.ARTIST_ASC -> LibrarySort.ARTIST_DESC
    LibrarySort.ARTIST_DESC -> LibrarySort.ARTIST_ASC
    LibrarySort.NEWEST -> LibrarySort.OLDEST
    LibrarySort.OLDEST -> LibrarySort.NEWEST
}

internal fun visibleLibraryBooks(state: MainUiState): List<LibraryBook> {
    val queryTerms = parseLibraryQuery(state.libraryQuery)
    val filtered = state.libraryBooksInVisibility().asSequence()
        .filter {
            when {
                state.selectedLibraryLocationUris != null -> it.locationUri in state.selectedLibraryLocationUris
                else -> true
            }
        }
        .filter { book ->
            queryTerms.all { term -> term.matches(book, state.customSeriesByBookId[book.id]?.name) }
        }
        .filter { book ->
            !state.libraryFavoritesOnly ||
                book.id in state.libraryFavoriteIds ||
                state.customSeriesByBookId[book.id]?.name in state.libraryFavoriteSeriesNames
        }
        .filter {
            when (state.libraryReadFilter) {
                LibraryReadFilter.ALL -> true
                LibraryReadFilter.UNREAD -> it.id !in state.libraryReadIds
                LibraryReadFilter.READ -> it.id in state.libraryReadIds
            }
        }
        .toList()
    return when (state.librarySort) {
        LibrarySort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
        LibrarySort.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        LibrarySort.ARTIST_ASC -> filtered.sortedWith(libraryBookArtistComparator(descending = false))
        LibrarySort.ARTIST_DESC -> filtered.sortedWith(libraryBookArtistComparator(descending = true))
        LibrarySort.NEWEST -> filtered.sortedByDescending(LibraryBook::modifiedAt)
        LibrarySort.OLDEST -> filtered.sortedBy(LibraryBook::modifiedAt)
    }
}

internal fun libraryFilterSignature(state: MainUiState): String = buildString {
    append(state.libraryQuery.length).append(':').append(state.libraryQuery)
    append('|').append(state.libraryFavoritesOnly)
    append('|').append(state.libraryReadFilter.name)
    append('|').append(state.libraryVisibilityFilter.name)
    append('|').append(state.librarySort.name)
    append('|').append(state.selectedLibraryLocationUris?.sorted()?.joinToString("\u0001").orEmpty())
}

private fun MainUiState.libraryBooksInVisibility(): List<LibraryBook> = libraryBooks.filter { book ->
    when (libraryVisibilityFilter) {
        LibraryVisibilityFilter.VISIBLE -> book.id !in libraryHiddenIds
        LibraryVisibilityFilter.HIDDEN -> book.id in libraryHiddenIds
        LibraryVisibilityFilter.ALL -> true
    }
}

private data class LibraryQueryTerm(val field: String?, val value: String, val excluded: Boolean) {
    fun matches(book: LibraryBook, customSeriesName: String?): Boolean {
        val candidates = when (field) {
            "title", "제목" -> listOf(book.title)
            "artist", "artists", "작가" -> book.metadata.artists
            "tag", "tags", "태그" -> book.metadata.tags
            "series", "시리즈" -> book.metadata.series + listOfNotNull(customSeriesName)
            "group", "groups", "그룹" -> book.metadata.groups
            "character", "characters", "캐릭터" -> book.metadata.characters
            "language", "lang", "언어" -> listOfNotNull(book.metadata.language)
            "type", "종류" -> listOfNotNull(book.metadata.galleryType)
            "id" -> listOfNotNull(book.metadata.hitomiId, book.id)
            else -> listOf(book.title) + book.metadata.artists + book.metadata.tags +
                book.metadata.series + listOfNotNull(customSeriesName) +
                book.metadata.groups + book.metadata.characters +
                listOfNotNull(book.metadata.language, book.metadata.galleryType)
        }
        val found = candidates.any { it.contains(value, ignoreCase = true) }
        return if (excluded) !found else found
    }
}

private fun parseLibraryQuery(query: String): List<LibraryQueryTerm> {
    val tokenRegex = Regex("""(-?)(?:(\w+|[가-힣]+):)?(?:\"([^\"]+)\"|(\S+))""")
    return tokenRegex.findAll(query.trim()).mapNotNull { match ->
        val value = match.groupValues[3].ifBlank { match.groupValues[4] }.trim()
        value.takeIf(String::isNotEmpty)?.let {
            LibraryQueryTerm(
                field = match.groupValues[2].ifBlank { null }?.lowercase(),
                value = it,
                excluded = match.groupValues[1] == "-",
            )
        }
    }.toList()
}
