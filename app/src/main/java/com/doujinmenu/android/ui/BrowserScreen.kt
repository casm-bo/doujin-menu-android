package com.doujinmenu.android.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    var queryFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = state.searchQuery,
                selection = TextRange(state.searchQuery.length),
            ),
        )
    }
    val contentKeys = buildList {
        add("search-controls")
        if (state.isLoadingPage && state.galleries.isEmpty()) add("initial-loader")
        if (state.galleries.isNotEmpty()) {
            add("result-count")
            state.galleries.forEach { add("gallery:${it.id}") }
        }
        if (state.galleries.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            add("page-loader-${state.currentPage}")
        }
    }
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
    LaunchedEffect(pageKey, contentKeys, state.isLoadingPage) {
        if (!scrollRestored) {
            val anchorIndex = contentKeys.indexOf(savedAnchorKey)
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex, initialScrollOffset)
                scrollRestored = true
            } else if (!state.isLoadingPage && state.galleries.isNotEmpty()) {
                listState.scrollToItem(resolveScrollIndex(null, initialScrollIndex, contentKeys))
                scrollRestored = true
            }
        }
    }
    LaunchedEffect(pageKey, listState, scrollRestored) {
        if (!scrollRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val anchorKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString()
                onScrollChange(anchorKey, index, offset)
            }
    }
    val queryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.searchQuery) {
        if (queryFieldValue.text != state.searchQuery) {
            queryFieldValue = TextFieldValue(
                text = state.searchQuery,
                selection = TextRange(state.searchQuery.length),
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "search-controls") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Hitomi 직접 검색",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = queryFieldValue,
                        onValueChange = { value ->
                            queryFieldValue = value
                            onQueryChange(value.text)
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(queryFocusRequester),
                        label = { Text("검색 조건") },
                        placeholder = { Text("tag:full_color artist:sample_artist") },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                TextButton(onClick = { onQueryChange("") }) { Text("×") }
                            }
                        },
                        singleLine = true,
                        enabled = !state.isLoadingPage,
                    )
                    if (state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            state.filterSuggestions.forEach { suggestion ->
                                TextButton(
                                    onClick = {
                                        onSelectSuggestion(suggestion)
                                        queryFocusRequester.requestFocus()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(suggestion.token, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            suggestion.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (state.isLoadingFilterSuggestions) {
                                Text(
                                    "필터 목록 불러오는 중…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                expandedPanel = if (expandedPanel == "favorites") null else "favorites"
                            },
                        ) { Text("☆ 즐겨찾기 ${state.searchFavorites.size}") }
                        TextButton(
                            onClick = {
                                expandedPanel = if (expandedPanel == "languages") null else "languages"
                            },
                        ) { Text("언어 ${state.preferredLanguages.size}") }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onSearch,
                            enabled = !state.isLoadingPage &&
                                (state.searchQuery.isNotBlank() || state.preferredLanguages.isNotEmpty()),
                        ) { Text("검색") }
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

        if (state.isLoadingPage && state.galleries.isEmpty()) {
            item(key = "initial-loader") { LoadingRow("불러오는 중…") }
        }

        if (state.galleries.isNotEmpty()) {
            item(key = "result-count") {
                Text(
                    "검색 결과 ${state.galleries.size}개",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.galleries, key = { "gallery:${it.id}" }) { gallery ->
                val libraryBook = libraryBookByGalleryId[gallery.id]
                GalleryCard(
                    gallery = gallery,
                    read = gallery.id in state.viewedGalleryIds ||
                        libraryBook?.id in state.libraryReadIds,
                    libraryBook = libraryBook,
                    favorite = libraryBook?.id?.let { it in state.libraryFavoriteIds } == true,
                    onToggleFavorite = {
                        libraryBook?.id?.let(onToggleLibraryFavorite)
                    },
                    onClick = { onGalleryClick(gallery.id) },
                    onLongClick = { onGalleryLongClick(gallery.id) },
                    onSearchFacet = onSearchFacet,
                )
            }
        }

        if (state.galleries.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            item(key = "page-loader-${state.currentPage}") {
                LaunchedEffect(state.currentPage, state.hasNextPage, state.isLoadingPage) {
                    if (state.hasNextPage && !state.isLoadingPage) onLoadNextPage()
                }
                LoadingRow(
                    if (state.isLoadingPage) "${state.currentPage + 1}페이지 불러오는 중…"
                    else "다음 페이지 준비 중…",
                )
            }
        }
        }
        DynamicVerticalScrollbar(listState)
        }
    }

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
    read: Boolean,
    libraryBook: LibraryBook?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSearchFacet: (String) -> Unit,
) {
    val context = LocalContext.current
    val request = gallery.thumbnailUrl?.let { hitomiImageRequest(context, it, gallery.id) }

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
                modifier = Modifier.weight(1f).height(144.dp),
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
