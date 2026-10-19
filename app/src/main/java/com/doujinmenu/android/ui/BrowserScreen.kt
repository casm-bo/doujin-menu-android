package com.doujinmenu.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.doujinmenu.android.model.resolveScrollIndex
import com.doujinmenu.android.network.FilterSuggestion
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
    onToggleLanguage: (String) -> Unit,
    onAddCustomLanguage: (String) -> Unit,
    onRemoveCustomLanguage: (String) -> Unit,
    onSelectSuggestion: (FilterSuggestion) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onSearchFacet: (String) -> Unit,
    onToggleLibraryFavorite: (String) -> Unit,
    onConnect: () -> Unit,
    pageKey: String,
    initialScrollAnchorKey: String?,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChange: (String?, Int, Int) -> Unit,
    onEnsureResults: () -> Unit,
    onVisibleResultsChange: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var expandedPanel by rememberSaveable { mutableStateOf<String?>(null) }
    var addLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var languageInput by rememberSaveable { mutableStateOf("") }
    var deleteLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryBookByGalleryId = remember(state.libraryBooks, state.libraryHiddenIds) {
        val visibleBooks = state.libraryBooks.filterNot { it.id in state.libraryHiddenIds }
        visibleBooks.mapNotNull { it.metadata.hitomiId?.toLongOrNull() }
            .distinct()
            .associateWith { preferredLibraryBookForGalleryId(visibleBooks, it) }
    }
    val resultIds = state.searchResultWindow?.index?.let { index -> remember(index) { index.ids } }
        ?: remember(state.galleries) { state.galleries.map { it.id } }
    val galleriesById = remember(state.galleries) { state.galleries.associateBy { it.id } }
    val canRestoreResults = state.searchResultWindow == null && state.currentPage > 0 &&
        !state.isLoadingPage && !state.isRestoringSearch
    val contentKeys = remember(resultIds, state.isLoadingPage, state.currentPage, state.hasNextPage, canRestoreResults) { buildList {
        add("search-controls")
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) add("empty-results")
        if (state.isLoadingPage && resultIds.isEmpty()) add("initial-loader")
        if (canRestoreResults) add("restore-results")
        if (resultIds.isNotEmpty()) {
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
        LazyListState(
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
        val index = listState.firstVisibleItemIndex
        val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString()
        onScrollChange(anchor, index, listState.firstVisibleItemScrollOffset)
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
            val first = visible.firstOrNull()?.index?.minus(2)?.coerceAtLeast(0) ?: 0
            val last = visible.lastOrNull()?.index?.minus(2)?.coerceAtLeast(first) ?: first
            first to last
        }.distinctUntilChanged().collect { (first, last) -> onVisibleResultsChange(first, last) }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BackHandler(editing) { editing = false; focusManager.clearFocus() }
    var menu by remember { mutableStateOf<String?>(null) }
    var compact by rememberSaveable { mutableStateOf(false) }
    var pageDialog by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable { mutableStateOf("") }
    var targetPage by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(pageKey, state.currentPage, state.isLoadingPage, state.isRestoringSearch, targetPage) {
        val target = targetPage ?: return@LaunchedEffect
        if (state.isLoadingPage || state.isRestoringSearch) return@LaunchedEffect
        if (state.nextSearchPageError != null) { targetPage = null; return@LaunchedEffect }
        val pages = state.searchResultWindow?.index?.pages.orEmpty()
        val targetIndex = pages.take((target - 1).coerceAtMost((pages.size - 1).coerceAtLeast(0))).sumOf { it.size }
        if (target <= state.currentPage || !state.hasNextPage) {
            val index = targetIndex.coerceAtMost((resultIds.size - 1).coerceAtLeast(0))
            if (resultIds.isNotEmpty()) listState.scrollToItem(contentKeys.indexOf("gallery:${resultIds[index]}").coerceAtLeast(0))
            targetPage = null
        } else onLoadNextPage()
    }
    val canCollapse = resultIds.isNotEmpty() && !editing && menu == null && !pageDialog
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { canCollapse })
    LaunchedEffect(pageKey, canCollapse) {
        if (!canCollapse) scrollBehavior.state.heightOffset = 0f
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        CollapsingSearchToolbar(scrollBehavior) {
        Column {
        tabHeader({
            UnifiedSearchField(state.searchQuery, onQueryChange, "검색 조건", { editing = true }, {
                targetPage = null
                editing = false
                onSearch()
            })
        }, {
            Box {
                IconButton(onClick = { menu = "root" }, modifier = Modifier.semantics { contentDescription = "검색 옵션" }) { Text("⋯") }
                DropdownMenu(expanded = menu != null, onDismissRequest = { menu = null }) {
                    if (menu == "sort") {
                        DropdownMenuItem(text = { Text("검색 옵션") }, leadingIcon = { ActionIcon(ActionSymbol.BACK) }, onClick = { menu = "root" })
                        listOf("최신순" to false, "오래된순" to true).forEach { (label, oldest) ->
                            DropdownMenuItem(text = { Text(label) }, leadingIcon = { ActionIcon(ActionSymbol.SORT) }, onClick = {
                                val query = state.searchQuery.replace(Regex("(?i)(^|\\s)sort:(oldest|newest)(?=\\s|$)"), " ").trim()
                                onQueryChange(listOf(query, if (oldest) "sort:oldest" else "sort:newest").filter { it.isNotBlank() }.joinToString(" "))
                                menu = null
                                targetPage = null
                                onSearch()
                            }, enabled = !state.isLoadingPage)
                        }
                    } else {
                        DropdownMenuItem(text = { Text("이 검색 즐겨찾기 등록") }, leadingIcon = { ActionIcon(ActionSymbol.STAR) }, enabled = state.searchQuery.isNotBlank(), onClick = { menu = null; editing = true; expandedPanel = "favorites" })
                        DropdownMenuItem(text = { Text("페이지 이동") }, leadingIcon = { ActionIcon(ActionSymbol.PAGE) }, enabled = resultIds.isNotEmpty(), onClick = { menu = null; pageDialog = true })
                        DropdownMenuItem(text = { Text(if (compact) "자세히 보기" else "리스트 보기") }, leadingIcon = { ActionIcon(if (compact) ActionSymbol.GRID else ActionSymbol.LIST) }, onClick = { compact = !compact; menu = null })
                        DropdownMenuItem(text = { Text("정렬…") }, leadingIcon = { ActionIcon(ActionSymbol.SORT) }, onClick = { menu = "sort" })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("새로고침") }, leadingIcon = { ActionIcon(ActionSymbol.REFRESH) }, onClick = { targetPage = null; menu = null; onRefresh() })
                        DropdownMenuItem(text = { Text("검색어 지우기") }, leadingIcon = { ActionIcon(ActionSymbol.CLEAR) }, onClick = { onQueryChange(""); menu = null; editing = true })
                    }
                }
            }
        })
        AnimatedVisibility(visible = editing, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions) {
                    state.filterSuggestions.forEach { suggestion ->
                        TextButton(onClick = { onSelectSuggestion(suggestion) }) { Text("${suggestion.token} · ${suggestion.displayName}") }
                    }
                    if (state.isLoadingFilterSuggestions) Text("필터 목록 불러오는 중…")
                }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                expandedPanel = if (expandedPanel == "favorites") null else "favorites"
                            },
                        ) { ActionIcon(ActionSymbol.STAR); Spacer(Modifier.width(6.dp)); Text("즐겨찾기 ${state.searchFavorites.size}") }
                        TextButton(
                            onClick = {
                                expandedPanel = if (expandedPanel == "languages") null else "languages"
                            },
                        ) { ActionIcon(ActionSymbol.LANGUAGE); Spacer(Modifier.width(6.dp)); Text("언어 ${state.preferredLanguages.size}") }

                    }

                    if (expandedPanel == "languages") {
                        Text(
                            if (state.preferredLanguages.isEmpty())
                                "선택하지 않으면 언어 필터를 적용하지 않습니다."
                            else "선택한 언어를 각각 검색해 결과를 합칩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PREFERRED_LANGUAGES.forEach { (value, label) ->
                                FilterChip(
                                    selected = value in state.preferredLanguages,
                                    onClick = { onToggleLanguage(value) },
                                    label = { Text(label) },
                                )
                            }
                            (state.customLanguages + (state.preferredLanguages -
                                PREFERRED_LANGUAGES.mapTo(linkedSetOf()) { it.first }))
                                .sorted().forEach { language ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (language in state.preferredLanguages)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onToggleLanguage(language) },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            deleteLanguage = language
                                        },
                                    ),
                                ) {
                                    Text(
                                        language.replaceFirstChar { it.uppercase() },
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    )
                                }
                            }
                            FilterChip(
                                selected = false,
                                onClick = { addLanguageDialog = true },
                                label = { Text("+") },
                            )
                        }
                    }

                    if (expandedPanel == "favorites") {
                        state.searchFavorites.forEach { favorite ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        expandedPanel = null
                                        editing = false
                                        targetPage = null
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
                TextButton(onClick = { focusManager.clearFocus(); editing = false }) { Text("접기") }
            }
        }
        }
        }
        if (targetPage != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${targetPage}페이지로 이동 중…", Modifier.weight(1f).padding(start = 16.dp))
            TextButton(onClick = { targetPage = null }) { Text("취소") }
        }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "search-controls") { Spacer(Modifier.height(0.dp)) }
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) {
            item(key = "empty-results") {
                Text(if (state.currentPage > 0) "검색 결과가 없습니다. 검색 조건을 바꿔 보세요." else "상단 검색창에서 검색어를 입력하세요. 언어와 즐겨찾기도 선택할 수 있습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.isLoadingPage && resultIds.isEmpty()) {
            item(key = "initial-loader") { LoadingRow("불러오는 중…") }
        }
        if (canRestoreResults) {
            item(key = "restore-results") {
                TextButton(onClick = onEnsureResults, modifier = Modifier.fillMaxWidth()) { Text("검색 결과 다시 불러오기") }
            }
        }

        if (resultIds.isNotEmpty()) {
            item(key = "result-count") {
                Text(
                    "검색 결과 ${resultIds.size}개",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(count = resultIds.size, key = { "gallery:${resultIds[it]}" }, contentType = { "gallery" }) { index ->
                val id = resultIds[index]
                val gallery = galleriesById[id]
                if (gallery == null) {
                    Card(Modifier.fillMaxWidth().height(if (compact) 112.dp else 168.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("#$id · 불러오는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val libraryBook = libraryBookByGalleryId[gallery.id]
                    GalleryCard(
                        gallery = gallery,
                        compact = compact,
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
            item(key = "page-loader-${state.currentPage}") {
                LaunchedEffect(state.currentPage, state.hasNextPage, state.isLoadingPage, state.isRestoringSearch, state.nextSearchPageError) {
                    if (state.hasNextPage && !state.isLoadingPage && !state.isRestoringSearch && state.nextSearchPageError == null) onLoadNextPage()
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
        text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(5) }, label = { Text("페이지 번호") }, singleLine = true) },
        confirmButton = { TextButton(enabled = pageInput.toIntOrNull() in 1..10000, onClick = { targetPage = pageInput.toInt(); pageDialog = false }) { Text("이동") } },
        dismissButton = { TextButton(onClick = { pageDialog = false }) { Text("취소") } },
    )

    if (addLanguageDialog) {
        AlertDialog(
            onDismissRequest = { addLanguageDialog = false },
            title = { Text("언어 추가") },
            text = {
                OutlinedTextField(
                    value = languageInput,
                    onValueChange = { languageInput = it },
                    label = { Text("언어") },
                    placeholder = { Text("예: french 또는 French") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCustomLanguage(languageInput)
                        languageInput = ""
                        addLanguageDialog = false
                    },
                    enabled = languageInput.substringAfter(':').isNotBlank(),
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { addLanguageDialog = false }) { Text("취소") }
            },
        )
    }
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

private val PREFERRED_LANGUAGES = listOf(
    "korean" to "한국어",
    "japanese" to "일본어",
    "english" to "영어",
    "chinese" to "중국어",
)

@Composable
private fun GalleryCard(
    gallery: GallerySummary,
    compact: Boolean = false,
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

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "새 탭에서 열기",
    )) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(if (compact) 64.dp else 104.dp)
                    .height(if (compact) 88.dp else 144.dp)
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
                modifier = Modifier.weight(1f).height(if (compact) 88.dp else 144.dp),
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
                gallery.tags.take(3).takeIf { !compact && it.isNotEmpty() }?.let { tags ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        tags.forEach { tag ->
                            TagPill(
                                info = tagDisplayInfo(tag.name, tag.type),
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
private fun BoxScope.SearchPageIndicator(listState: LazyListState, index: SearchResultIndex, onClick: () -> Unit) {
    val page by remember(listState, index) { derivedStateOf { visibleSearchPage(listState.firstVisibleItemIndex, index) } }
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 16.dp)
            .semantics { contentDescription = "현재 ${page}페이지. 눌러서 페이지 이동" }) {
        Text("${page}페이지", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
