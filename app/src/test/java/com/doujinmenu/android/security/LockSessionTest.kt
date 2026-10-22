package com.doujinmenu.android.security

import androidx.biometric.BiometricPrompt
import org.junit.Assert.*
import org.junit.Test

class LockSessionTest {
    @Test fun autoRequestsOnceAndCancelAllowsManualFallbackWithoutPopupLoop() {
        val session = BiometricSession()
        val first = session.begin(true, true)!!
        assertNull(session.begin(true, true))
        assertNull(session.begin(false, true))
        assertTrue(session.complete(first))
        assertNull(session.begin(true, true))
        val retry = session.begin(false, true)!!
        assertFalse(session.complete(first))
        assertTrue(session.complete(retry))
    }
    @Test fun rotationKeepsActiveRequestWhileBackgroundInvalidatesEveryOldCallback() {
        val session = BiometricSession()
        val first = session.begin(true, true)!!
        assertEquals(first, session.active)
        assertNull(session.begin(true, true))
        session.background()
        assertFalse(session.complete(first))
        val next = session.begin(true, true)!!
        assertNotEquals(first.generation, next.generation)
        assertFalse(session.complete(first))
        assertTrue(session.complete(next))
    }
    @Test fun unavailableSensorDoesNotLoopAndManualRetryCanRecover() {
        val session = BiometricSession()
        assertNull(session.begin(true, false))
        assertNull(session.begin(true, true))
        assertNotNull(session.begin(false, true))
    }
    @Test fun osErrorsSelectFallbackAndKeepTemporaryAndPermanentLockoutsDistinct() {
        listOf(BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON).forEach {
            assertEquals(BiometricFailure.CANCELLED, biometricFailure(it))
        }
        assertEquals(BiometricFailure.TEMPORARY_LOCKOUT, biometricFailure(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(BiometricFailure.PERMANENT_LOCKOUT, biometricFailure(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
        listOf(BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_BIOMETRICS).forEach {
            assertEquals(BiometricFailure.UNAVAILABLE, biometricFailure(it))
        }
        assertEquals(BiometricFailure.OTHER, biometricFailure(BiometricPrompt.ERROR_TIMEOUT))
    }
    @Test fun fifthAppFailureThrottlesAndBoundaryAndResetAreExact() {
        var attempts = LockAttempts()
        repeat(4) { attempts = attempts.failed(100); assertEquals(0L, attempts.remaining(100)) }
        attempts = attempts.failed(100)
        assertEquals(30_000L, attempts.remaining(100))
        assertEquals(1L, attempts.remaining(30_099))
        assertEquals(0L, attempts.remaining(30_100))
        assertEquals(1, attempts.failed(30_101).count)
        assertEquals(0L, LockAttempts().remaining(100))
    }
    @Test fun settingsRequireCurrentCredentialAuthorizationAndRejectExpiredOrLockedSessions() {
        assertFalse(canChangeLock(true, true, 1000, 1))
        assertFalse(canChangeLock(true, false, 1000, 1000))
        assertFalse(canChangeLock(true, false, 1000, 1001))
        assertTrue(canChangeLock(true, false, 1000, 999))
        assertTrue(canChangeLock(false, false, 0, 1))
    }
}
