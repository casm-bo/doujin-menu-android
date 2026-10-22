package com.doujinzip.android.security

import androidx.biometric.BiometricPrompt

data class BiometricRequest(val generation: Long, val attempt: Long)
enum class BiometricFailure { CANCELLED, TEMPORARY_LOCKOUT, PERMANENT_LOCKOUT, UNAVAILABLE, OTHER }

fun biometricFailure(code: Int): BiometricFailure = when (code) {
    BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricFailure.CANCELLED
    BiometricPrompt.ERROR_LOCKOUT -> BiometricFailure.TEMPORARY_LOCKOUT
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricFailure.PERMANENT_LOCKOUT
    BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricFailure.UNAVAILABLE
    else -> BiometricFailure.OTHER
}

class BiometricSession {
    var generation = 0L; private set
    var active: BiometricRequest? = null; private set
    private var automaticAttempted = false
    private var attempt = 0L
    fun background() { generation++; active = null; automaticAttempted = false }
    fun begin(automatic: Boolean, available: Boolean): BiometricRequest? {
        if (active != null || (automatic && automaticAttempted)) return null
        if (automatic) automaticAttempted = true
        if (!available) return null
        return BiometricRequest(generation, ++attempt).also { active = it }
    }
    fun complete(request: BiometricRequest): Boolean {
        if (active != request || request.generation != generation) return false
        active = null
        return true
    }
    fun cancel() { active = null }
}

data class LockAttempts(val count: Int = 0, val blockedUntil: Long = 0) {
    fun remaining(now: Long): Long = (blockedUntil - now).coerceIn(0, 30_000)
    fun failed(now: Long): LockAttempts = if (count >= 4) LockAttempts(0, now + 30_000) else LockAttempts(count + 1)
}

fun canChangeLock(enabled: Boolean, locked: Boolean, authorizedUntil: Long, now: Long): Boolean =
    !enabled || (!locked && now < authorizedUntil)
