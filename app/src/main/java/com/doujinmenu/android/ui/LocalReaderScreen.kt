package com.doujinmenu.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.model.ViewerScale
import kotlinx.coroutines.launch

@Composable
fun LocalReaderScreen(
    book: LibraryBook?,
    initialPage: Int,
    favorite: Boolean,
    preferences: ViewerPreferences,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProgress: (Int) -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
) {
    HideSystemBars()
    KeepScreenOn(preferences.keepScreenOn)
    if (book == null || book.pages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("책을 열 수 없습니다.", color = Color.White)
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("돌아가기") }
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, book.pages.lastIndex),
        pageCount = { book.pages.size },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { onProgress(pagerState.currentPage) }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = minOf(2, book.pages.lastIndex),
            key = { book.pages[it].uri },
        ) { page ->
            LocalZoomableReaderImage(
                model = libraryImageRequest(context, book.pages[page].uri, book.cloudToken),
                contentDescription = "${page + 1}페이지",
                contentScale = if (preferences.scale == ViewerScale.FIT_WIDTH) {
                    ContentScale.FillWidth
                } else {
                    ContentScale.Fit
                },
                onTap = { fraction ->
                    when {
                        fraction < TAP_ZONE -> if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        fraction > 1f - TAP_ZONE -> if (pagerState.currentPage < book.pages.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        else -> controlsVisible = !controlsVisible
                    }
                },
            )
        }

        if (controlsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.72f)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹ 뒤로", color = Color.White) }
                Text(
                    book.title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onToggleFavorite) {
                    Text(if (favorite) "♥" else "♡", color = Color.White)
                }
                TextButton(onClick = { settingsVisible = true }) { Text("설정", color = Color.White) }
            }
        }
        if (preferences.showPageNumber) {
            Text(
                "${pagerState.currentPage + 1} / ${book.pages.size}",
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
            )
        }
    }

    if (settingsVisible) {
        ViewerSettingsDialog(
            preferences = preferences,
            onChange = onPreferencesChange,
            onDismiss = { settingsVisible = false },
        )
    }
}

@Composable
private fun LocalZoomableReaderImage(
    model: Any,
    contentDescription: String,
    contentScale: ContentScale,
    onTap: (Float) -> Unit,
) {
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
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(model, scale) {
                detectTapGestures(
                    onTap = { tap -> if (scale == 1f) onTap(tap.x / size.width) },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            }
            .transformable(transformState, enabled = scale > 1f),
        contentScale = contentScale,
    )
}

@Composable
private fun ViewerSettingsDialog(
    preferences: ViewerPreferences,
    onChange: (ViewerPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("뷰어 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("화면 맞춤", style = MaterialTheme.typography.titleSmall)
                ViewerScale.entries.forEach { scale ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = preferences.scale == scale,
                            onClick = { onChange(preferences.copy(scale = scale)) },
                        )
                        Text(if (scale == ViewerScale.FIT_SCREEN) "전체 페이지" else "너비 맞춤")
                    }
                }
                SettingSwitch("페이지 번호 표시", preferences.showPageNumber) {
                    onChange(preferences.copy(showPageNumber = it))
                }
                SettingSwitch("화면 꺼짐 방지", preferences.keepScreenOn) {
                    onChange(preferences.copy(keepScreenOn = it))
                }
                Text(
                    "왼쪽을 누르면 이전 장, 오른쪽을 누르면 다음 장으로 이동합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current.localReaderActivity()
    DisposableEffect(activity, enabled) {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private tailrec fun Context.localReaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.localReaderActivity()
    else -> null
}

private const val TAP_ZONE = 0.35f
