package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.LibraryBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDetailScreen(
    book: LibraryBook?,
    previousBook: LibraryBook?,
    nextBook: LibraryBook?,
    isSeriesBook: Boolean,
    progress: Int,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onOpenPreviousBook: () -> Unit,
    onOpenNextBook: () -> Unit,
    onOpenSeriesList: () -> Unit,
    onSearchFacet: (String) -> Unit,
    onSearchLanguage: (String) -> Unit,
    tabBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("갤러리 상세") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("<") } },
                )
                tabBar()
            }
        },
    ) { padding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("갤러리 정보를 찾을 수 없습니다.")
            }
            return@Scaffold
        }
        val metadata = book.metadata
        val context = LocalContext.current
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (book.coverUri.isNotBlank()) {
                        AsyncImage(
                            model = libraryImageRequest(context, book.coverUri, book.cloudToken),
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxWidth().height(390.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    FacetSection("작가", metadata.artists, "artist", onSearchFacet)
                    FacetSection("그룹", metadata.groups, "group", onSearchFacet)
                    FacetSection("시리즈", metadata.series, "series", onSearchFacet)
                    FacetSection("캐릭터", metadata.characters, "character", onSearchFacet)
                    metadata.galleryType?.let { value ->
                        FacetSection("타입", listOf(value), "type", onSearchFacet)
                    }
                    metadata.language?.let { value ->
                        FacetSection("언어", listOf(value), "language", onSearchLanguage, rawValue = true)
                    }
                    FacetSection("태그", metadata.tags, "tag", onSearchFacet)
                    Text(
                        listOfNotNull(
                            metadata.hitomiId?.let { "ID $it" },
                            "${book.pages.size}페이지",
                            book.locationName,
                            if (book.isCloud) "데스크톱" else null,
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val readerEnabled = !isLoading && book.pages.all { it.uri.isNotBlank() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onOpenReader(0) },
                            enabled = readerEnabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (isLoading) "불러오는 중…" else "처음부터 읽기") }
                        Button(
                            onClick = { onOpenReader(progress.coerceAtLeast(0)) },
                            enabled = readerEnabled && progress > 0,
                            modifier = Modifier.weight(1f),
                        ) { Text("계속해서 읽기") }
                    }
                    if (isSeriesBook) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Button(
                                onClick = onOpenPreviousBook,
                                enabled = previousBook != null,
                                modifier = Modifier.weight(3f),
                                shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                DetailChevronIcon(pointsRight = false)
                                Text("이전화", modifier = Modifier.padding(start = 5.dp), maxLines = 1)
                            }
                            Button(
                                onClick = onOpenSeriesList,
                                modifier = Modifier.weight(4f),
                                shape = RoundedCornerShape(0.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                DetailListIcon()
                                Text("목록보기", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                            Button(
                                onClick = onOpenNextBook,
                                enabled = nextBook != null,
                                modifier = Modifier.weight(3f),
                                shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                Text("다음화", modifier = Modifier.padding(end = 5.dp), maxLines = 1)
                                DetailChevronIcon(pointsRight = true)
                            }
                        }
                    }
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("갤러리 미리보기", style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(book.pages, key = { index, page -> "$index-${page.name}" }) { index, page ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = page.uri.isNotBlank()) { onOpenReader(index) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                    if (page.uri.isNotBlank()) {
                        AsyncImage(
                            model = libraryImageRequest(context, page.uri, book.cloudToken),
                            contentDescription = "${index + 1}페이지 미리보기",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChevronIcon(pointsRight: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(16.dp)) {
        val startX = if (pointsRight) size.width * 0.32f else size.width * 0.68f
        val endX = if (pointsRight) size.width * 0.7f else size.width * 0.3f
        drawLine(color, Offset(startX, size.height * 0.18f), Offset(endX, size.height * 0.5f), size.width * 0.12f)
        drawLine(color, Offset(endX, size.height * 0.5f), Offset(startX, size.height * 0.82f), size.width * 0.12f)
    }
}

@Composable
private fun DetailListIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(17.dp)) {
        listOf(0.25f, 0.5f, 0.75f).forEach { y ->
            drawCircle(color, radius = size.width * 0.07f, center = Offset(size.width * 0.12f, size.height * y))
            drawLine(
                color,
                Offset(size.width * 0.28f, size.height * y),
                Offset(size.width * 0.9f, size.height * y),
                strokeWidth = size.width * 0.1f,
            )
        }
    }
}

@Composable
private fun FacetSection(
    title: String,
    values: List<String>,
    prefix: String,
    onSearchFacet: (String) -> Unit,
    rawValue: Boolean = false,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                val enabled = value.isSearchableFacet()
                val facet = when {
                    rawValue -> value
                    prefix == "tag" && value.substringBefore(':').lowercase() in setOf("female", "male") -> value
                    else -> "$prefix:$value"
                }
                val display = if (prefix == "tag") tagDisplayInfo(value) else null
                CopyableFacetChip(
                    text = display?.text ?: value.replace('_', ' '),
                    clipboardText = if (prefix == "artist") value else facet,
                    tagStyle = display?.style ?: TagStyle.DEFAULT,
                    onClick = { onSearchFacet(facet) },
                    enabled = enabled,
                )
            }
        }
    }
}

private fun String.isSearchableFacet(): Boolean =
    trim().isNotEmpty() && lowercase() !in setOf("n/a", "na", "unknown", "정보 없음")
