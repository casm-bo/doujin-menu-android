package com.doujinmenu.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointNormalizerTest {
    @Test
    fun rawIpUsesHttpAndEnteredPort() {
        assertEquals(
            "http://192.168.1.20:47831",
            EndpointNormalizer.normalize("192.168.1.20", "47831"),
        )
    }

    @Test
    fun explicitSchemeAndPortTakePrecedence() {
        assertEquals(
            "https://desktop.local:8443",
            EndpointNormalizer.normalize("https://desktop.local:8443/", "47831"),
        )
    }

    @Test
    fun pathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointNormalizer.normalize("http://desktop.local/api", "47831")
        }
    }

    @Test
    fun invalidPortIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointNormalizer.normalize("desktop.local", "70000")
        }
    }
}
