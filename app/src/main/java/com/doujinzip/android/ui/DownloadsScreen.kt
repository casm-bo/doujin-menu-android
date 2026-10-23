package com.doujinzip.android.ui

import androidx.compose.foundation.background
import com.doujinzip.android.R
import com.doujinzip.android.model.readableStoragePath

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.doujinzip.android.data.DownloadTarget
import com.doujinzip.android.data.RequestStatus
import androidx.compose.foundation.layout.FlowRow
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
import com.doujinzip.android.model.DownloadQueueItem
import com.doujinzip.android.model.DownloadStatus
import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.LibraryBook
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
    onRemoveRequest: (String) -> Unit,
    onLocalDownload: (String) -> Unit,
    onPauseLocal: (String) -> Unit,
    onSetDownloadLocation: (String, String) -> Unit,
    onSyncRequests: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val granted = runCatching { context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }.isSuccess
            if (granted) onSetDownloadLocation(uri.toString(), DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':'))
            else android.widget.Toast.makeText(context, strings[R.string.ui_could_not_save_folder_access_permission], android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val originalTitles = remember(state.galleryCache, state.galleries, state.libraryBooks) {
        downloadOriginalTitles(state.galleryCache.values + state.galleries, state.libraryBooks)
    }
    DisposableEffect(Unit) {
        onDispose(onLeave)
    }

    val visibleRequests = state.downloadRequests.filterNot { request ->
        request.status == RequestStatus.SENT && request.profileId == state.selectedProfileId &&
            state.downloadQueue.any { it.galleryId == request.galleryId }
    }
    val hasProfile = state.profiles.any { it.id == state.selectedProfileId }
    LaunchedEffect(state.selectedProfileId) {
        if (!hasProfile) return@LaunchedEffect
        onRefresh(false)
        while (isActive) {
            delay(DOWNLOAD_REFRESH_INTERVAL_MS)
            onRefresh(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).background(MaterialTheme.colorScheme.surface),
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
                strings[R.string.ui_total_pc_active, (state.downloadQueue.size + visibleRequests.size), (activeCount)],
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onClearCompleted,
                enabled = state.downloadQueue.any { it.status == DownloadStatus.COMPLETED },
            ) { Text(strings[R.string.ui_clear_completed_items]) }
            OutlinedButton(
                onClick = { onRefresh(false) },
                enabled = hasProfile && !state.isDownloadQueueLoading,
            ) { Text(strings[R.string.ui_refresh]) }
        }

        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings[R.string.ui_pc_pending, (state.downloadRequests.count { it.target == DownloadTarget.DESKTOP && !it.isFinished })], Modifier.weight(1f))
            TextButton(onClick = onSyncRequests, enabled = hasProfile && !state.isDownloadSyncing &&
                state.downloadRequests.any { it.pendingForDesktop(state.selectedProfileId.orEmpty()) }) {
                Text(if (state.isDownloadSyncing) strings[R.string.ui_syncing] else strings[R.string.ui_sync_with_pc])
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings[R.string.ui_device_storage, (state.downloadLocation?.let { readableStoragePath(it.uri, it.displayName) } ?: strings[R.string.download_folder_unset])], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { folderPicker.launch(null) }) { Text(strings[R.string.ui_choose_folder]) }
        }
        Text(strings[R.string.ui_desktop_download_folder] + ": " + (state.desktopDownloadPath?.let { readableStoragePath(it) }
            ?: strings[R.string.ui_not_configured_on_desktop]), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (!hasProfile || state.isDownloadConnectionUnavailable) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings[R.string.ui_pc_disconnected_pending_requests_are_stored_on_this_device], Modifier.weight(1f))
                TextButton(onClick = onConnect) { Text(strings[R.string.ui_connection_settings]) }
            }
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
                strings[R.string.ui_clearing_completed_items_keeps_files_in_the_pc_library],
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when {
            state.isDownloadQueueLoading && state.downloadQueue.isEmpty() && state.downloadRequests.isEmpty() -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.downloadQueue.isEmpty() && state.downloadRequests.isEmpty() -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings[R.string.ui_the_download_queue_is_empty], style = MaterialTheme.typography.titleMedium)
                        Text(
                            strings[R.string.ui_add_downloads_from_a_gallery_detail_screen],
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleRequests, key = { "request:${it.id}" }) { request ->
                        Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(preferredLocalizedTitle(request.title), fontWeight = FontWeight.SemiBold)
                                val local = request.target == DownloadTarget.LOCAL
                                Text(when (request.status) {
                                    RequestStatus.WAITING -> if (local) strings[R.string.ui_waiting_for_device_download] else strings[R.string.ui_waiting_for_pc_sync]
                                    RequestStatus.SENDING -> strings[R.string.ui_sending_to_pc]
                                    RequestStatus.SENT -> strings[R.string.ui_sent_to_pc_queue]
                                    RequestStatus.RUNNING -> strings[R.string.ui_downloading_to_device]
                                    RequestStatus.PAUSING -> strings[R.string.ui_pausing]
                                    RequestStatus.PAUSED -> strings[R.string.ui_device_download_paused]
                                    RequestStatus.FAILED -> strings[R.string.ui_device_download_failed]
                                    RequestStatus.COMPLETED -> strings[R.string.ui_saved_on_device]
                                })
                                if (local) {
                                    Text(strings[R.string.ui_pages_cb9b7a, (request.downloadedFiles), (request.totalFiles)], style = MaterialTheme.typography.bodySmall)
                                    if (request.totalFiles > 0) LinearProgressIndicator(
                                        progress = { request.downloadedFiles.toFloat() / request.totalFiles }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    val desktop = state.profiles.firstOrNull { it.id == request.profileId }
                                    Text(strings[R.string.ui_target, (desktop?.name ?: if (request.profileId == null) strings[R.string.next_sync_pc] else strings[R.string.removed_pc])], style = MaterialTheme.typography.bodySmall)
                                }
                                if (request.desktopAttempted && !request.isFinished && !request.isBusy) {
                                    Text(strings[R.string.ui_check_transfer_status_sync_with_pc], style = MaterialTheme.typography.bodySmall)
                                }
                                request.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (request.canDownloadLocally) {
                                        TextButton(onClick = { onLocalDownload(request.id) }, enabled = state.downloadLocation != null) {
                                            Text(if (local) strings[R.string.ui_resume_device_download] else strings[R.string.ui_add_local_download])
                                        }
                                    }
                                    if (local && (request.status == RequestStatus.RUNNING || request.status == RequestStatus.WAITING)) {
                                        TextButton(onClick = { onPauseLocal(request.id) }) { Text(strings[R.string.ui_pause]) }
                                    }
                                    TextButton(onClick = { onRemoveRequest(request.id) }, enabled = !request.isBusy) {
                                        Text(if (request.isFinished || request.desktopAttempted) strings[R.string.ui_clear_record] else strings[R.string.ui_cancel_and_delete])
                                    }
                                }
                            }
                        }
                    }
                    items(state.downloadQueue, key = { "desktop:${it.id}" }) { item ->
                        DownloadQueueCard(
                            item = item,
                            displayTitle = originalTitles[item.galleryId] ?: item.galleryTitle,
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
    displayTitle: String,
    actionInProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val thumbnailRequest = remember(item.thumbnailUrl, item.galleryId) {
        item.thumbnailUrl?.let {
            hitomiImageRequest(context, it, item.galleryId, crossfade = false)
        }
    }
    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = displayTitle,
                modifier = Modifier.size(width = 76.dp, height = 104.dp),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    preferredLocalizedTitle(displayTitle),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.galleryArtist?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings[item.status.labelResource],
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
                        DownloadStatus.DOWNLOADING -> DownloadActionButton(strings[R.string.ui_pause], actionInProgress, onPause)
                        DownloadStatus.PAUSED -> DownloadActionButton(strings[R.string.ui_resume], actionInProgress, onResume)
                        DownloadStatus.FAILED -> DownloadActionButton(strings[R.string.ui_retry], actionInProgress, onRetry)
                        else -> Unit
                    }
                    TextButton(
                        onClick = onRemove,
                        enabled = !actionInProgress && item.status != DownloadStatus.DOWNLOADING,
                    ) {
                        Text(
                            if (item.status == DownloadStatus.COMPLETED) strings[R.string.ui_completed]
                            else strings[R.string.ui_cancel_and_delete],
                        )
                    }
                }
            }
        }
    }
}

internal fun downloadOriginalTitles(
    galleries: Collection<GallerySummary>,
    books: List<LibraryBook>,
): Map<Long, String> = buildMap {
    books.forEach { book ->
        book.metadata.hitomiId?.toLongOrNull()?.let { galleryId ->
            book.originalTitle.takeIf(String::isNotBlank)?.let { put(galleryId, it) }
        }
    }
    galleries.forEach { gallery ->
        gallery.title.takeIf(String::isNotBlank)?.let { put(gallery.id, it) }
    }
}

@Composable
private fun DownloadActionButton(
    label: String,
    actionInProgress: Boolean,
    onClick: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Button(onClick = onClick, enabled = !actionInProgress) {
        Text(if (actionInProgress) strings[R.string.ui_processing] else label)
    }
}

@Composable
private fun DownloadStatus.statusColor() = when (this) {
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val DownloadStatus.labelResource: Int
    get() = when (this) {
        DownloadStatus.PENDING -> R.string.ui_waiting
        DownloadStatus.DOWNLOADING -> R.string.ui_downloading
        DownloadStatus.COMPLETED -> R.string.ui_completed
        DownloadStatus.FAILED -> R.string.ui_failed
        DownloadStatus.PAUSED -> R.string.ui_pause
        DownloadStatus.UNKNOWN -> R.string.ui_unknown
    }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private const val DOWNLOAD_REFRESH_INTERVAL_MS = 2_000L
