package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.doujinmenu.android.security.LockMethod
import com.doujinmenu.android.security.appendPatternPoint
import com.doujinmenu.android.security.validLockSecret
import kotlin.math.roundToInt

val LocalAppLock = staticCompositionLocalOf<AppLockViewModel> { error("Missing app lock controller") }

@Composable
fun AppLockScreen(lock: AppLockViewModel, onBackground: () -> Unit) {
    val activity = LocalContext.current as AppCompatActivity
    var secret by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }
    BackHandler { onBackground() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(32.dp))
            Text("앱 잠금", style = MaterialTheme.typography.headlineMedium)
            Text("${lock.method.label}으로 잠금을 해제하세요.")
            CredentialInput(lock.method, secret, { secret = it }, !lock.busy)
            (lock.error ?: biometricError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !lock.busy && secret.isNotEmpty(), onClick = { lock.verify(secret) { if (!it) secret = "" } }) {
                Text(if (lock.busy) "확인 중…" else "잠금 해제")
            }
            if (lock.biometric) OutlinedButton(enabled = !lock.busy, onClick = {
                val epoch = lock.generation
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { lock.biometricSuccess(epoch) }
                    override fun onAuthenticationError(code: Int, message: CharSequence) { biometricError = message.toString() }
                })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("앱 잠금 해제")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText("${lock.method.label} 사용").build())
            }) { Text("생체인증") }
        }
    }
}

@Composable
fun AppLockSettings() {
    val lock = LocalAppLock.current
    val context = LocalContext.current
    val biometricAvailable = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    var pending by remember { mutableStateOf<String?>(null) }
    var authenticating by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    fun dismiss() { pending = null; authenticating = false; secret = ""; confirmation = ""; repeat = false; mismatch = false; lock.clearError() }
    fun request(action: String) { dismiss(); pending = action; authenticating = lock.enabled }
    fun executeAction() {
        when (pending) {
            "disable" -> lock.disable(::dismiss)
            "biometric" -> lock.setBiometric(!lock.biometric, ::dismiss)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (lock.enabled) "사용 중: ${lock.method.label}" else "앱 잠금 꺼짐")
        Text("앱 실행과 백그라운드 복귀 시 잠급니다. 잠금 정보를 잊으면 앱 데이터 복구가 필요하므로 안전하게 보관하세요.", style = MaterialTheme.typography.bodySmall)
        LockMethod.entries.forEach { method ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !lock.busy, onClick = { request(method.name) }) {
                RadioButton(selected = lock.enabled && lock.method == method, onClick = null)
                Spacer(Modifier.width(8.dp)); Text(method.label)
            }
        }
        if (lock.enabled) TextButton(enabled = !lock.busy, onClick = { request("disable") }) { Text("앱 잠금 해제하기") }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("생체잠금", modifier = Modifier.weight(1f))
            Switch(checked = lock.biometric, enabled = lock.enabled && !lock.busy && (biometricAvailable || lock.biometric), onCheckedChange = { request("biometric") })
        }
        Text(if (biometricAvailable) "설정한 PIN·비밀번호·패턴 대신 생체인증으로 해제합니다." else "지원되는 생체정보를 기기 설정에서 먼저 등록하세요.", style = MaterialTheme.typography.bodySmall)
        lock.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    val action = pending
    if (action != null) {
        val method = if (authenticating) lock.method else LockMethod.entries.firstOrNull { it.name == action }
        if (method != null) AlertDialog(
            onDismissRequest = { if (!lock.busy) dismiss() },
            title = { Text(if (authenticating) "현재 잠금 인증" else if (repeat) "${method.label} 다시 입력" else "${method.label} 설정") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!authenticating) Text(when (method) { LockMethod.PIN -> "숫자 4~12자리"; LockMethod.PASSWORD -> "6~128자"; LockMethod.PATTERN -> "점 4개 이상 연결" })
                CredentialInput(method, if (repeat) confirmation else secret, { if (repeat) confirmation = it else secret = it; mismatch = false }, !lock.busy)
                if (mismatch) Text("두 입력이 일치하지 않습니다.", color = MaterialTheme.colorScheme.error)
                lock.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !lock.busy && (authenticating || validLockSecret(method, if (repeat) confirmation else secret)), onClick = {
                if (authenticating) lock.verify(secret, settings = true) { accepted ->
                    secret = ""
                    if (accepted) { authenticating = false; executeAction() }
                }
                else if (!repeat) repeat = true
                else if (secret != confirmation) { mismatch = true; confirmation = "" }
                else lock.configure(method, secret, ::dismiss)
            }) { Text(if (authenticating) "확인" else if (repeat) "저장" else "다음") } },
            dismissButton = { TextButton(enabled = !lock.busy, onClick = ::dismiss) { Text("취소") } },
        )
    }
}

@Composable
private fun CredentialInput(method: LockMethod, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    if (method == LockMethod.PATTERN) PatternInput(value, onChange, enabled)
    else OutlinedTextField(value = value, onValueChange = { if (it.length <= 128) onChange(it) }, enabled = enabled,
        label = { Text(method.label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (method == LockMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PatternInput(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val update by rememberUpdatedState(onChange)
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(240.dp).pointerInput(enabled) {
            if (enabled) {
                var dragPath = ""
                fun visit(position: Offset) {
                    val cell = size.width / 3f
                    val x = (position.x / cell - .5f).roundToInt()
                    val y = (position.y / cell - .5f).roundToInt()
                    if (x in 0..2 && y in 0..2 && (position - Offset((x + .5f) * cell, (y + .5f) * cell)).getDistance() < cell * .4f) {
                        dragPath = appendPatternPoint(dragPath, y * 3 + x)
                        update(dragPath)
                    }
                }
                detectDragGestures(onDragStart = { dragPath = ""; update(""); visit(it) }, onDrag = { change, _ -> change.consume(); visit(change.position) })
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                fun center(char: Char) = Offset((char.digitToInt() % 3 + .5f) * size.width / 3, (char.digitToInt() / 3 + .5f) * size.height / 3)
                value.zipWithNext().forEach { (a, b) -> drawLine(color, center(a), center(b), 4.dp.toPx()) }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceAround) {
                repeat(3) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    repeat(3) { col -> val point = row * 3 + col
                        FilledTonalIconButton(modifier = Modifier.semantics { contentDescription = "${point + 1}" }, enabled = enabled, onClick = { onChange(appendPatternPoint(value, point)) }) {
                            Text(if (point.digitToChar() in value) "●" else "○", modifier = Modifier, color = color)
                        }
                    }
                } }
            }
        }
        TextButton(enabled = enabled, onClick = { onChange("") }) { Text("다시 그리기") }
    }
}
