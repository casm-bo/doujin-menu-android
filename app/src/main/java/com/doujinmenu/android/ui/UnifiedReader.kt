package com.doujinmenu.android.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.ViewerPageTurnMode
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.model.ViewerReadingDirection
import com.doujinmenu.android.model.ViewerScale
import com.doujinmenu.android.model.ViewerTapAction
import com.doujinmenu.android.model.ViewerTapZones
import kotlinx.coroutines.launch

internal data class ReaderPageModel(val key: String, val model: Any)

internal fun initialThumbnailVisibility(preferences: ViewerPreferences): Boolean =
    !preferences.hideThumbnails

@Composable
internal fun UnifiedReader(
    readerKey: String,
    title: String,
    pages: List<ReaderPageModel>,
    initialPage: Int,
    preferences: ViewerPreferences,
    onBack: () -> Unit,
    onProgress: (Int) -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    nextBookTitle: String? = null,
    onOpenNextBook: (() -> Unit)? = null,
) {
    ReaderKeepScreenOn(preferences.keepScreenOn)

    val pagerState = androidx.compose.runtime.key(readerKey) {
        rememberPagerState(
            initialPage = initialPage.coerceIn(0, pages.lastIndex),
            pageCount = { pages.size },
        )
    }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember(readerKey) { mutableStateOf(true) }
    var thumbnailsVisible by remember(readerKey) {
        mutableStateOf(initialThumbnailVisibility(preferences))
    }
    var settingsVisible by remember(readerKey) { mutableStateOf(false) }
    var nextBookDialog by remember(readerKey) { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(pagerState.currentPage) { onProgress(pagerState.currentPage) }
    LaunchedEffect(preferences.hideThumbnails) {
        thumbnailsVisible = initialThumbnailVisibility(preferences)
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }

    fun moveTo(page: Int) {
        when {
            page in pages.indices -> scope.launch { pagerState.animateScrollToPage(page) }
            page >= pages.size && nextBookTitle != null && onOpenNextBook != null -> nextBookDialog = true
        }
    }

    fun handleTap(action: ViewerTapAction) {
        when (action) {
            ViewerTapAction.PREVIOUS_PAGE -> if (preferences.pageTurnMode != ViewerPageTurnMode.SWIPE_ONLY) {
                moveTo(pagerState.currentPage - 1)
            }
            ViewerTapAction.NEXT_PAGE -> if (preferences.pageTurnMode != ViewerPageTurnMode.SWIPE_ONLY) {
                moveTo(pagerState.currentPage + 1)
            }
            ViewerTapAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
            ViewerTapAction.NONE -> Unit
        }
    }

    fun builtInTapAction(column: Int): ViewerTapAction = when (column) {
        0 -> if (preferences.readingDirection == ViewerReadingDirection.LEFT_TO_RIGHT) {
            ViewerTapAction.PREVIOUS_PAGE
        } else {
            ViewerTapAction.NEXT_PAGE
        }
        2 -> if (preferences.readingDirection == ViewerReadingDirection.LEFT_TO_RIGHT) {
            ViewerTapAction.NEXT_PAGE
        } else {
            ViewerTapAction.PREVIOUS_PAGE
        }
        else -> ViewerTapAction.TOGGLE_CONTROLS
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(
                readerContentPadding(isLandscape, controlsVisible, thumbnailsVisible),
            ),
            beyondViewportPageCount = minOf(2, pages.lastIndex),
            key = { pages[it].key },
            reverseLayout = preferences.readingDirection == ViewerReadingDirection.RIGHT_TO_LEFT,
            userScrollEnabled = preferences.pageTurnMode != ViewerPageTurnMode.TAP_ONLY,
        ) { page ->
            UnifiedZoomableImage(
                model = pages[page].model,
                contentDescription = "${page + 1}페이지",
                contentScale = if (preferences.scale == ViewerScale.FIT_WIDTH) {
                    ContentScale.FillWidth
                } else {
                    ContentScale.Fit
                },
                onTap = { row, column ->
                    handleTap(
                        if (preferences.customTapZonesEnabled) {
                            preferences.tapZones.actionAt(row * 3 + column)
                        } else {
                            builtInTapAction(column)
                        },
                    )
                },
            )
        }

        if (controlsVisible) {
            ReaderTopBar(
                title = title,
                favorite = favorite,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onSettings = { settingsVisible = true },
            )
        }

        AnimatedVisibility(
            visible = thumbnailsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(160)),
        ) {
            ReaderThumbnailBar(
                pages = pages,
                currentPage = pagerState.currentPage,
                onPageClick =(::moveTo),
                onSwipeDown = { thumbnailsVisible = false },
            )
        }

        if (!thumbnailsVisible) {
            ReaderBottomSwipeHandle(
                onSwipeUp = {
                    thumbnailsVisible = true
                    controlsVisible = true
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (!controlsVisible && preferences.showPageNumber) {
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (thumbnailsVisible) 120.dp else 0.dp)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
            )
        }
    }

    if (settingsVisible) {
        UnifiedViewerSettingsDialog(
            preferences = preferences,
            onChange = onPreferencesChange,
            onDismiss = { settingsVisible = false },
        )
    }
    if (nextBookDialog && nextBookTitle != null && onOpenNextBook != null) {
        AlertDialog(
            onDismissRequest = { nextBookDialog = false },
            title = { Text("다음 책") },
            text = { Text("$nextBookTitle 을(를) 이어서 볼까요?") },
            confirmButton = {
                TextButton(onClick = {
                    nextBookDialog = false
                    onOpenNextBook()
                }) { Text("열기") }
            },
            dismissButton = { TextButton(onClick = { nextBookDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    favorite: Boolean?,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .background(Color.Black.copy(alpha = 0.76f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("‹", color = Color.White) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (favorite != null && onToggleFavorite != null) {
            TextButton(onClick = onToggleFavorite) {
                Text(if (favorite) "♥" else "♡", color = Color.White)
            }
        }
        TextButton(onClick = onSettings) { Text("설정", color = Color.White) }
    }
}

@Composable
private fun ReaderThumbnailBar(
    pages: List<ReaderPageModel>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        if (currentPage in pages.indices) listState.animateScrollToItem(currentPage)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .height(READER_THUMBNAIL_BAR_HEIGHT)
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(onSwipeDown) {
                val threshold = 48.dp.toPx()
                var draggedY = 0f
                detectVerticalDragGestures(
                    onDragStart = { draggedY = 0f },
                    onVerticalDrag = { change, amount ->
                        draggedY += amount
                        if (draggedY > threshold) change.consume()
                    },
                    onDragEnd = { if (draggedY > threshold) onSwipeDown() },
                    onDragCancel = { draggedY = 0f },
                )
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pages, key = { _, page -> page.key }) { index, page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = page.model,
                    contentDescription = "${index + 1}페이지로 이동",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 60.dp, height = 84.dp)
                        .border(
                            width = if (index == currentPage) 3.dp else 1.dp,
                            color = if (index == currentPage) MaterialTheme.colorScheme.primary else Color.Gray,
                        )
                        .clickable { onPageClick(index) },
                )
                Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun readerContentPadding(
    isLandscape: Boolean,
    controlsVisible: Boolean,
    thumbnailsVisible: Boolean,
): PaddingValues = if (isLandscape) {
    PaddingValues(
        top = if (controlsVisible) READER_TOP_BAR_HEIGHT else 0.dp,
        bottom = if (thumbnailsVisible) READER_THUMBNAIL_BAR_HEIGHT else 0.dp,
    )
} else {
    PaddingValues()
}

private val READER_TOP_BAR_HEIGHT = 64.dp
private val READER_THUMBNAIL_BAR_HEIGHT = 116.dp

@Composable
private fun ReaderBottomSwipeHandle(
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .background(Color.Black.copy(alpha = 0.12f))
            .pointerInput(onSwipeUp) {
                val threshold = 48.dp.toPx()
                var draggedY = 0f
                detectVerticalDragGestures(
                    onDragStart = { draggedY = 0f },
                    onVerticalDrag = { change, amount ->
                        draggedY += amount
                        if (draggedY < -threshold) change.consume()
                    },
                    onDragEnd = { if (draggedY < -threshold) onSwipeUp() },
                    onDragCancel = { draggedY = 0f },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier.padding(bottom = 8.dp).size(width = 52.dp, height = 4.dp)
                .background(Color.White.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun UnifiedZoomableImage(
    model: Any,
    contentDescription: String,
    contentScale: ContentScale,
    onTap: (row: Int, column: Int) -> Unit,
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
                    onTap = { tap ->
                        if (scale == 1f && size.width > 0 && size.height > 0) {
                            val column = (tap.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                            val row = (tap.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                            onTap(row, column)
                        }
                    },
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
private fun UnifiedViewerSettingsDialog(
    preferences: ViewerPreferences,
    onChange: (ViewerPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("리더 설정") },
        text = {
            ViewerSettingsContent(
                preferences = preferences,
                onChange = onChange,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
    )
}

@Composable
internal fun ViewerSettingsContent(
    preferences: ViewerPreferences,
    onChange: (ViewerPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tapEditorVisible by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsHeading("화면 맞춤")
        ViewerScale.entries.forEach { value ->
            SettingsRadio(
                label = if (value == ViewerScale.FIT_SCREEN) "전체 페이지" else "너비 맞춤",
                selected = preferences.scale == value,
            ) { onChange(preferences.copy(scale = value)) }
        }

        SettingsHeading("읽는 방향")
        ViewerReadingDirection.entries.forEach { value ->
            SettingsRadio(
                label = if (value == ViewerReadingDirection.LEFT_TO_RIGHT) {
                    "왼쪽 → 오른쪽"
                } else {
                    "오른쪽 → 왼쪽"
                },
                selected = preferences.readingDirection == value,
            ) {
                if (preferences.readingDirection != value) {
                    onChange(
                        preferences.copy(
                            readingDirection = value,
                            tapZones = preferences.tapZones.swapPreviousAndNext(),
                        ),
                    )
                }
            }
        }

        SettingsHeading("페이지 넘기기")
        ViewerPageTurnMode.entries.forEach { value ->
            SettingsRadio(
                label = when (value) {
                    ViewerPageTurnMode.SWIPE_AND_TAP -> "슬라이드 + 터치"
                    ViewerPageTurnMode.SWIPE_ONLY -> "슬라이드만"
                    ViewerPageTurnMode.TAP_ONLY -> "터치만"
                },
                selected = preferences.pageTurnMode == value,
            ) { onChange(preferences.copy(pageTurnMode = value)) }
        }

        SettingsHeading("터치 영역")
        ReaderSettingSwitch("9분할 사용자 설정", preferences.customTapZonesEnabled) { enabled ->
            onChange(
                preferences.copy(
                    customTapZonesEnabled = enabled,
                    tapZones = preferences.tapZones.customActionsOnly(),
                ),
            )
            tapEditorVisible = enabled
        }
        if (preferences.customTapZonesEnabled) {
            Text(
                "이전·없음·다음 동작을 화면의 실제 위치에 배치합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { tapEditorVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("터치 영역 편집") }
        }

        ReaderSettingSwitch("페이지 번호 표시", preferences.showPageNumber) {
            onChange(preferences.copy(showPageNumber = it))
        }
        ReaderSettingSwitch("썸네일 숨기기", preferences.hideThumbnails) {
            onChange(preferences.copy(hideThumbnails = it))
        }
        ReaderSettingSwitch("화면 꺼짐 방지", preferences.keepScreenOn) {
            onChange(preferences.copy(keepScreenOn = it))
        }
    }

    if (tapEditorVisible && preferences.customTapZonesEnabled) {
        TouchZoneEditor(
            zones = preferences.tapZones,
            onChange = { zones -> onChange(preferences.copy(tapZones = zones)) },
            onDismiss = { tapEditorVisible = false },
        )
    }
}

@Composable
private fun TouchZoneEditor(
    zones: ViewerTapZones,
    onChange: (ViewerTapZones) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(3) { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        repeat(3) { column ->
                            val index = row * 3 + column
                            val action = zones.actionAt(index).asCustomAction()
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize()
                                    .background(action.zoneColor().copy(alpha = 0.76f))
                                    .border(1.dp, Color.White.copy(alpha = 0.55f))
                                    .clickable {
                                        onChange(zones.withAction(index, action.nextCustomAction()))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    action.shortLabel(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "영역을 눌러 이전 · 없음 · 다음 변경",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) { Text("완료", color = Color.White) }
            }
        }
    }
}

private fun ViewerTapZones.customActionsOnly(): ViewerTapZones = ViewerTapZones(
    actions = actions.map(ViewerTapAction::asCustomAction),
)

private fun ViewerTapAction.asCustomAction(): ViewerTapAction =
    if (this == ViewerTapAction.TOGGLE_CONTROLS) ViewerTapAction.NONE else this

private fun ViewerTapAction.nextCustomAction(): ViewerTapAction = when (asCustomAction()) {
    ViewerTapAction.PREVIOUS_PAGE -> ViewerTapAction.NONE
    ViewerTapAction.NONE -> ViewerTapAction.NEXT_PAGE
    ViewerTapAction.NEXT_PAGE -> ViewerTapAction.PREVIOUS_PAGE
    ViewerTapAction.TOGGLE_CONTROLS -> ViewerTapAction.NONE
}

private fun ViewerTapAction.zoneColor(): Color = when (asCustomAction()) {
    ViewerTapAction.PREVIOUS_PAGE -> Color(0xFFD32F2F)
    ViewerTapAction.NONE -> Color(0xFF616161)
    ViewerTapAction.NEXT_PAGE -> Color(0xFF00897B)
    ViewerTapAction.TOGGLE_CONTROLS -> Color(0xFF616161)
}

private fun ViewerTapAction.shortLabel(): String = when (this) {
    ViewerTapAction.PREVIOUS_PAGE -> "이전"
    ViewerTapAction.NEXT_PAGE -> "다음"
    ViewerTapAction.TOGGLE_CONTROLS -> "메뉴"
    ViewerTapAction.NONE -> "없음"
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SettingsRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ReaderSettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReaderKeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
