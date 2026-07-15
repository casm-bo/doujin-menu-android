package com.doujinmenu.android.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.model.DesktopProfile

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
) {
    val enabled = !state.isBusy && !state.isLoadingPage
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("데스크톱 연결") {
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
                    label = { Text("장치 이름") },
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
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onTestConnection, enabled = enabled) { Text("상태 확인") }
                    Button(
                        onClick = onPair,
                        enabled = enabled && state.pairingCode.length == 6,
                    ) { Text("페어링") }
                }
            }
        }

        if (state.profiles.isNotEmpty()) {
            item {
                SavedDesktopsCard(
                    profiles = state.profiles,
                    selectedProfileId = state.selectedProfileId,
                    enabled = enabled,
                    onSelect = onSelectProfile,
                    onRemove = onRemoveProfile,
                )
            }
        }

        state.message?.let { message -> item { MessageCard(message, state.isError) } }
        if (state.isBusy) item { LoadingRow("연결 중…") }
    }
}

@Composable
private fun SavedDesktopsCard(
    profiles: List<DesktopProfile>,
    selectedProfileId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    SectionCard("저장된 데스크톱") {
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
