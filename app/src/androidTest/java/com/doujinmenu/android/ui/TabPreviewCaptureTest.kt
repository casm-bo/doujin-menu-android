package com.doujinmenu.android.ui

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.model.BrowserWorkspace
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabPreviewCaptureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun capturesOnlyWhenRequestedAndAlwaysUsesTheLatestDrawnContent() {
        val cache = BrowserTabPreviewCache(InstrumentationRegistry.getInstrumentation().targetContext)
        val tab = BrowserWorkspace.initial().activeTab
        val key = BrowserTabPreviewKey(false, tab.id, tab.currentPage.key)
        var color by mutableStateOf(Color.Red)
        var completed by mutableIntStateOf(0)
        compose.setContent { DoujinMenuTheme {
            val scope = rememberCoroutineScope()
            BrowserTabPreviewHost(cache, key) { Column(Modifier.fillMaxSize().background(color)) {
                Button(onClick = { scope.launch { cache.captureNow(); completed++ } }) { Text("Capture fixture") }
            } }
        } }
        assertNull(cache.preview(false, tab))
        fun capture() {
            val before = completed
            compose.onNodeWithText("Capture fixture").performClick()
            compose.waitUntil(5000) { completed == before + 1 }
        }
        fun pixel(): Int {
            val file = requireNotNull(cache.preview(false, tab))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            return try { bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) } finally { bitmap.recycle() }
        }
        try {
            capture()
            assertTrue(AndroidColor.red(pixel()) > 200)
            compose.runOnIdle { color = Color.Blue }
            compose.waitForIdle()
            assertTrue("Changing the screen alone must not recapture it", AndroidColor.red(pixel()) > 200)
            capture()
            assertTrue("A requested capture must include the updated screen", AndroidColor.blue(pixel()) > 200)
        } finally { cache.preview(false, tab)?.delete() }
    }
}
