package com.doujinmenu.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort

private enum class LibraryViewMode { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    var viewModeName by rememberSaveable { mutableStateOf(LibraryViewMode.GRID.name) }
    var gridColumns by rememberSaveable { mutableIntStateOf(2) }
    val viewMode = LibraryViewMode.valueOf(viewModeName)
    val books = remember(
        state.libraryBooks,
        state.libraryQuery,
        state.libraryFavoritesOnly,
        state.libraryReadFilter,
        state.librarySort,
        state.selectedLibraryLocationUri,
        state.libraryFavoriteIds,
        state.libraryReadIds,
    ) { visibleLibraryBooks(state) }

    PullToRefreshBox(
        isRefreshing = state.isLibraryScanning,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            LibraryToolbar(
                state = state,
                onToggleFavoritesFilter = onToggleFavoritesFilter,
                onReadFilterChange = onReadFilterChange,
                onSortChange = onSortChange,
                onLocationChange = onLocationChange,
                viewMode = viewMode,
                gridColumns = gridColumns,
                onViewModeChange = { viewModeName = it.name },
                onGridColumnsChange = { gridColumns = it },
            )

            when {
                state.isLibraryScanning && state.libraryBooks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.libraryLocations.isEmpty() && state.desktopLibraryLocations.isEmpty() -> LibraryEmpty(
                    "설정에서 스캔할 라이브러리 폴더를 먼저 추가하세요.",
                )
                books.isEmpty() -> LibraryEmpty("조건에 맞는 책이 없습니다.")
                viewMode == LibraryViewMode.GRID -> LibraryGrid(
                    books = books,
                    columns = gridColumns,
                    state = state,
                    onOpenBook = onOpenBook,
                    onToggleFavorite = onToggleFavorite,
                    onToggleRead = onToggleRead,
                )
                else -> LibraryList(
                    books = books,
                    state = state,
                    onOpenBook = onOpenBook,
                    onToggleFavorite = onToggleFavorite,
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
        }
    }
}

@Composable
private fun LibraryToolbar(
    state: MainUiState,
    onToggleFavoritesFilter: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String?) -> Unit,
    viewMode: LibraryViewMode,
    gridColumns: Int,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterMenu(
            label = state.selectedLibraryLocationUri?.let { selected ->
                (state.libraryLocations + state.desktopLibraryLocations)
                    .firstOrNull { it.uri == selected }?.displayName
            } ?: "라이브러리",
            options = listOf(null to "전체 라이브러리") +
                (state.libraryLocations + state.desktopLibraryLocations)
                    .map { it.uri to (if (it.isCloud) "☁ ${it.displayName}" else "기기 · ${it.displayName}") },
            onSelect = onLocationChange,
        )
        LibraryFilterMenu(
            favoritesOnly = state.libraryFavoritesOnly,
            readFilter = state.libraryReadFilter,
            onToggleFavorites = onToggleFavoritesFilter,
            onReadFilterChange = onReadFilterChange,
        )
        LibrarySortMenu(sort = state.librarySort, onSortChange = onSortChange)
        OutlinedButton(onClick = { onSortChange(state.librarySort.reversed()) }) {
            Text(if (state.librarySort.isAscending()) "↑" else "↓")
        }
        LibraryViewMenu(
            viewMode = viewMode,
            gridColumns = gridColumns,
            onViewModeChange = onViewModeChange,
            onGridColumnsChange = onGridColumnsChange,
        )
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
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (favoritesOnly || readFilter != LibraryReadFilter.ALL) "필터 •" else "필터")
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
private fun LibrarySortMenu(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val titleSelected = sort == LibrarySort.TITLE_ASC || sort == LibrarySort.TITLE_DESC
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (titleSelected) "정렬 · 제목" else "정렬 · 수정일")
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
}

@Composable
private fun LibraryViewMenu(
    viewMode: LibraryViewMode,
    gridColumns: Int,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("⋯") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun LibraryGrid(
    books: List<LibraryBook>,
    columns: Int,
    state: MainUiState,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
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
                onOpen = { onOpenBook(book.id) },
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
                onOpen = { onOpenBook(book.id) },
                onToggleFavorite = { onToggleFavorite(book.id) },
                onToggleRead = { onToggleRead(book.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
    book: LibraryBook,
    favorite: Boolean,
    read: Boolean,
    progress: Int,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onToggleRead)) {
        Box {
            AsyncImage(
                model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                contentDescription = "${book.title} 표지",
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            LibraryThumbnailBadges(
                read = read,
                isCloud = book.isCloud,
                favorite = favorite,
                onToggleFavorite = onToggleFavorite,
            )
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
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().height(132.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onToggleRead),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(94.dp).fillMaxHeight()) {
                AsyncImage(
                    model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                    contentDescription = "${book.title} 표지",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                LibraryThumbnailBadges(
                    read = read,
                    isCloud = book.isCloud,
                    favorite = favorite,
                    onToggleFavorite = onToggleFavorite,
                )
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
    ThumbnailBadge(if (isCloud) "☁" else "기기", Modifier.align(Alignment.TopEnd))
    TextButton(
        onClick = onToggleFavorite,
        modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f)),
    ) { Text(if (favorite) "♥" else "♡", color = Color.White) }
}

@Composable
private fun ThumbnailBadge(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier.background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun LibraryBookText(book: LibraryBook, progress: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        .filter { state.selectedLibraryLocationUri == null || it.locationUri == state.selectedLibraryLocationUri }
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
