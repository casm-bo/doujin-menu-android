package com.doujinmenu.android.ui

import com.doujinmenu.android.R

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
    val strings = androidx.compose.ui.platform.LocalResources.current
    val activity = androidx.activity.compose.LocalActivity.current as AppCompatActivity
    var secret by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }
    BackHandler { onBackground() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(32.dp))
            Text(strings[R.string.ui_app_lock], style = MaterialTheme.typography.headlineMedium)
            Text(strings[R.string.ui_unlock_with, (strings[lock.method.labelResource])])
            CredentialInput(lock.method, secret, { secret = it }, !lock.busy)
            (lock.error ?: biometricError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !lock.busy && secret.isNotEmpty(), onClick = { lock.verify(secret) { if (!it) secret = "" } }) {
                Text(if (lock.busy) strings[R.string.ui_checking] else strings[R.string.ui_unlock])
            }
            if (lock.biometric) OutlinedButton(enabled = !lock.busy, onClick = {
                val epoch = lock.generation
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { lock.biometricSuccess(epoch) }
                    override fun onAuthenticationError(code: Int, message: CharSequence) { biometricError = message.toString() }
                })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(strings[R.string.ui_unlock_app])
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(strings[R.string.ui_use, (strings[lock.method.labelResource])]).build())
            }) { Text(strings[R.string.ui_biometric_authentication]) }
        }
    }
}

@Composable
fun AppLockSettings() {
    val strings = androidx.compose.ui.platform.LocalResources.current
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
        Text(if (lock.enabled) strings[R.string.ui_enabled, (strings[lock.method.labelResource])] else strings[R.string.ui_app_lock_is_off])
        Text(strings[R.string.ui_locks_on_launch_and_return_from_background_keep_your_credentia], style = MaterialTheme.typography.bodySmall)
        LockMethod.entries.forEach { method ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !lock.busy, onClick = { request(method.name) }) {
                RadioButton(selected = lock.enabled && lock.method == method, onClick = null)
                Spacer(Modifier.width(8.dp)); Text(strings[method.labelResource])
            }
        }
        if (lock.enabled) TextButton(enabled = !lock.busy, onClick = { request("disable") }) { Text(strings[R.string.ui_disable_app_lock]) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings[R.string.ui_biometric_unlock], modifier = Modifier.weight(1f))
            Switch(checked = lock.biometric, enabled = lock.enabled && !lock.busy && (biometricAvailable || lock.biometric), onCheckedChange = { request("biometric") })
        }
        Text(if (biometricAvailable) strings[R.string.ui_use_biometrics_instead_of_the_configured_pin_password_or_patte] else strings[R.string.ui_first_enroll_supported_biometrics_in_device_settings], style = MaterialTheme.typography.bodySmall)
        lock.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    val action = pending
    if (action != null) {
        val method = if (authenticating) lock.method else LockMethod.entries.firstOrNull { it.name == action }
        if (method != null) AlertDialog(
            onDismissRequest = { if (!lock.busy) dismiss() },
            title = { Text(if (authenticating) strings[R.string.ui_verify_current_credential] else if (repeat) strings[R.string.ui_enter_again, (strings[method.labelResource])] else strings[R.string.ui_set, (strings[method.labelResource])]) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!authenticating) Text(when (method) { LockMethod.PIN -> strings[R.string.ui_4_12_digits]; LockMethod.PASSWORD -> strings[R.string.ui_6_128_characters]; LockMethod.PATTERN -> strings[R.string.ui_connect_at_least_4_dots] })
                CredentialInput(method, if (repeat) confirmation else secret, { if (repeat) confirmation = it else secret = it; mismatch = false }, !lock.busy)
                if (mismatch) Text(strings[R.string.ui_the_two_entries_do_not_match], color = MaterialTheme.colorScheme.error)
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
            }) { Text(if (authenticating) strings[R.string.ui_confirm] else if (repeat) strings[R.string.ui_save] else strings[R.string.ui_next]) } },
            dismissButton = { TextButton(enabled = !lock.busy, onClick = ::dismiss) { Text(strings[R.string.ui_cancel]) } },
        )
    }
}

@Composable
private fun CredentialInput(method: LockMethod, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    if (method == LockMethod.PATTERN) PatternInput(value, onChange, enabled)
    else OutlinedTextField(value = value, onValueChange = { if (it.length <= 128) onChange(it) }, enabled = enabled,
        label = { Text(strings[method.labelResource]) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (method == LockMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PatternInput(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val strings = androidx.compose.ui.platform.LocalResources.current
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
        TextButton(enabled = enabled, onClick = { onChange("") }) { Text(strings[R.string.ui_draw_again]) }
    }
}
