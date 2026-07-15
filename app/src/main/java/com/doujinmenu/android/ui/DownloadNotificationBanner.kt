package com.doujinmenu.android.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DownloadNotificationBanner(
    notification: DownloadNotification,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    var dragX by remember(notification.galleryId) { mutableFloatStateOf(0f) }
    var dragY by remember(notification.galleryId) { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .zIndex(10f)
            .statusBarsPadding()
            .padding(12.dp)
            .fillMaxWidth()
            .pointerInput(notification.galleryId) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(dragX) > size.width * 0.25f || dragY < -size.height * 0.25f) {
                            onDismiss()
                        } else {
                            dragX = 0f
                            dragY = 0f
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount.x
                    dragY = (dragY + dragAmount.y).coerceAtMost(0f)
                }
            }
            .then(
                Modifier.offset {
                    IntOffset(dragX.roundToInt(), dragY.roundToInt())
                },
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.galleryTitle, style = MaterialTheme.typography.titleSmall)
                Text("다운로드 완료", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onOpen) { Text("열기") }
        }
    }
}
