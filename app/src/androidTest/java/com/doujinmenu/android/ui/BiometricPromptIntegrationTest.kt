package com.doujinmenu.android.ui

import android.view.KeyEvent
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.MainActivity
import com.doujinmenu.android.security.AppLockStore
import com.doujinmenu.android.security.BiometricRequest
import com.doujinmenu.android.security.LockCredential
import com.doujinmenu.android.security.LockMethod
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BiometricPromptIntegrationTest {
    @Test fun realPromptSurvivesRotationAndCancelAllowsAppCredentialWithoutReopening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        assumeTrue(BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
        val store = AppLockStore(context)
        store.save(LockCredential.create(LockMethod.PIN, "142857").copy(biometric = true))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun awaitState(predicate: (AppLockViewModel) -> Boolean) {
                    val deadline = System.currentTimeMillis() + 5000
                    var matched = false
                    while (!matched && System.currentTimeMillis() < deadline) {
                        scenario.onActivity { matched = predicate(ViewModelProvider(it)[AppLockViewModel::class.java]) }
                        if (!matched) Thread.sleep(20)
                    }
                    assertTrue("Expected lock state within five seconds", matched)
                }
                lateinit var request: BiometricRequest
                awaitState { it.activeBiometricRequest != null && it.biometricInProgress }
                scenario.onActivity { request = ViewModelProvider(it)[AppLockViewModel::class.java].activeBiometricRequest!! }
                scenario.recreate()
                awaitState { it.activeBiometricRequest == request && it.biometricInProgress }
                Thread.sleep(500)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                awaitState { it.locked && !it.biometricInProgress && it.activeBiometricRequest == null }
                Thread.sleep(700)
                scenario.onActivity {
                    assertTrue(it.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                    assertTrue(it.hasWindowFocus())
                    val lock = ViewModelProvider(it)[AppLockViewModel::class.java]
                    assertTrue(lock.locked)
                    assertNull(lock.activeBiometricRequest)
                }
                val verified = CountDownLatch(1)
                scenario.onActivity { ViewModelProvider(it)[AppLockViewModel::class.java].verify("142857") { accepted ->
                    assertTrue(accepted); verified.countDown()
                } }
                assertTrue(verified.await(5, TimeUnit.SECONDS))
                awaitState { !it.locked }
            }
        } finally { store.clear() }
    }
}
