package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

private enum class LibraryViewMode { GRID, LIST }

private sealed interface LibraryMainEntry {
    val title: String
    data class Book(val book: LibraryBook) : LibraryMainEntry { override val title: String = book.title }
    data class Series(val name: String, val books: List<LibraryBook>) : LibraryMainEntry {
        override val title: String = name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
    onAddFavorites: (Set<String>) -> Unit,
    onMarkRead: (Set<String>) -> Unit,
    onMarkUnread: (Set<String>) -> Unit,
    onAssignSeries: (Set<String>, String) -> Unit,
    onMoveSeriesBook: (String, Int) -> Unit,
    onDeleteBooks: (Set<String>) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onRemoveBooksFromSeries: (Set<String>) -> Unit,
    onRenameSeries: (String, String) -> Unit,
    onDeleteSeries: (Set<String>) -> Unit,
    onSeriesModeChange: (Boolean) -> Unit,
    onSelectedSeriesChange: (String?) -> Unit,
    onBackToSearch: () -> Unit,
) {
    var viewModeName by rememberSaveable { mutableStateOf(LibraryViewMode.GRID.name) }
    var gridColumns by rememberSaveable { mutableIntStateOf(2) }
    val seriesMode = state.librarySeriesMode
    val selectedSeries = state.selectedLibrarySeries
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedSeriesNames by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var seriesSelectionMode by rememberSaveable { mutableStateOf(false) }
    var seriesDialog by rememberSaveable { mutableStateOf(false) }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var editingSeriesOrder by rememberSaveable { mutableStateOf(false) }
    var addingToSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSeriesSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var renameBookDialog by rememberSaveable { mutableStateOf(false) }
    var renameSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var renameInput by rememberSaveable { mutableStateOf("") }
    val viewMode = LibraryViewMode.valueOf(viewModeName)
    val books = remember(
        state.libraryBooks,
        state.libraryQuery,
        state.libraryFavoritesOnly,
        state.libraryReadFilter,
        state.librarySort,
        state.selectedLibraryLocationUri,
        state.selectedLibraryLocationUris,
        state.libraryFavoriteIds,
        state.libraryReadIds,
    ) { visibleLibraryBooks(state) }
    val shownBooks = books.filterBySeries(selectedSeries, state.customSeriesByBookId)
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()

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
            }
            editingSeriesOrder -> editingSeriesOrder = false
            selectedSeries != null -> onSelectedSeriesChange(null)
            seriesMode -> onSeriesModeChange(false)
            else -> onBackToSearch()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .pullToRefresh(
                isRefreshing = state.isLibraryScanning,
                state = pullToRefreshState,
                enabled = !editingSeriesOrder,
                onRefresh = onRefresh,
            ),
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
                        selectedSeriesNames = if (selectedSeriesNames.containsAll(all)) emptySet() else all
                    },
                    onFavorite = null,
                    onRead = null,
                    onSeries = null,
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
                    },
                )
            } else if (selectionMode) {
                val allSelectedRead = selectedIds.isNotEmpty() && selectedIds.all { it in state.libraryReadIds }
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
                    onViewModeChange = { viewModeName = it.name },
                    onGridColumnsChange = { gridColumns = it },
                )
            }
            if (seriesMode && selectedSeries != null && !selectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = {
                        onSelectedSeriesChange(null)
                        editingSeriesOrder = false
                    }) { Text("< ${selectedSeries.orEmpty()}") }
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
                    customSeries = state.customSeriesByBookId,
                    viewMode = viewMode,
                    columns = gridColumns,
                    selectionMode = seriesSelectionMode,
                    selectedSeries = selectedSeriesNames,
                    onOpenSeries = onSelectedSeriesChange,
                    onToggleSelection = { name -> selectedSeriesNames = selectedSeriesNames.toggled(name) },
                    onLongPress = { name ->
                        context.performLightHaptic()
                        seriesSelectionMode = true
                        selectedSeriesNames = selectedSeriesNames + name
                    },
                )
                editingSeriesOrder && selectedSeries != null -> LibrarySeriesOrderEditor(
                    books = shownBooks,
                    customSeries = state.customSeriesByBookId,
                    onMove = onMoveSeriesBook,
                )
                !seriesMode && viewMode == LibraryViewMode.GRID -> LibraryMixedGrid(
                    books = books,
                    state = state,
                    columns = gridColumns,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = { name ->
                        onSeriesModeChange(true)
                        onSelectedSeriesChange(name)
                    },
                    onToggleBook = { id -> selectedIds = selectedIds.toggled(id) },
                    onToggleSeries = { name, ids ->
                        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids
                        else selectedIds.also { pendingSeriesSelection = name }
                    },
                    onLongPressBook = { id ->
                        context.performLightHaptic()
                        if (!selectionMode) {
                            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                        }
                        selectionMode = true
                        selectedIds = selectedIds + id
                    },
                    onLongPressSeries = { name, firstTitle ->
                        context.performLightHaptic()
                        if (!selectionMode) seriesName = firstTitle
                        selectionMode = true
                        pendingSeriesSelection = name
                    },
                    onToggleFavorite = onToggleFavorite,
                    onToggleRead = onToggleRead,
                )
                !seriesMode -> LibraryMixedList(
                    books = books,
                    state = state,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = { name ->
                        onSeriesModeChange(true)
                        onSelectedSeriesChange(name)
                    },
                    onToggleBook = { id -> selectedIds = selectedIds.toggled(id) },
                    onToggleSeries = { name, ids ->
                        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids
                        else selectedIds.also { pendingSeriesSelection = name }
                    },
                    onLongPressBook = { id ->
                        context.performLightHaptic()
                        if (!selectionMode) {
                            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                        }
                        selectionMode = true
                        selectedIds = selectedIds + id
                    },
                    onLongPressSeries = { name, firstTitle ->
                        context.performLightHaptic()
                        if (!selectionMode) seriesName = firstTitle
                        selectionMode = true
                        pendingSeriesSelection = name
                    },
                    onToggleFavorite = onToggleFavorite,
                    onToggleRead = onToggleRead,
                )
                viewMode == LibraryViewMode.GRID -> LibraryGrid(
                    books = shownBooks,
                    columns = gridColumns,
                    state = state,
                    onOpenBook = onOpenBook,
                    onToggleFavorite = onToggleFavorite,
                    onToggleRead = onToggleRead,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onToggleSelection = { id -> selectedIds = selectedIds.toggled(id) },
                    onLongPress = { id ->
                        context.performLightHaptic()
                        if (!selectionMode) {
                            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                        }
                        selectionMode = true
                        selectedIds = selectedIds + id
                    },
                )
                else -> LibraryList(
                    books = shownBooks,
                    state = state,
                    onOpenBook = onOpenBook,
                    onToggleFavorite = onToggleFavorite,
                    onToggleRead = onToggleRead,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onToggleSelection = { id -> selectedIds = selectedIds.toggled(id) },
                    onLongPress = { id ->
                        context.performLightHaptic()
                        if (!selectionMode) {
                            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                        }
                        selectionMode = true
                        selectedIds = selectedIds + id
                    },
                )
            }
            state.libraryScanError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = !editingSeriesOrder,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PullToRefreshDefaults.Indicator(
                isRefreshing = state.isLibraryScanning,
                state = pullToRefreshState,
            )
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
                    else -> "선택한 ${selectedIds.size}개 항목을 갤러리 목록에서 삭제하시겠습니까? 원본 파일은 유지됩니다."
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
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String) -> Unit,
    viewMode: LibraryViewMode,
    gridColumns: Int,
    seriesMode: Boolean,
    onSeriesModeChange: (Boolean) -> Unit,
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
        } else {
            OutlinedButton(onClick = { onSeriesModeChange(true) }) {
                SeriesArchiveIcon()
                Text("시리즈", modifier = Modifier.padding(start = 7.dp))
            }
        }
        LibraryFilterMenu(
            favoritesOnly = state.libraryFavoritesOnly,
            readFilter = state.libraryReadFilter,
            onToggleFavorites = onToggleFavoritesFilter,
            onReadFilterChange = onReadFilterChange,
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
    onSave: (() -> Unit)? = null,
    readLabel: String = "읽음",
    seriesLabel: String = "시리즈",
    onRename: (() -> Unit)?,
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
        onSave?.let { action -> TextButton(onClick = action) { Text("저장") } }
        onRename?.let { action -> TextButton(onClick = action) { Text("이름변경") } }
        onDelete?.let { action -> TextButton(onClick = action, enabled = selectedCount > 0) { Text("삭제") } }
        TextButton(onClick = onClose) { Text("취소") }
    }
}

@Composable
private fun LibraryFilterMenu(
    favoritesOnly: Boolean,
    readFilter: LibraryReadFilter,
    onToggleFavorites: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = favoritesOnly || readFilter != LibraryReadFilter.ALL
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
        }
    }
}

@Composable
private fun LibrarySortControl(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val titleSelected = sort == LibrarySort.TITLE_ASC || sort == LibrarySort.TITLE_DESC
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
                    text = { Text(if (!titleSelected) "✓ 수정일" else "수정일") },
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
            (2..4).forEach { columns ->
                DropdownMenuItem(
                    text = { Text(if (gridColumns == columns) "✓ 한 줄에 ${columns}개" else "한 줄에 ${columns}개") },
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
    books: List<LibraryBook>,
    columns: Int,
    state: MainUiState,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = LibraryBook::id) { book ->
            LibraryBookCard(
                book = book,
                favorite = book.id in state.libraryFavoriteIds,
                read = book.id in state.libraryReadIds,
                progress = state.libraryProgress[book.id] ?: 0,
                selected = book.id in selectedIds,
                selectionMode = selectionMode,
                onOpen = {
                    if (selectionMode) onToggleSelection(book.id) else onOpenBook(book.id)
                },
                onLongPress = { onLongPress(book.id) },
                onToggleFavorite = { onToggleFavorite(book.id) },
                onToggleRead = { onToggleRead(book.id) },
            )
        }
    }
}

@Composable
private fun LibraryList(
    books: List<LibraryBook>,
    state: MainUiState,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listItems(books, key = LibraryBook::id) { book ->
            LibraryBookListItem(
                book = book,
                favorite = book.id in state.libraryFavoriteIds,
                read = book.id in state.libraryReadIds,
                progress = state.libraryProgress[book.id] ?: 0,
                selected = book.id in selectedIds,
                selectionMode = selectionMode,
                onOpen = {
                    if (selectionMode) onToggleSelection(book.id) else onOpenBook(book.id)
                },
                onLongPress = { onLongPress(book.id) },
                onToggleFavorite = { onToggleFavorite(book.id) },
                onToggleRead = { onToggleRead(book.id) },
            )
        }
    }
}

@Composable
private fun LibraryMixedGrid(
    books: List<LibraryBook>,
    state: MainUiState,
    columns: Int,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    val entries = remember(books, state.libraryBooks, state.customSeriesByBookId) {
        libraryMainEntries(books, state.libraryBooks, state.customSeriesByBookId)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
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
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryMixedList(
    books: List<LibraryBook>,
    state: MainUiState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    val entries = remember(books, state.libraryBooks, state.customSeriesByBookId) {
        libraryMainEntries(books, state.libraryBooks, state.customSeriesByBookId)
    }
    LazyColumn(
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
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
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
    return (independent + series).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
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
                )
            }
            if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center))
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
                    )
                }
                if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center))
            }
            LibraryBookText(book, progress, Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun BoxScope.LibraryThumbnailBadges(
    read: Boolean,
    isCloud: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    if (read) {
        ThumbnailBadge("읽음", Modifier.align(Alignment.TopStart))
    }
    if (isCloud) {
        ThumbnailIconBadge(Modifier.align(Alignment.TopEnd)) { CloudOutlineIcon() }
    } else {
        ThumbnailBadge("기기", Modifier.align(Alignment.TopEnd))
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(42.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            FavoriteHeartIcon(favorite = favorite, modifier = Modifier.size(22.dp))
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
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Box(
            modifier = Modifier.width(35.dp).height(27.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun CloudOutlineIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(18.dp)) {
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
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(42.dp).border(
            width = 3.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            shape = CircleShape,
        ),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Text("✓", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun selectionColorFilter(enabled: Boolean): ColorFilter? = if (enabled) {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
} else null

@Composable
private fun ThumbnailBadge(text: String, modifier: Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
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
    customSeries: Map<String, CustomSeriesAssignment>,
    viewMode: LibraryViewMode,
    columns: Int,
    selectionMode: Boolean,
    selectedSeries: Set<String>,
    onOpenSeries: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val groups = remember(books, customSeries) {
        books.flatMap { book -> book.effectiveSeries(customSeries).map { it to book } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (seriesName, seriesBooks) ->
                seriesBooks.sortedWith(
                    compareBy<LibraryBook> {
                        customSeries[it.id]?.takeIf { assignment -> assignment.name == seriesName }?.order
                            ?: Int.MAX_VALUE
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                )
            }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
    }
    if (groups.isEmpty()) {
        LibraryEmpty("등록된 시리즈가 없습니다. 항목을 길게 눌러 시리즈로 묶어보세요.")
        return
    }
    if (viewMode == LibraryViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
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
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listItems(groups, key = { it.first }) { (name, seriesBooks) ->
                SeriesCard(
                    name = name,
                    books = seriesBooks,
                    listMode = true,
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
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
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
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
                    context = context,
                    selectionMode = selectionMode,
                    selected = selected,
                    modifier = Modifier.width(100.dp).fillMaxHeight(),
                )
                SeriesText(name, books, Modifier.padding(14.dp))
            }
        } else {
            Column {
                SeriesCover(
                    book = cover,
                    context = context,
                    selectionMode = selectionMode,
                    selected = selected,
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
    context: android.content.Context,
    selectionMode: Boolean,
    selected: Boolean,
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
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                SeriesStackIcon(Modifier.padding(6.dp).size(20.dp))
            }
        }
        if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center))
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
    Column(modifier = modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(
            "${books.size}화 · ${books.take(3).joinToString { it.title }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySeriesOrderEditor(
    books: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    onMove: (String, Int) -> Unit,
) {
    val context = LocalContext.current
    val orderedBooks = books.sortedWith(
        compareBy<LibraryBook> { customSeries[it.id]?.order ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
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
            val currentIndex by rememberUpdatedState(index)
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
                        val reorderThreshold = 56.dp.toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accumulatedDrag = 0f
                                dragging = true
                                context.performLightHaptic()
                            },
                            onDragEnd = {
                                dragging = false
                            },
                            onDragCancel = {
                                dragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount.y
                                when {
                                    accumulatedDrag > reorderThreshold && currentIndex < orderedBooks.lastIndex -> {
                                        context.performLightHaptic()
                                        onMove(book.id, 1)
                                        accumulatedDrag -= reorderThreshold
                                    }
                                    accumulatedDrag < -reorderThreshold && currentIndex > 0 -> {
                                        context.performLightHaptic()
                                        onMove(book.id, -1)
                                        accumulatedDrag += reorderThreshold
                                    }
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
    this == LibrarySort.TITLE_ASC || this == LibrarySort.OLDEST

private fun LibrarySort.reversed(): LibrarySort = when (this) {
    LibrarySort.TITLE_ASC -> LibrarySort.TITLE_DESC
    LibrarySort.TITLE_DESC -> LibrarySort.TITLE_ASC
    LibrarySort.NEWEST -> LibrarySort.OLDEST
    LibrarySort.OLDEST -> LibrarySort.NEWEST
}

internal fun visibleLibraryBooks(state: MainUiState): List<LibraryBook> {
    val queryTerms = parseLibraryQuery(state.libraryQuery)
    val filtered = state.libraryBooks.asSequence()
        .filter {
            when {
                state.selectedLibraryLocationUris != null -> it.locationUri in state.selectedLibraryLocationUris
                state.selectedLibraryLocationUri != null -> it.locationUri == state.selectedLibraryLocationUri
                else -> true
            }
        }
        .filter { book -> queryTerms.all { it.matches(book) } }
        .filter { !state.libraryFavoritesOnly || it.id in state.libraryFavoriteIds }
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
        LibrarySort.NEWEST -> filtered.sortedByDescending(LibraryBook::modifiedAt)
        LibrarySort.OLDEST -> filtered.sortedBy(LibraryBook::modifiedAt)
    }
}

private data class LibraryQueryTerm(val field: String?, val value: String, val excluded: Boolean) {
    fun matches(book: LibraryBook): Boolean {
        val candidates = when (field) {
            "title", "제목" -> listOf(book.title)
            "artist", "artists", "작가" -> book.metadata.artists
            "tag", "tags", "태그" -> book.metadata.tags
            "series", "시리즈" -> book.metadata.series
            "group", "groups", "그룹" -> book.metadata.groups
            "character", "characters", "캐릭터" -> book.metadata.characters
            "language", "lang", "언어" -> listOfNotNull(book.metadata.language)
            "type", "종류" -> listOfNotNull(book.metadata.galleryType)
            "id" -> listOfNotNull(book.metadata.hitomiId, book.id)
            else -> listOf(book.title) + book.metadata.artists + book.metadata.tags +
                book.metadata.series + book.metadata.groups + book.metadata.characters +
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
