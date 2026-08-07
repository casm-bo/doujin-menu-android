package com.doujinmenu.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDiagnosticsTest {
    @Test
    fun reportOmitsConnectionSecrets() {
        val report = buildDiagnosticReport(
            state = MainUiState(
                host = "192.168.1.25",
                pairingCode = "123456",
                deviceName = "Private device name",
            ),
            appVersion = "0.5.0 (6)",
            androidVersion = "16 / SDK 36",
            device = "Test Device",
            orientation = "landscape",
        )

        assertTrue(report.contains("App: 0.5.0 (6)"))
        assertFalse(report.contains("192.168.1.25"))
        assertFalse(report.contains("123456"))
        assertFalse(report.contains("Private device name"))
    }
}
