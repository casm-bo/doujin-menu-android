package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onSearchFacet: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (book?.isCloud == true) "클라우드 갤러리 상세" else "갤러리 상세") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
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
                        FacetSection("언어", listOf(value), "language", onSearchFacet)
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
                    Button(
                        onClick = { onOpenReader(0) },
                        enabled = !isLoading && book.pages.all { it.uri.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (isLoading) "페이지 불러오는 중…" else "전체화면으로 읽기") }
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
private fun FacetSection(
    title: String,
    values: List<String>,
    prefix: String,
    onSearchFacet: (String) -> Unit,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                AssistChip(
                    onClick = { onSearchFacet("$prefix:$value") },
                    label = { Text(value.replace('_', ' ')) },
                )
            }
        }
    }
}
