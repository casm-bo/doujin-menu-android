package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinmenu.android.model.DownloadQueueItem
import com.doujinmenu.android.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DownloadsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onRefresh: (Boolean) -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    onConnect: () -> Unit,
    onLeave: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose(onLeave)
    }

    if (state.profiles.none { it.id == state.selectedProfileId }) {
        ConnectionRequiredScreen(contentPadding, onConnect)
        return
    }
    if (state.isDownloadConnectionUnavailable) {
        ConnectionRequiredScreen(
            contentPadding = contentPadding,
            onConnect = onConnect,
            title = "PC에 연결할 수 없습니다",
            description = "등록된 PC가 응답하지 않습니다. 연결 설정을 확인한 뒤 PC를 다시 선택해주세요.",
            buttonLabel = "연결 설정",
        )
        return
    }

    LaunchedEffect(state.selectedProfileId, state.isDownloadConnectionUnavailable) {
        onRefresh(false)
        while (isActive) {
            delay(DOWNLOAD_REFRESH_INTERVAL_MS)
            onRefresh(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val activeCount = state.downloadQueue.count {
                it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING
            }
            Text(
                "전체 ${state.downloadQueue.size} · 진행 중 $activeCount",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onClearCompleted,
                enabled = state.downloadQueue.any { it.status == DownloadStatus.COMPLETED },
            ) { Text("완료 항목 정리") }
            OutlinedButton(
                onClick = { onRefresh(false) },
                enabled = !state.isDownloadQueueLoading,
            ) { Text("새로고침") }
        }

        state.downloadQueueError?.let { error ->
            Text(
                error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.downloadQueue.any { it.status == DownloadStatus.COMPLETED }) {
            Text(
                "완료 항목을 정리해도 PC 라이브러리의 파일은 유지됩니다.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when {
            state.isDownloadQueueLoading && state.downloadQueue.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.downloadQueue.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("다운로드 큐가 비어 있습니다.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "갤러리 상세 화면에서 데스크톱 다운로드를 추가할 수 있습니다.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.downloadQueue, key = { it.id }) { item ->
                        DownloadQueueCard(
                            item = item,
                            actionInProgress = item.id in state.activeDownloadActionIds,
                            onPause = { onPause(item.id) },
                            onResume = { onResume(item.id) },
                            onRetry = { onRetry(item.id) },
                            onRemove = { onRemove(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadQueueCard(
    item: DownloadQueueItem,
    actionInProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(item.thumbnailUrl, item.galleryId) {
        item.thumbnailUrl?.let {
            hitomiImageRequest(context, it, item.galleryId, crossfade = false)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = item.galleryTitle,
                modifier = Modifier.size(width = 76.dp, height = 104.dp),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.galleryTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.galleryArtist?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.status.label,
                        modifier = Modifier.weight(1f),
                        color = item.status.statusColor(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${item.downloadedFiles}/${item.totalFiles} · ${item.progress}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                if (item.status == DownloadStatus.DOWNLOADING && item.downloadSpeed > 0) {
                    Text(
                        "${formatBytes(item.downloadSpeed)}/s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> DownloadActionButton("일시정지", actionInProgress, onPause)
                        DownloadStatus.PAUSED -> DownloadActionButton("재개", actionInProgress, onResume)
                        DownloadStatus.FAILED -> DownloadActionButton("재시도", actionInProgress, onRetry)
                        else -> Unit
                    }
                    TextButton(
                        onClick = onRemove,
                        enabled = !actionInProgress && item.status != DownloadStatus.DOWNLOADING,
                    ) {
                        Text(
                            if (item.status == DownloadStatus.COMPLETED) "완료"
                            else "취소 및 삭제",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    label: String,
    actionInProgress: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = !actionInProgress) {
        Text(if (actionInProgress) "처리 중…" else label)
    }
}

@Composable
private fun DownloadStatus.statusColor() = when (this) {
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val DownloadStatus.label: String
    get() = when (this) {
        DownloadStatus.PENDING -> "대기 중"
        DownloadStatus.DOWNLOADING -> "다운로드 중"
        DownloadStatus.COMPLETED -> "완료"
        DownloadStatus.FAILED -> "실패"
        DownloadStatus.PAUSED -> "일시정지"
        DownloadStatus.UNKNOWN -> "알 수 없음"
    }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private const val DOWNLOAD_REFRESH_INTERVAL_MS = 2_000L
