package com.doujinmenu.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import com.doujinmenu.android.data.SearchResultIndex
import kotlin.math.roundToInt
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.SearchFavorite
import com.doujinmenu.android.model.SearchQueryEntry
import com.doujinmenu.android.model.resolveScrollIndex
import com.doujinmenu.android.model.isSearchPageInRange
import com.doujinmenu.android.network.parseHitomiSearchOrder
import com.doujinmenu.android.network.FilterSuggestion
import com.doujinmenu.android.network.AvailableLanguage
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: MainUiState,
    tabHeader: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFavoriteNameChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSaveFavorite: () -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onFavoriteSearch: (SearchFavorite) -> Unit,
    onRecentSearch: (SearchQueryEntry) -> Unit,
    onClearSearchHistory: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onLoadLanguages: () -> Unit,
    onSelectAnyLanguage: () -> Unit,
    onRemoveCustomLanguage: (String) -> Unit,
    onSelectSuggestion: (FilterSuggestion) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPreviousPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onSearchFacet: (String) -> Unit,
    onToggleLibraryFavorite: (String) -> Unit,
    onConnect: () -> Unit,
    pageKey: String,
    initialScrollAnchorKey: String?,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChange: (String?, Int, Int, Int) -> Unit,
    onEnsureResults: () -> Unit,
    onVisibleResultsChange: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var expandedPanel by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryBookByGalleryId = remember(state.libraryBooks, state.libraryHiddenIds) {
        val visibleBooks = state.libraryBooks.filterNot { it.id in state.libraryHiddenIds }
        visibleBooks.mapNotNull { it.metadata.hitomiId?.toLongOrNull() }
            .distinct()
            .associateWith { preferredLibraryBookForGalleryId(visibleBooks, it) }
    }
    val resultIds = state.searchResultWindow?.index?.let { index -> remember(index) { index.ids } }
        ?: remember(state.galleries) { state.galleries.map { it.id } }
    val galleryPositions = remember(resultIds) { resultIds.withIndex().associate { "gallery:${it.value}" to it.index } }
    val totalCount = state.searchResultWindow?.index?.totalCount
    val maxPage = state.searchResultWindow?.index?.maxPage
    val galleriesById = remember(state.galleries) { state.galleries.associateBy { it.id } }
    val canRestoreResults = state.searchResultWindow == null && state.currentPage > 0 &&
        !state.isLoadingPage && !state.isRestoringSearch
    val contentKeys = remember(resultIds, totalCount, state.isLoadingPage, state.currentPage, state.hasNextPage, canRestoreResults) { buildList {
        add("search-controls")
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) add("empty-results")
        if (state.isLoadingPage && resultIds.isEmpty()) add("initial-loader")
        if (canRestoreResults) add("restore-results")
        if (resultIds.isNotEmpty() || totalCount != null) {
            add("result-count")
            resultIds.forEach { add("gallery:$it") }
        }
        if (resultIds.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            add("page-loader-${state.currentPage}")
        }
    } }
    LaunchedEffect(pageKey) { onEnsureResults() }
    val savedAnchorKey = remember(pageKey) { initialScrollAnchorKey }
    val restoredIndex = resolveScrollIndex(savedAnchorKey, initialScrollIndex, contentKeys)
    val listState = remember(pageKey) {
        LazyGridState(
            firstVisibleItemIndex = restoredIndex,
            firstVisibleItemScrollOffset = initialScrollOffset,
        )
    }
    var scrollRestored by remember(pageKey) {
        mutableStateOf(savedAnchorKey == null || savedAnchorKey in contentKeys)
    }
    LaunchedEffect(pageKey, contentKeys, state.isLoadingPage, state.isRestoringSearch) {
        if (!scrollRestored) {
            val anchorIndex = contentKeys.indexOf(savedAnchorKey)
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex, initialScrollOffset)
                scrollRestored = true
            } else if (!state.isLoadingPage && !state.isRestoringSearch && resultIds.isNotEmpty()) {
                listState.scrollToItem(resolveScrollIndex(null, initialScrollIndex, contentKeys))
                scrollRestored = true
            }
        }
    }
    fun persistScroll() {
        if (state.searchResultWindow?.index?.pages.isNullOrEmpty()) return
        val index = listState.firstVisibleItemIndex
        val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString()
        onScrollChange(anchor, index, listState.firstVisibleItemScrollOffset, visibleSearchPage(index, state.searchResultWindow.index))
    }
    // Keep per-frame list offsets out of MainUiState; save when the gesture settles.
    LaunchedEffect(pageKey, listState, scrollRestored) {
        if (!scrollRestored) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect { persistScroll() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(pageKey, listState, scrollRestored, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && scrollRestored) persistScroll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (scrollRestored && listState.layoutInfo.totalItemsCount > 0) persistScroll()
        }
    }
    LaunchedEffect(pageKey, listState, scrollRestored, resultIds.size) {
        if (!scrollRestored || resultIds.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo.filter { it.key.toString().startsWith("gallery:") }
            val first = visible.firstOrNull()?.key?.toString()?.let(galleryPositions::get) ?: 0
            val last = visible.lastOrNull()?.key?.toString()?.let(galleryPositions::get) ?: first
            Triple(first, last, listState.isScrollInProgress)
        }.distinctUntilChanged().collect { (first, last, scrolling) ->
            onVisibleResultsChange(first, last)
            val index = state.searchResultWindow?.index
            if (scrolling && index != null && index.firstPage > 1 && index.pageAt(first) == index.firstPage) onLoadPreviousPage()
        }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editing, expandedPanel) {
        if (editing && expandedPanel == "languages") onLoadLanguages()
    }
    val focusManager = LocalFocusManager.current
    fun submitSearch() {
        focusManager.clearFocus()
        editing = false
        expandedPanel = null
        onSearch()
    }
    fun togglePanel(name: String) {
        val close = editing && expandedPanel == name
        expandedPanel = if (close) null else name
        editing = !close
        focusManager.clearFocus()
    }
    BackHandler(editing) { editing = false; focusManager.clearFocus() }
    var menu by remember { mutableStateOf<String?>(null) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var pageDialog by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable { mutableStateOf("") }
    val canCollapse = resultIds.isNotEmpty() && !editing && menu == null && !pageDialog
    val collapseEnabled by rememberUpdatedState(canCollapse)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { collapseEnabled })
    LaunchedEffect(pageKey, canCollapse) {
        if (!canCollapse) scrollBehavior.state.heightOffset = 0f
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        CollapsingSearchToolbar(scrollBehavior) {
        Column {
        tabHeader({
            UnifiedSearchField(state.searchQuery, { expandedPanel = null; onQueryChange(it) }, "검색 조건", { editing = true; expandedPanel = "history" }, { submitSearch() })
        }, {
            Box {
                IconButton(onClick = { menu = "root" }, modifier = Modifier.semantics { contentDescription = "검색 옵션" }) { Text("⋯") }
                DropdownMenu(expanded = menu != null, onDismissRequest = { menu = null }) {
                    if (menu == "view") {
                        DropdownMenuItem(text = { Text("검색 옵션") }, leadingIcon = { ActionIcon(ActionSymbol.BACK) }, onClick = { menu = "root" })
                        ChoiceMenuItem("리스트 보기", !gridView, onClick = { gridView = false; menu = null })
                        ChoiceMenuItem("그리드 보기", gridView, onClick = { gridView = true; menu = null })
                    } else {
                        DropdownMenuItem(text = { Text("이 검색 즐겨찾기 등록") }, leadingIcon = { ActionIcon(ActionSymbol.STAR) }, enabled = state.searchQuery.isNotBlank(), onClick = { menu = null; editing = true; expandedPanel = "favorites" })
                        DropdownMenuItem(text = { Text("페이지 이동") }, leadingIcon = { ActionIcon(ActionSymbol.PAGE) }, enabled = resultIds.isNotEmpty(), onClick = { menu = null; pageDialog = true })
                        DropdownMenuItem(text = { Text("보기  ›") }, leadingIcon = { ActionIcon(ActionSymbol.GRID) }, onClick = { menu = "view" })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("새로고침") }, leadingIcon = { ActionIcon(ActionSymbol.REFRESH) }, onClick = { menu = null; onRefresh() })
                        DropdownMenuItem(text = { Text("검색어 지우기") }, leadingIcon = { ActionIcon(ActionSymbol.CLEAR) }, onClick = { onQueryChange(""); menu = null; editing = true })
                    }
                }
            }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { togglePanel("languages") }, contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (editing && expandedPanel == "languages") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                ActionIcon(ActionSymbol.LANGUAGE); Spacer(Modifier.width(4.dp))
                val selected = state.preferredLanguages.singleOrNull()
                Text(selected?.let { code -> state.availableLanguages.firstOrNull { it.code == code }?.label
                    ?: mapOf("korean" to "한국어", "japanese" to "日本語", "english" to "English", "chinese" to "中文")[code]
                    ?: code.replace('_', ' ') } ?: "언어")
            }
            OutlinedButton(onClick = { togglePanel("favorites") }, contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (editing && expandedPanel == "favorites") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                ActionIcon(ActionSymbol.STAR); Spacer(Modifier.width(4.dp)); Text("즐겨찾기")
            }
            Spacer(Modifier.weight(1f))
            SearchSortMenu(state.searchQuery, enabled = !state.isLoadingPage) { oldest ->
                val query = state.searchQuery.replace(Regex("(?i)(^|\\s)sort:(oldest|newest)(?=\\s|$)"), " ").trim()
                onQueryChange(listOf(query, if (oldest) "sort:oldest" else "sort:newest").filter { it.isNotBlank() }.joinToString(" "))
                onSearch()
            }
        }
        AnimatedVisibility(visible = editing && (expandedPanel != null || state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions), enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val editorScroll = rememberScrollState()
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)
                .then(if (expandedPanel == "languages" || expandedPanel == "history") Modifier else Modifier.verticalScroll(editorScroll)).padding(12.dp)) {
                if (expandedPanel == "history") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("최근 검색기록", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = onClearSearchHistory, enabled = state.searchQueryHistory.isNotEmpty()) { Text("기록 삭제") }
                    }
                    if (state.searchQueryHistory.isEmpty()) Text("최근 검색기록이 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(state.searchQueryHistory) { entry ->
                            TextButton(onClick = {
                                focusManager.clearFocus(); editing = false; expandedPanel = null; onRecentSearch(entry)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(entry.query.ifBlank { "전체 갤러리" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.languages.sorted().joinToString().ifBlank { "Any" }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (expandedPanel == null && (state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions)) {
                    state.filterSuggestions.forEach { suggestion ->
                        TextButton(onClick = { onSelectSuggestion(suggestion) }) { Text(suggestion.token) }
                    }
                    if (state.isLoadingFilterSuggestions) Text("필터 목록 불러오는 중…")
                }

                    if (expandedPanel == "languages") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("언어", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { submitSearch() }, enabled = !state.isLoadingPage,
                                modifier = Modifier.semantics { contentDescription = "언어 선택 확인 및 검색" }) {
                                ActionIcon(ActionSymbol.SEARCH)
                                Spacer(Modifier.width(4.dp))
                                Text("확인")
                            }
                        }
                        Text(
                            if (state.preferredLanguages.isEmpty())
                                "Any: 모든 언어에서 검색합니다."
                            else "선택한 언어를 각각 검색해 결과를 합칩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("전체 ${state.availableLanguages.size}개 · ${state.preferredLanguages.size}개 선택 (복수 선택)", style = MaterialTheme.typography.labelMedium)
                        if (state.isLoadingLanguages) LoadingRow("전체 언어 목록 불러오는 중…")
                        state.languageLoadError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onLoadLanguages) { Text("다시 시도") }
                        }
                        val languages = remember(state.availableLanguages, state.customLanguages, state.preferredLanguages) {
                            val known = state.availableLanguages.mapTo(hashSetOf()) { it.code }
                            state.availableLanguages + (state.customLanguages + state.preferredLanguages - known).sorted().map {
                                AvailableLanguage(it, it.replace('_', ' ').replaceFirstChar { char -> char.uppercase() })
                            }
                        }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            item(key = "any") {
                                Row(Modifier.fillMaxWidth().toggleable(value = state.preferredLanguages.isEmpty(), role = Role.Checkbox,
                                    onValueChange = { onSelectAnyLanguage() }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = state.preferredLanguages.isEmpty(), onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                    Text("Any · 모든 언어", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            items(languages, key = { it.code }) { language ->
                                val selected = language.code in state.preferredLanguages
                                Row(Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Checkbox,
                                    onValueChange = { onToggleLanguage(language.code) }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                        Text(language.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(language.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (language.code in state.customLanguages) TextButton(onClick = { deleteLanguage = language.code }) { Text("삭제") }
                                }
                            }
                        }
                    }

                    if (expandedPanel == "favorites") {
                        Text("검색 즐겨찾기", style = MaterialTheme.typography.titleSmall)
                        state.searchFavorites.forEach { favorite ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        expandedPanel = null
                                        editing = false
                                        focusManager.clearFocus()
                                        onFavoriteSearch(favorite)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(favorite.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            favorite.query,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                TextButton(onClick = { onRemoveFavorite(favorite.id) }) {
                                    Text("삭제")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.favoriteName,
                            onValueChange = onFavoriteNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("현재 조건의 즐겨찾기 이름") },
                            singleLine = true,
                        )
                        Button(
                            onClick = onSaveFavorite,
                            enabled = state.searchQuery.isNotBlank(),
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("현재 조건 저장") }
                    }
            }
            }
        }
        }
        }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (gridView) 2 else 1),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "search-controls", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(0.dp)) }
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) {
            item(key = "empty-results", span = { GridItemSpan(maxLineSpan) }) {
                Text(if (state.currentPage > 0) "검색 결과가 없습니다. 검색 조건을 바꿔 보세요." else "상단 검색창에서 검색어를 입력하세요. 언어와 즐겨찾기도 선택할 수 있습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.isLoadingPage && resultIds.isEmpty()) {
            item(key = "initial-loader", span = { GridItemSpan(maxLineSpan) }) { LoadingRow("불러오는 중…") }
        }
        if (canRestoreResults) {
            item(key = "restore-results", span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onEnsureResults, modifier = Modifier.fillMaxWidth()) { Text("검색 결과 다시 불러오기") }
            }
        }

        if (resultIds.isNotEmpty() || totalCount != null) {
            item(key = "result-count", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    totalCount?.let { "검색결과 ${java.text.NumberFormat.getIntegerInstance().format(it)}개" } ?: "검색결과 · 전체 건수 미확인",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(count = resultIds.size, key = { "gallery:${resultIds[it]}" }, contentType = { "gallery" }) { index ->
                val id = resultIds[index]
                val gallery = galleriesById[id]
                if (gallery == null) {
                    Card(Modifier.fillMaxWidth().height(if (gridView) 280.dp else 168.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("#$id · 불러오는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val libraryBook = libraryBookByGalleryId[gallery.id]
                    GalleryCard(
                        gallery = gallery,
                        gridView = gridView,
                        read = gallery.id in state.viewedGalleryIds || libraryBook?.id in state.libraryReadIds,
                        libraryBook = libraryBook,
                        favorite = libraryBook?.id?.let { it in state.libraryFavoriteIds } == true,
                        onToggleFavorite = { libraryBook?.id?.let(onToggleLibraryFavorite) },
                        onClick = { onGalleryClick(gallery.id) },
                        onLongClick = { onGalleryLongClick(gallery.id) },
                        onSearchFacet = onSearchFacet,
                    )
                }
            }
        }

        if (resultIds.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            item(key = "page-loader-${state.currentPage}", span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(state.currentPage, state.hasNextPage, state.isLoadingPage, state.isRestoringSearch, state.nextSearchPageError, listState.isScrollInProgress) {
                    if (listState.isScrollInProgress && state.hasNextPage && !state.isLoadingPage && !state.isRestoringSearch && state.nextSearchPageError == null) onLoadNextPage()
                }
                if (state.nextSearchPageError != null) {
                    TextButton(onClick = onLoadNextPage, modifier = Modifier.fillMaxWidth()) { Text("다음 페이지 다시 불러오기") }
                } else LoadingRow(
                    if (state.isLoadingPage) "${state.currentPage + 1}페이지 불러오는 중…"
                    else "다음 페이지 준비 중…",
                )
            }
        }
        }
        DynamicVerticalScrollbar(listState)
        state.searchResultWindow?.index?.takeIf { resultIds.isNotEmpty() }?.let { index ->
            SearchPageIndicator(listState, index, onClick = { pageDialog = true })
        }
        }
    }

    }
    if (pageDialog) AlertDialog(
        onDismissRequest = { pageDialog = false },
        title = { Text("페이지 이동") },
        text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(10) }, label = { Text("페이지 번호") },
            placeholder = { Text(maxPage?.let { "최대 ${java.text.NumberFormat.getIntegerInstance().format(it)}페이지" } ?: "이동할 페이지 입력") },
            supportingText = { Text(maxPage?.let { "1 ~ ${java.text.NumberFormat.getIntegerInstance().format(it)}페이지" } ?: "1 이상의 페이지 번호") },
            isError = pageInput.isNotEmpty() && !isSearchPageInRange(pageInput.toIntOrNull(), maxPage),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton(enabled = isSearchPageInRange(pageInput.toIntOrNull(), maxPage), onClick = { onJumpToPage(pageInput.toInt()); pageDialog = false }) { Text("이동") } },
        dismissButton = { TextButton(onClick = { pageDialog = false }) { Text("취소") } },
    )

    deleteLanguage?.let { language ->
        AlertDialog(
            onDismissRequest = { deleteLanguage = null },
            title = { Text("언어 삭제") },
            text = { Text("${language.replaceFirstChar { it.uppercase() }} 언어를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCustomLanguage(language)
                    deleteLanguage = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteLanguage = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun GalleryCard(
    gallery: GallerySummary,
    gridView: Boolean = false,
    read: Boolean,
    libraryBook: LibraryBook?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSearchFacet: (String) -> Unit,
) {
    val context = LocalContext.current
    val request = remember(context, gallery.thumbnailUrl, gallery.id) {
        gallery.thumbnailUrl?.let { hitomiImageRequest(context, it, gallery.id) }
    }

    if (gridView) {
        val tagAreaHeight = (110 * LocalDensity.current.fontScale).dp
        Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "새 탭에서 열기")) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text("#${gallery.id}", style = MaterialTheme.typography.labelSmall)
                if (request != null) AsyncImage(model = request, contentDescription = "${preferredLocalizedTitle(gallery.title)} 썸네일", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                LibraryThumbnailBadges(read, libraryBook?.isCloud, favorite, showFavorite = libraryBook != null, onToggleFavorite = onToggleFavorite, scale = 0.75f)
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preferredLocalizedTitle(gallery.title), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(gallery.artists.joinToString().ifBlank { "작가 정보 없음" }, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(listOfNotNull(gallery.language, gallery.pageCount.takeIf { it > 0 }?.let { "${it}페이지" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.fillMaxWidth().height(tagAreaHeight).clipToBounds(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp), maxLines = 3) {
                        gallery.tags.take(3).forEach { tag ->
                            TagPill(info = tagDisplayInfo(tag.name, tag.type), large = true, modifier = Modifier.clickable {
                                onSearchFacet(tagSearchFacet(tag.name, tag.type))
                            })
                        }
                    }
                        if (gallery.tags.size > 3) Text("+${gallery.tags.size - 3}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(gallery.loadError.orEmpty(), minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "새 탭에서 열기",
    )) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(144.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("#${gallery.id}", style = MaterialTheme.typography.labelMedium)
                if (request != null) {
                    AsyncImage(
                        model = request,
                        contentDescription = "${preferredLocalizedTitle(gallery.title)} 썸네일",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                LibraryThumbnailBadges(
                    read = read,
                    isCloud = libraryBook?.isCloud,
                    favorite = favorite,
                    showFavorite = libraryBook != null,
                    onToggleFavorite = onToggleFavorite,
                    scale = 0.82f,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 144.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    preferredLocalizedTitle(gallery.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    gallery.artists.takeIf { it.isNotEmpty() }?.joinToString() ?: "작가 정보 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        gallery.language,
                        gallery.pageCount.takeIf { it > 0 }?.let { "${it}페이지" },
                        formatGalleryPublishedDate(gallery.publishedDate)?.let { "업로드 $it" },
                    ).joinToString(" · ").ifEmpty { "ID ${gallery.id}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                gallery.tags.take(3).takeIf { it.isNotEmpty() }?.let { tags ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        tags.forEach { tag ->
                            TagPill(
                                info = tagDisplayInfo(tag.name, tag.type),
                                large = true,
                                modifier = Modifier.clickable {
                                    onSearchFacet(tagSearchFacet(tag.name, tag.type))
                                },
                            )
                        }
                    }
                }
                gallery.loadError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

fun hitomiImageRequest(
    context: Context,
    url: String,
    galleryId: Long,
    crossfade: Boolean = true,
    preview: Boolean = false,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(
            NetworkHeaders.Builder()
                .set("Referer", "https://hitomi.la/reader/$galleryId.html")
                .build(),
        )
        .apply {
            if (preview) {
                size(360, 480)
                precision(Precision.INEXACT)
            }
        }
        .crossfade(crossfade)
        .build()

@Composable
fun LoadingRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp).height(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingSearchToolbar(behavior: TopAppBarScrollBehavior, content: @Composable () -> Unit) {
    Box(Modifier.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        behavior.state.heightOffsetLimit = -placeable.height.toFloat()
        val offset = behavior.state.heightOffset.roundToInt()
        layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) {
            placeable.placeRelative(0, offset)
        }
    }) { content() }
}

internal fun visibleSearchPage(firstVisibleListIndex: Int, index: SearchResultIndex): Int =
    index.pageAt((firstVisibleListIndex - 2).coerceAtLeast(0))

@Composable
private fun BoxScope.SearchPageIndicator(listState: LazyGridState, index: SearchResultIndex, onClick: () -> Unit) {
    val page by remember(listState, index) { derivedStateOf { visibleSearchPage(listState.firstVisibleItemIndex, index) } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) visible = true
        else { delay(1200); visible = false }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(180)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 16.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = "현재 ${page}페이지. 눌러서 페이지 이동" }) {
            Text("${page}페이지", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SearchSortMenu(query: String, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val oldest = remember(query) { parseHitomiSearchOrder(query).oldest }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
            ActionIcon(ActionSymbol.SORT); Spacer(Modifier.width(4.dp)); Text("정렬")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChoiceMenuItem("최신순", !oldest, onClick = { expanded = false; onSelect(false) }, enabled = enabled)
            ChoiceMenuItem("오래된순", oldest, onClick = { expanded = false; onSelect(true) }, enabled = enabled)
        }
    }
}
