package com.doujinmenu.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.GallerySummary

@Composable
fun ReaderScreen(
    galleryId: Long,
    initialPage: Int,
    state: MainUiState,
    onLoad: (Long) -> Unit,
    onBack: () -> Unit,
) {
    HideSystemBars()
    LaunchedEffect(galleryId) { onLoad(galleryId) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isReaderLoading -> CircularProgressIndicator()
            state.readerError != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.readerError, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onLoad(galleryId) }) { Text("다시 시도") }
                }
            }
            state.readerPages.isEmpty() -> Text("페이지가 없습니다.", color = Color.White)
            else -> ReaderPager(galleryId, state.readerPages, initialPage)
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) { Text("닫기") }
    }
}

@Composable
private fun ReaderPager(galleryId: Long, pages: List<String>, initialPage: Int) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
        pageCount = { pages.size },
    )
    val context = LocalContext.current

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = minOf(2, pages.lastIndex.coerceAtLeast(0)),
        key = { pages[it] },
    ) { page ->
        ZoomableReaderImage(
            model = hitomiImageRequest(context, pages[page], galleryId),
            contentDescription = "${page + 1}페이지",
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${pagerState.currentPage + 1} / ${pages.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ZoomableReaderImage(model: Any, contentDescription: String) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale > 1f) offset + panChange else Offset.Zero
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            }
            .transformable(transformState, enabled = scale > 1f),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun HideSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
