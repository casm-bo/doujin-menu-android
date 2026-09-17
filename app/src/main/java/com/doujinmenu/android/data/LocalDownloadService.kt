package com.doujinmenu.android.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.doujinmenu.android.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts only following an explicit local-download action; never on boot or reconnect. */
class LocalDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: DownloadRequestStore
    private var worker: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = DownloadRequestStore.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "기기 다운로드", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(0, 0))
        if (worker?.isActive != true) {
            worker = scope.launch {
                try {
                    while (true) {
                        val request = store.requests.value.firstOrNull {
                            it.target == DownloadTarget.LOCAL && it.status == RequestStatus.WAITING
                        } ?: break
                        try {
                            val claimed = withContext(Dispatchers.IO) {
                                var claimed = false
                                store.change(request.id) {
                                    if (it.target == DownloadTarget.LOCAL && it.status == RequestStatus.WAITING) {
                                        claimed = true
                                        it.copy(status = RequestStatus.RUNNING, error = null)
                                    } else it
                                }
                                claimed
                            }
                            if (!claimed) continue
                            val uri = LocalGalleryDownloader(this@LocalDownloadService).download(request) { done, total ->
                                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(done, total))
                            }
                            withContext(Dispatchers.IO) {
                                store.change(request.id) { it.copy(status = RequestStatus.COMPLETED, outputUri = uri, error = null) }
                                stagingDirectory(filesDir, request.id).deleteRecursively()
                            }
                        } catch (error: Exception) {
                            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                                store.change(request.id) {
                                    if (it.status !in setOf(RequestStatus.RUNNING, RequestStatus.PAUSING)) it else it.copy(
                                        status = if (error is CancellationException) RequestStatus.PAUSED else RequestStatus.FAILED,
                                        error = error.message ?: "기기 다운로드에 실패했습니다.",
                                    )
                                }
                            }
                            if (error is CancellationException && !scope.coroutineContext[Job]!!.isActive) throw error
                        }
                    }
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(done: Int, total: Int): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("기기 다운로드")
        .setContentText(if (total == 0) "다운로드 준비 중" else "$done / $total 페이지")
        .setProgress(total, done, total == 0)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        private const val CHANNEL = "local-downloads"
        private const val NOTIFICATION_ID = 101
    }
}
