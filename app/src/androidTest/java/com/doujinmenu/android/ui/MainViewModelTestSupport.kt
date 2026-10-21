package com.doujinmenu.android.ui

import androidx.test.platform.app.InstrumentationRegistry

internal fun MainViewModel.awaitStarted() {
    check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + 10_000
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        var ready = false
        instrumentation.runOnMainSync { ready = !uiState.isStarting }
        if (ready) return
        android.os.SystemClock.sleep(10)
    }
    error("Startup state was not restored")
}
