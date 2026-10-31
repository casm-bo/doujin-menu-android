package com.doujinzip.android.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.security.AppLockStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class BackupExportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun consumingExportRequestDoesNotCancelSuspendedPreparation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppLockStore(context, "synthetic-backup-ui-lock")
        store.clear()
        val lock = AppLockViewModel(context.applicationContext as Application, store)
        val completed = AtomicBoolean(false)
        compose.setContent { DoujinZipTheme { CompositionLocalProvider(LocalAppLock provides lock) {
            BackupSettings(onPrepare = {
                delay(300)
                completed.set(true)
                throw IOException("Synthetic preparation failure")
            }, onOpenConnection = {})
        } } }
        compose.onNodeWithText(context.getString(R.string.backup_export)).performClick()
        compose.waitUntil(5_000) { completed.get() }
        compose.onAllNodesWithText(context.getString(R.string.backup_failed)).onFirst().assertIsDisplayed()
        assertTrue(completed.get())
    }
}
