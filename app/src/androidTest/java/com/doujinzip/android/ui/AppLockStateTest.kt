package com.doujinzip.android.ui

import android.app.Application
import androidx.biometric.BiometricPrompt
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.security.*
import org.junit.Assert.*
import org.junit.Test

class AppLockStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun validBiometricResetsAppFailuresButSettingsStillNeedAppCredential() {
        val store = AppLockStore(context, "app_lock_success_test")
        try {
            store.save(LockCredential.create(LockMethod.PIN, "142857").copy(biometric = true))
            repeat(4) { store.failed(System.currentTimeMillis()) }
            lateinit var lock: AppLockViewModel
            instrumentation.runOnMainSync {
                lock = AppLockViewModel(context.applicationContext as Application, store)
                lock.biometricSuccess(lock.beginBiometric(true, true)!!)
            }
            val deadline = System.currentTimeMillis() + 5000
            var unlocked = false
            while (!unlocked && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { unlocked = !lock.locked && !lock.busy }
                if (!unlocked) Thread.sleep(20)
            }
            assertTrue(unlocked)
            store.failed(System.currentTimeMillis())
            assertEquals(0L, store.remainingDelay(System.currentTimeMillis()))
            val saved = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                lock.disable { fail("Biometric must not authorize settings") }
                assertTrue(lock.enabled)
                lock.verify("142857", settings = true) { valid ->
                    assertTrue(valid)
                    lock.setBiometric(false) { saved.countDown() }
                }
            }
            assertTrue(saved.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(store.read()!!.biometric)
        } finally { store.clear() }
    }

    @Test fun attemptsSurviveStoreRecreationAndSuccessResetsThem() {
        val name = "app_lock_attempt_test"
        val store = AppLockStore(context, name)
        try {
            store.clear()
            repeat(5) { store.failed(1000) }
            val reopened = AppLockStore(context, name)
            assertEquals(30_000L, reopened.remainingDelay(1000))
            assertEquals(1L, reopened.remainingDelay(30_999))
            assertEquals(0L, reopened.remainingDelay(31_000))
            reopened.resetAttempts()
            assertEquals(0L, AppLockStore(context, name).remainingDelay(1000))
        } finally { store.clear() }
    }

    @Test fun mockedCallbacksNeverUnlockAfterCancelOrBackgroundAndCannotAuthorizeSettings() {
        val store = AppLockStore(context, "app_lock_callback_test")
        try {
            store.save(LockCredential.create(LockMethod.PIN, "142857").copy(biometric = true))
            instrumentation.runOnMainSync {
                val lock = AppLockViewModel(context.applicationContext as Application, store)
                val first = lock.beginBiometric(true, true)!!
                lock.biometricError(first, BiometricPrompt.ERROR_NEGATIVE_BUTTON, "cancel")
                lock.biometricSuccess(first)
                assertTrue(lock.locked)
                assertNull(lock.beginBiometric(true, true))
                val retry = lock.beginBiometric(false, true)!!
                lock.onBackground()
                lock.biometricSuccess(retry)
                assertTrue(lock.locked)
                lock.disable { fail("Unauthorized settings change") }
                assertTrue(lock.enabled)
                assertNotNull(lock.error)
            }
        } finally { store.clear() }
    }
}
