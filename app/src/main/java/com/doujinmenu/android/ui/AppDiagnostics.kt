package com.doujinmenu.android.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import com.doujinmenu.android.BuildConfig

internal fun shareDiagnosticData(context: Context, state: MainUiState) {
    val report = buildDiagnosticReport(context, state)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Doujin Menu Android diagnostic data")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "진단 데이터 보내기"))
}

internal fun openGitHubIssue(context: Context, state: MainUiState) {
    val body = """
        ## 문제 설명

        ## 재현 방법
        1.

        ## 진단 정보
        ```text
        ${buildDiagnosticReport(context, state)}
        ```
    """.trimIndent()
    val uri = Uri.parse("https://github.com/casm-bo/doujin-menu-android/issues/new")
        .buildUpon()
        .appendQueryParameter("title", "[Bug] ")
        .appendQueryParameter("body", body)
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private fun buildDiagnosticReport(context: Context, state: MainUiState): String {
    val orientation = when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        else -> "unknown"
    }
    return buildDiagnosticReport(
        state = state,
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        androidVersion = "${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        orientation = orientation,
    )
}

internal fun buildDiagnosticReport(
    state: MainUiState,
    appVersion: String,
    androidVersion: String,
    device: String,
    orientation: String,
): String = buildString {
    appendLine("App: $appVersion")
    appendLine("Android: $androidVersion")
    appendLine("Device: $device")
    appendLine("Orientation: $orientation")
    appendLine("Connection: ${state.desktopConnectionState}")
    appendLine("Saved desktops: ${state.profiles.size}; selected: ${state.selectedProfileId != null}")
    appendLine("Library books: ${state.libraryBooks.size}; locations: ${state.libraryLocations.size}")
    appendLine("Sync pending: ${state.librarySyncPendingCount}; syncing: ${state.isLibrarySyncing}")
    appendLine("Downloads: ${state.downloadQueue.size}")
    appendLine(
        "Errors present: library=${state.libraryScanError != null}, " +
            "sync=${state.librarySyncError != null}, reader=${state.readerError != null}, " +
            "download=${state.downloadQueueError != null}",
    )
    appendLine("Viewer scale: ${state.viewerPreferences.scale}")
    appendLine("Reading direction: ${state.viewerPreferences.readingDirection}")
    appendLine("Page turn: ${state.viewerPreferences.pageTurnMode}")
}
