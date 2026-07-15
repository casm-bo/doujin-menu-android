package com.doujinmenu.android.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.doujinmenu.android.model.SearchFavorite
import com.doujinmenu.android.network.FilterSuggestion

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
    onSelectSuggestion: (FilterSuggestion) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onGalleryClick: (Long) -> Unit,
) {
    val selected = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    var expandedPanel by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        selected?.let { "연결 대상: ${it.name}" } ?: "설정에서 데스크톱을 연결하세요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("검색 조건") },
                        placeholder = { Text("tag:full_color artist:sample_artist") },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                TextButton(onClick = { onQueryChange("") }) { Text("×") }
                            }
                        },
                        singleLine = true,
                        enabled = !state.isLoadingPage && selected != null,
                    )
                    if (state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            state.filterSuggestions.forEach { suggestion ->
                                TextButton(
                                    onClick = { onSelectSuggestion(suggestion) },
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
                            enabled = !state.isLoadingPage && selected != null &&
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

        state.message?.let { message ->
            item { MessageCard(message, state.isError) }
        }

        if (state.isLoadingPage && state.galleries.isEmpty()) {
            item { LoadingRow("불러오는 중…") }
        }

        if (state.galleries.isNotEmpty()) {
            item {
                Text(
                    "검색 결과 ${state.galleries.size}개",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.galleries, key = { it.id }) { gallery ->
                GalleryCard(
                    gallery = gallery,
                    viewed = gallery.id in state.viewedGalleryIds,
                    onClick = { onGalleryClick(gallery.id) },
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
    }
}

private val PREFERRED_LANGUAGES = listOf(
    "korean" to "한국어",
    "japanese" to "일본어",
    "english" to "영어",
    "chinese" to "중국어",
    "spanish" to "스페인어",
)

@Composable
private fun GalleryCard(gallery: GallerySummary, viewed: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = gallery.thumbnailUrl?.let { hitomiImageRequest(context, it, gallery.id) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
                        contentDescription = "${gallery.title} 썸네일",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f).height(144.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (viewed) {
                    Text(
                        "봤음",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    gallery.title,
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
                    ).joinToString(" · ").ifEmpty { "ID ${gallery.id}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                gallery.tags.take(3).takeIf { it.isNotEmpty() }?.let { tags ->
                    Text(
                        tags.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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

@Composable
fun MessageCard(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
