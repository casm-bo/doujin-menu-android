package com.doujinmenu.android.ui

import com.doujinmenu.android.R

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doujinmenu.android.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val strings get() = androidx.core.content.ContextCompat.getContextForLanguage(getApplication()).resources
    private val store = AppLockStore(application)
    private var credential = runCatching { store.read() }.getOrNull()
    var enabled by mutableStateOf(store.enabled); private set
    var locked by mutableStateOf(enabled); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(if (enabled && credential == null) strings[R.string.msg_cannot_read_lock_data_app_data_must_be_reset_to_recove] else null); private set
    var method by mutableStateOf(credential?.method ?: LockMethod.PIN); private set
    var biometric by mutableStateOf(credential?.biometric == true); private set
    var generation = 0L; private set
    private var authorizedUntil = 0L

    fun onBackground() { generation++; authorizedUntil = 0; if (enabled) locked = true }
    fun clearError() { error = null }
    fun verify(secret: String, settings: Boolean = false, completed: (Boolean) -> Unit = {}) {
        if (busy || secret.length > 128) return
        val epoch = generation
        busy = true
        viewModelScope.launch {
            val success = runCatching { withContext(Dispatchers.IO) {
                check(store.remainingDelay(System.currentTimeMillis()) == 0L) { strings[R.string.msg_wait_a_moment_before_trying_again] }
                val valid = credential?.matches(secret) == true
                if (valid) store.resetAttempts() else store.failed(System.currentTimeMillis())
                valid
            } }
            busy = false
            if (epoch != generation) return@launch
            val accepted = success.getOrDefault(false)
            error = if (accepted) null else success.exceptionOrNull()?.message ?: strings[R.string.msg_incorrect_credential]
            if (accepted) { if (settings) authorizedUntil = SystemClock.elapsedRealtime() + 60_000 else locked = false }
            completed(accepted)
        }
    }
    fun biometricSuccess(epoch: Long) {
        if (enabled && biometric && epoch == generation && credential != null) { locked = false; error = null }
    }
    fun configure(method: LockMethod, secret: String, completed: () -> Unit) = change(completed) {
        LockCredential.create(method, secret).copy(biometric = biometric)
    }
    fun setBiometric(value: Boolean, completed: () -> Unit) = change(completed) { requireNotNull(credential).copy(biometric = value) }
    fun disable(completed: () -> Unit) = change(completed) { null }
    private fun change(completed: () -> Unit, create: () -> LockCredential?) {
        if (busy) return
        if (enabled && (locked || SystemClock.elapsedRealtime() >= authorizedUntil)) { error = strings[R.string.msg_verify_your_current_credential_first]; return }
        val epoch = generation
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { create().also { if (it == null) store.clear() else store.save(it) } } }
            busy = false
            result.onSuccess { value ->
                credential = value; enabled = value != null; method = value?.method ?: LockMethod.PIN
                biometric = value?.biometric == true; locked = enabled && epoch != generation
                authorizedUntil = 0; error = null
                completed()
            }.onFailure { error = it.message ?: strings[R.string.msg_could_not_save_lock_settings] }
        }
    }
}
