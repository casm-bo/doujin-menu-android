package com.doujinmenu.android.ui

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
import com.doujinmenu.android.model.ViewerPreferences

@Composable
fun ReaderScreen(
    galleryId: Long,
    initialPage: Int,
    state: MainUiState,
    preferences: ViewerPreferences,
    onLoad: (Long) -> Unit,
    onBack: () -> Unit,
    onProgress: (Int) -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
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
                    Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("돌아가기") }
                }
            }
            state.readerPages.isEmpty() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("페이지가 없습니다.", color = Color.White)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("돌아가기") }
                }
            }
            else -> {
                val context = LocalContext.current
                val pageUrls = state.readerPages
                val pages = remember(galleryId, pageUrls) {
                    pageUrls.map { url ->
                        ReaderPageModel(
                            key = url,
                            model = hitomiImageRequest(context, url, galleryId),
                        )
                    }
                }
                val title = state.galleryCache[galleryId]?.title
                    ?: state.activeGallery?.takeIf { it.id == galleryId }?.title
                    ?: "갤러리 $galleryId"
                UnifiedReader(
                    readerKey = "online:$galleryId",
                    title = title,
                    pages = pages,
                    initialPage = initialPage,
                    preferences = preferences,
                    onBack = onBack,
                    onProgress = onProgress,
                    onPreferencesChange = onPreferencesChange,
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
