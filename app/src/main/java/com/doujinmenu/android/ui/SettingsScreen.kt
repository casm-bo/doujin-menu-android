package com.doujinmenu.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.model.StorageLocation

private enum class SettingsSection {
    HOME,
    VIEWER,
    LIBRARY,
    CONNECTION,
}

@Composable
fun SettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onPair: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onAddLibraryLocation: (String, String) -> Unit,
    onRemoveLibraryLocation: (String) -> Unit,
    onSetDownloadLocation: (String, String) -> Unit,
    onClearDownloadLocation: () -> Unit,
    openConnectionRequested: Boolean,
    onConnectionRequestHandled: () -> Unit,
) {
    var sectionName by rememberSaveable { mutableStateOf(SettingsSection.HOME.name) }
    val section = SettingsSection.valueOf(sectionName)
    val openSection: (SettingsSection) -> Unit = { sectionName = it.name }

    BackHandler(enabled = section != SettingsSection.HOME) {
        openSection(SettingsSection.HOME)
    }

    LaunchedEffect(openConnectionRequested) {
        if (openConnectionRequested) {
            openSection(SettingsSection.CONNECTION)
            onConnectionRequestHandled()
        }
    }

    when (section) {
        SettingsSection.HOME -> SettingsHomeScreen(
            state = state,
            contentPadding = contentPadding,
            onOpenSection = openSection,
        )
        SettingsSection.VIEWER -> ViewerSettingsScreen(
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
        )
        SettingsSection.LIBRARY -> LibrarySettingsScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onAddLibraryLocation = onAddLibraryLocation,
            onRemoveLibraryLocation = onRemoveLibraryLocation,
            onSetDownloadLocation = onSetDownloadLocation,
            onClearDownloadLocation = onClearDownloadLocation,
        )
        SettingsSection.CONNECTION -> ConnectionSettingsScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onDeviceNameChange = onDeviceNameChange,
            onCodeChange = onCodeChange,
            onTestConnection = onTestConnection,
            onPair = onPair,
            onSelectProfile = onSelectProfile,
            onRemoveProfile = onRemoveProfile,
        )
    }
}

@Composable
private fun SettingsHomeScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onOpenSection: (SettingsSection) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCategoryCard(
                title = "뷰어 설정",
                description = "읽기 방향, 페이지 표시, 화면 맞춤과 제스처",
                onClick = { onOpenSection(SettingsSection.VIEWER) },
            )
        }
        item {
            SettingsCategoryCard(
                title = "라이브러리 설정",
                description = "라이브러리 경로 ${state.libraryLocations.size}개 · 다운로드 경로 " +
                    if (state.downloadLocation == null) "미설정" else "설정됨",
                onClick = { onOpenSection(SettingsSection.LIBRARY) },
            )
        }
        item {
            SettingsCategoryCard(
                title = "연결 설정",
                description = "선택적 PC 연결 및 동기화 · 등록된 PC ${state.profiles.size}개",
                onClick = { onOpenSection(SettingsSection.CONNECTION) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ViewerSettingsScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    SettingsDetailLayout("뷰어 설정", contentPadding, onBack) {
        SectionCard("뷰어") {
            Text(
                "뷰어 세부 설정은 다음 요구사항에 맞춰 이 화면에 추가됩니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibrarySettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddLibraryLocation: (String, String) -> Unit,
    onRemoveLibraryLocation: (String) -> Unit,
    onSetDownloadLocation: (String, String) -> Unit,
    onClearDownloadLocation: () -> Unit,
) {
    val context = LocalContext.current
    val libraryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            persistTreePermission(context, it)
            onAddLibraryLocation(it.toString(), treeDisplayName(context, it))
        }
    }
    val downloadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            persistTreePermission(context, it)
            onSetDownloadLocation(it.toString(), treeDisplayName(context, it))
        }
    }

    SettingsDetailLayout("라이브러리 설정", contentPadding, onBack) {
        SectionCard("라이브러리 경로") {
            Text(
                "책을 검색할 폴더를 여러 개 등록할 수 있습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            state.libraryLocations.forEachIndexed { index, location ->
                if (index > 0) HorizontalDivider()
                StorageLocationRow(location, "제거") {
                    onRemoveLibraryLocation(location.uri)
                }
            }
            Button(
                onClick = { libraryPicker.launch(null) },
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("라이브러리 경로 추가") }
        }

        SectionCard("다운로드 경로") {
            Text(
                if (state.selectedProfileId != null) {
                    "데스크톱 연결 중에는 PC 다운로드가 데스크톱 경로에 저장됩니다. " +
                        "데스크톱 경로는 PC에서만 변경할 수 있으며, 모바일 로컬 경로는 별도로 설정할 수 있습니다."
                } else {
                    "데스크톱에 연결되지 않은 상태에서는 모바일 로컬 다운로드 경로만 사용합니다."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectedProfileId != null) {
                Spacer(Modifier.height(10.dp))
                ReadOnlyPathRow(
                    title = "데스크톱 다운로드 경로",
                    path = state.desktopDownloadPath ?: "데스크톱에서 설정되지 않음",
                )
            }
            state.downloadLocation?.let { location ->
                Spacer(Modifier.height(10.dp))
                StorageLocationRow(
                    location = location,
                    actionLabel = "삭제",
                    title = "로컬 다운로드 경로",
                    onAction = onClearDownloadLocation,
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { downloadPicker.launch(null) }) {
                    Text(if (state.downloadLocation == null) "다운로드 경로 선택" else "경로 변경")
                }
            }
        }
    }
}

@Composable
private fun StorageLocationRow(
    location: StorageLocation,
    actionLabel: String,
    title: String? = null,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(location.displayName, fontWeight = FontWeight.Medium)
            Text(
                location.uri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ReadOnlyPathRow(title: String, path: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(path, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "데스크톱 앱에서만 변경할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionSettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onPair: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
) {
    val enabled = !state.isBusy && !state.isLoadingPage
    SettingsDetailLayout("연결 설정", contentPadding, onBack) {
        SectionCard("연결 및 동기화") {
            Text(
                "PC 연결은 선택 사항이며 독립 리더 기능에는 영향을 주지 않습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("IP 또는 호스트") },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    enabled = enabled,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    modifier = Modifier.width(112.dp),
                    label = { Text("포트") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = onDeviceNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("앱 표시 이름") },
                placeholder = { Text("예: 내 데스크톱") },
                singleLine = true,
                enabled = enabled,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.pairingCode,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("6자리 페어링 코드") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onTestConnection, enabled = enabled) { Text("상태 확인") }
                Button(
                    onClick = onPair,
                    enabled = enabled && state.pairingCode.length == 6 && state.deviceName.isNotBlank(),
                ) {
                    Text("페어링")
                }
            }
        }

        if (state.profiles.isNotEmpty()) {
            SectionCard("연결된 PC") {
                SavedDesktopRows(
                    profiles = state.profiles,
                    selectedProfileId = state.selectedProfileId,
                    enabled = enabled,
                    onSelect = onSelectProfile,
                    onRemove = onRemoveProfile,
                )
            }
        }
        state.message?.takeUnless { state.isError }?.let { MessageCard(it, false) }
        if (state.isBusy) LoadingRow("연결 중…")
    }
}

@Composable
private fun SettingsDetailLayout(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("<") }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SavedDesktopRows(
    profiles: List<DesktopProfile>,
    selectedProfileId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    profiles.forEachIndexed { index, profile ->
        if (index > 0) HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = profile.id == selectedProfileId,
                onClick = { onSelect(profile.id) },
                enabled = enabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Medium)
                Text(
                    profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onRemove(profile.id) }, enabled = enabled) { Text("삭제") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun persistTreePermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
}

private fun treeDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val fromProvider = runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':') }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "선택한 폴더"
}
