package com.doujinmenu.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.R
import com.doujinmenu.android.data.ReaderImages
import com.doujinmenu.android.model.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun WebtoonReader(
    readerKey: String, pages: List<ReaderPageModel>, initial: ReaderPosition,
    initialDimensions: Map<Int, ImageDimensions>, jumpPage: Int?, onJumpHandled: () -> Unit,
    onCurrentPage: (Int) -> Unit, onProgress: (ReaderPosition) -> Unit,
    onToggleControls: () -> Unit, onUsePageMode: () -> Unit, onNextBook: (() -> Unit)? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val repository = remember(context) { ReaderImages(context) }
    val dimensions = remember(readerKey) { mutableStateMapOf<Int, ImageDimensions>().apply { putAll(initialDimensions) } }
    val files = remember(readerKey) { mutableStateMapOf<Int, File>() }
    val retries = remember(readerKey) { mutableStateMapOf<Int, Int>() }
    val errors = remember(readerKey) { mutableStateMapOf<Int, Boolean>() }
    val state = remember(readerKey) { LazyListState() }
    var resume by remember(readerKey) { mutableStateOf(initial) }
    var restoreTarget by remember(readerKey) { mutableStateOf(initial) }
    var restored by remember(readerKey) { mutableStateOf(false) }
    suspend fun prepare(page: Int) {
        if (files[page]?.isFile == true || page in errors) return
        files.remove(page)
        try {
            val file = repository.file(pages[page].source)
            val size = withContext(Dispatchers.IO) { repository.dimensions(file) } ?: error("Unknown image size")
            dimensions[page] = size; files[page] = file
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { errors[page] = true }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val tiles = remember(readerKey, width, dimensions.toMap()) {
            pages.indices.flatMap { page ->
                val size = dimensions[page] ?: ImageDimensions(width, width)
                runCatching { webtoonTiles(page, size, width) }.getOrElse { listOf(WebtoonTile(page, 0, width, width)) }
            }
        }
        fun position(): ReaderPosition? = tiles.getOrNull(state.firstVisibleItemIndex)?.let { tile ->
            if (tile.page in dimensions) webtoonPosition(tile, state.firstVisibleItemScrollOffset, width) else null
        }
        LaunchedEffect(readerKey, width) { restoreTarget = resume; restored = false }
        LaunchedEffect(readerKey, jumpPage) {
            jumpPage?.let { restoreTarget = ReaderPosition(it.coerceIn(pages.indices)); restored = false }
        }
        LaunchedEffect(readerKey, restoreTarget) { prepare(restoreTarget.page.coerceIn(pages.indices)) }
        LaunchedEffect(readerKey, width, restoreTarget, tiles) {
            if (!restored && (restoreTarget.page in dimensions || restoreTarget.page in errors)) {
                val index = tiles.indexOfLast { it.page == restoreTarget.page && it.top <= restoreTarget.offset }.coerceAtLeast(0)
                val tile = tiles[index]
                val offset = ((restoreTarget.offset - tile.top).coerceIn(0, tile.sourceHeight) * width.toDouble() / tile.sourceWidth).roundToInt()
                state.scrollToItem(index, offset)
                restored = true; onJumpHandled()
            }
        }
        LaunchedEffect(readerKey, tiles, restored) {
            if (restored) snapshotFlow { tiles.getOrNull(state.firstVisibleItemIndex)?.page }
                .distinctUntilChanged().collect { it?.let(onCurrentPage) }
        }
        LaunchedEffect(readerKey, tiles, restored) {
            if (restored) snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
                position()?.let { resume = it; onProgress(it) }
            }
        }
        DisposableEffect(readerKey, width, tiles, restored) {
            onDispose { if (restored) position()?.let { resume = it; onProgress(it) } }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("webtoon-reader")) {
            itemsIndexed(tiles, key = { _, tile -> "${pages[tile.page].key}:${tile.top}" }) { _, tile ->
                LaunchedEffect(tile.page, errors[tile.page], retries[tile.page]) { prepare(tile.page) }
                val file = files[tile.page]
                val image by produceState<Result<Bitmap>?>(null, file, tile, retries[tile.page]) {
                    value = null
                    if (file != null) value = withContext(Dispatchers.IO) { runCatching { repository.tile(file, tile, width) } }
                }
                val tileHeight = with(density) { (tile.sourceHeight * width.toDouble() / tile.sourceWidth).roundToInt().coerceAtLeast(1).toDp() }
                Box(Modifier.fillMaxWidth().height(tileHeight).clickable(onClick = onToggleControls), contentAlignment = Alignment.Center) {
                    when {
                        errors[tile.page] == true || image?.isFailure == true -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strings[R.string.ui_webtoon_image_error], color = Color.White)
                            TextButton(onClick = { errors.remove(tile.page); files.remove(tile.page); retries[tile.page] = (retries[tile.page] ?: 0) + 1 }) { Text(strings[R.string.ui_try_again]) }
                            TextButton(onClick = onUsePageMode) { Text(strings[R.string.ui_reader_page]) }
                        }
                        image?.isSuccess == true -> Image(image!!.getOrThrow().asImageBitmap(), strings[R.string.ui_pages, tile.page + 1],
                            Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        else -> CircularProgressIndicator()
                    }
                }
            }
            if (onNextBook != null) item(key = "next-book") {
                TextButton(onClick = onNextBook, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(strings[R.string.ui_next_book]) }
            }
        }
        DynamicVerticalScrollbar(state, color = Color.White)
    }
}
