package com.doujinzip.android.ui

import com.doujinzip.android.model.ReaderImageSource
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.R

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.doujinzip.android.model.ViewerPreferences

@Composable
fun ReaderScreen(
    sessionKey: String,
    galleryId: Long,
    initialPage: Int,
    initialOffset: Int,
    state: MainUiState,
    preferences: ViewerPreferences,
    onLoad: (Long) -> Unit,
    onBack: () -> Unit,
    onProgress: (ReaderPosition) -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
    isSeries: Boolean = false,
    previousBookTitle: String? = null,
    nextBookTitle: String? = null,
    onOpenPreviousBook: (() -> Unit)? = null,
    onOpenNextBook: (() -> Unit)? = null,
    onReadingModeResolved: (com.doujinzip.android.model.ViewerReadingMode) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    HideSystemBars()
    LaunchedEffect(galleryId) { onLoad(galleryId) }
    val pageUrls = state.readerPagesByGalleryId[galleryId].orEmpty()
    val readerError = state.readerErrorsByGalleryId[galleryId]

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            galleryId in state.readerLoadingGalleryIds -> CircularProgressIndicator()
            readerError != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(readerError, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onLoad(galleryId) }) { Text(strings[R.string.ui_try_again]) }
                    Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text(strings[R.string.ui_return]) }
                }
            }
            pageUrls.isEmpty() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings[R.string.ui_no_pages], color = Color.White)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text(strings[R.string.ui_return]) }
                }
            }
            else -> {
                val context = LocalContext.current
                val pages = remember(galleryId, pageUrls) {
                    pageUrls.map { url ->
                        ReaderPageModel(
                            key = url,
                            source = ReaderImageSource(url, mapOf("Referer" to "https://hitomi.la/reader/$galleryId.html")),
                            model = hitomiImageRequest(context, url, galleryId),
                        )
                    }
                }
                val title = state.galleryCache[galleryId]?.title
                    ?: strings[R.string.ui_gallery_d16837, (galleryId)]
                UnifiedReader(
                    readerKey = sessionKey,
                    workKey = "gallery:$galleryId",
                    initialOffset = initialOffset,
                    title = title,
                    pages = pages,
                    initialPage = initialPage,
                    preferences = preferences,
                    onBack = onBack,
                    onProgress = onProgress,
                    onPreferencesChange = onPreferencesChange,
                    isSeries = isSeries,
                    previousBookTitle = previousBookTitle,
                    nextBookTitle = nextBookTitle,
                    onOpenPreviousBook = onOpenPreviousBook,
                    onOpenNextBook = onOpenNextBook,
                    onReadingModeResolved = onReadingModeResolved,
                )
            }
        }
    }
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
