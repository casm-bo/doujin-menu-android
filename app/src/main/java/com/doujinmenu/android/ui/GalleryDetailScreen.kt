package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.GallerySummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    gallery: GallerySummary?,
    state: MainUiState,
    onLoadPreview: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onSearchFacet: (String) -> Unit,
    onDownload: (GallerySummary) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("갤러리 상세") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { scaffoldPadding ->
        if (gallery == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) { Text("갤러리 정보를 찾을 수 없습니다.") }
            return@Scaffold
        }

        LaunchedEffect(gallery.id) { onLoadPreview(gallery.id) }
        val context = LocalContext.current
        val previewPages = state.readerPages.takeIf { state.readerGalleryId == gallery.id }.orEmpty()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    gallery.thumbnailUrl?.let { url ->
                        AsyncImage(
                            model = hitomiImageRequest(context, url, gallery.id, crossfade = false),
                            contentDescription = gallery.title,
                            modifier = Modifier.fillMaxWidth().height(390.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        gallery.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("작가", fontWeight = FontWeight.SemiBold)
                        if (gallery.artists.isEmpty()) {
                            Text("작가 정보 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                gallery.artists.forEach { artist ->
                                    AssistChip(
                                        onClick = { onSearchFacet("artist:$artist") },
                                        label = { Text(artist) },
                                    )
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Series", fontWeight = FontWeight.SemiBold)
                        if (gallery.series.isEmpty()) {
                            Text("시리즈 정보 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                gallery.series.forEach { series ->
                                    AssistChip(
                                        onClick = { onSearchFacet("series:$series") },
                                        label = { Text(series) },
                                    )
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Type", fontWeight = FontWeight.SemiBold)
                        gallery.galleryType?.let { type ->
                            AssistChip(
                                onClick = { onSearchFacet("type:$type") },
                                label = { Text(type) },
                            )
                        } ?: Text(
                            "타입 정보 없음",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOfNotNull(
                            "ID ${gallery.id}",
                            gallery.language,
                            gallery.pageCount.takeIf { it > 0 }?.let { "${it}페이지" },
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (gallery.tags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("태그", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                gallery.tags.forEach { tag ->
                                    val facet = "${tag.type}:${tag.name}"
                                    AssistChip(
                                        onClick = { onSearchFacet(facet) },
                                        label = { Text(facet) },
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onOpenReader(0) }, modifier = Modifier.weight(1f)) {
                            Text("전체화면으로 읽기")
                        }
                        OutlinedButton(
                            onClick = { onDownload(gallery) },
                            enabled = gallery.id !in state.downloadingGalleryIds,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (gallery.id in state.downloadingGalleryIds) "요청 중…" else "다운로드하기")
                        }
                    }
                    Text("갤러리 전체 썸네일", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.isReaderLoading && state.readerGalleryId == gallery.id -> {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        state.readerError != null && state.readerGalleryId == gallery.id -> {
                            Text(state.readerError, color = MaterialTheme.colorScheme.error)
                        }
                        previewPages.isEmpty() -> {
                            Text("미리보기 이미지가 없습니다.")
                        }
                    }
                }
            }
            itemsIndexed(previewPages, key = { index, url -> "$index-$url" }) { index, url ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenReader(index) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                    AsyncImage(
                        model = hitomiImageRequest(
                            context = context,
                            url = url,
                            galleryId = gallery.id,
                            crossfade = false,
                            preview = true,
                        ),
                        contentDescription = "${index + 1}페이지 미리보기",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
