package com.doujinmenu.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onSearchLanguage: (String) -> Unit,
    onDownload: (GallerySummary) -> Unit,
    tabBar: @Composable () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("갤러리 상세") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("<") } },
                )
                tabBar()
            }
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
        val previewPages = state.readerPagesByGalleryId[gallery.id].orEmpty()

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
                            contentDescription = preferredLocalizedTitle(gallery.title),
                            modifier = Modifier.fillMaxWidth().height(390.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        preferredLocalizedTitle(gallery.title),
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
                                    CopyableFacetChip(
                                        text = artist,
                                        onClick = { onSearchFacet("artist:$artist") },
                                        enabled = artist.isGalleryFacetSearchable(),
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
                                        enabled = series.isGalleryFacetSearchable(),
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
                                enabled = type.isGalleryFacetSearchable(),
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
                            gallery.pageCount.takeIf { it > 0 }?.let { "${it}페이지" },
                            formatGalleryPublishedDate(gallery.publishedDate)?.let { "업로드 $it" },
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    gallery.language?.let { language ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("언어", fontWeight = FontWeight.SemiBold)
                            AssistChip(
                                onClick = { onSearchLanguage(language) },
                                enabled = language.isGalleryFacetSearchable(),
                                label = { Text(language.replace('_', ' ')) },
                            )
                        }
                    }
                    if (gallery.tags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("태그", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                gallery.tags.forEach { tag ->
                                    val facet = tagSearchFacet(tag.name, tag.type)
                                    val display = tagDisplayInfo(tag.name, tag.type)
                                    CopyableFacetChip(
                                        text = display.text,
                                        tagStyle = display.style,
                                        clipboardText = facet,
                                        onClick = { onSearchFacet(facet) },
                                        enabled = tag.name.isGalleryFacetSearchable(),
                                    )
                                }
                            }
                        }
                    }
                    val queuedDownload = state.downloadQueue.firstOrNull {
                        it.galleryId == gallery.id
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onOpenReader(0) }, modifier = Modifier.weight(1f)) {
                            Text("전체화면으로 읽기")
                        }
                        OutlinedButton(
                            onClick = { onDownload(gallery) },
                            enabled = gallery.id !in state.downloadingGalleryIds && queuedDownload == null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                when {
                                    gallery.id in state.downloadingGalleryIds -> "요청 중…"
                                    queuedDownload != null -> "다운로드 큐에 있음"
                                    else -> "다운로드하기"
                                },
                            )
                        }
                    }
                    Text("갤러리 미리보기", style = MaterialTheme.typography.titleMedium)
                    when {
                        gallery.id in state.readerLoadingGalleryIds -> {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        state.readerErrorsByGalleryId[gallery.id] != null -> {
                            Text(state.readerErrorsByGalleryId.getValue(gallery.id), color = MaterialTheme.colorScheme.error)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CopyableFacetChip(
    text: String,
    clipboardText: String = text,
    tagStyle: TagStyle = TagStyle.DEFAULT,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val (containerColor, contentColor) = tagColors(tagStyle)
    Surface(
        modifier = Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("gallery metadata", clipboardText))
                Toast.makeText(context, "클립보드에 복사했습니다.", Toast.LENGTH_SHORT).show()
            },
        ),
        shape = RoundedCornerShape(if (tagStyle == TagStyle.DEFAULT) 8.dp else 50.dp),
        color = containerColor,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        border = if (tagStyle == TagStyle.DEFAULT) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

private fun String.isGalleryFacetSearchable(): Boolean =
    trim().isNotEmpty() && lowercase() !in setOf("n/a", "na", "unknown", "정보 없음")
