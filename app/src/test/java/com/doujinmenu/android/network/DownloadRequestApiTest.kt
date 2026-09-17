package com.doujinmenu.android.network

import com.doujinmenu.android.model.DesktopProfile
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DownloadRequestApiTest {
    @Test fun `unreachable PC is distinct from an ambiguous response timeout`() {
        assertTrue(downloadDefinitelyNotSubmitted(java.net.ConnectException("refused")))
        assertTrue(downloadDefinitelyNotSubmitted(java.net.UnknownHostException("offline")))
        assertTrue(downloadDefinitelyNotSubmitted(CompanionApiException("unauthorized", 401)))
        assertFalse(downloadDefinitelyNotSubmitted(java.net.SocketTimeoutException("response lost")))
        assertFalse(downloadDefinitelyNotSubmitted(CompanionApiException("receipt write failed", 500)))
    }

    @Test fun `download requests carry the same durable ID on retry`() = runBlocking {
        val received = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/downloads") { exchange ->
            received += JSONObject(exchange.requestBody.reader().readText())
            val response = """{"success":true,"data":{"id":7,"gallery_id":42,"status":"pending"}}""".toByteArray()
            exchange.sendResponseHeaders(201, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/v1/status") { exchange ->
            val response = """{"success":true,"data":{"version":2,"downloadRequestIds":true}}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val profile = DesktopProfile("pc", "PC", "http://127.0.0.1:${server.address.port}", "token")
            val client = CompanionClient()
            assertTrue(client.getStatus(profile.baseUrl).downloadRequestIds)
            val id = "00000000-0000-4000-8000-000000000001"
            repeat(2) { assertEquals(7L, client.requestDownload(profile, 42, id).id) }
            assertEquals(listOf(id, id), received.map { it.getString("requestId") })
            assertEquals(listOf(42L, 42L), received.map { it.getLong("galleryId") })
        } finally { server.stop(0) }
    }
}
