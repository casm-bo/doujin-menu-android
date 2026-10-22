package com.doujinzip.android.network

import com.doujinzip.android.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SubscriptionApiTest {
    @Test fun subscriptionRetriesKeepMutationAndAuthenticationAndReadPayload() = runBlocking {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val requests=mutableListOf<JSONObject>()
        val value=SearchSubscription(id="test-subscription",name="Example",query="tag:example",enabled=true)
        server.createContext("/v1/subscriptions") { exchange ->
            assertEquals("Bearer synthetic-test-token",exchange.requestHeaders.getFirst("Authorization"))
            val data=if(exchange.requestMethod=="GET") JSONObject().put("changes",JSONArray()).put("cursor",17).put("hasMore",false)
                else { requests+=JSONObject(exchange.requestBody.reader().readText()); if(exchange.requestURI.path.endsWith("/read")) JSONObject().put("applied",true) else JSONObject().put("status","applied").put("value",value.copy(version=1).json()) }
            val output=JSONObject().put("success",true).put("data",data).toString().toByteArray()
            exchange.sendResponseHeaders(200,output.size.toLong());exchange.responseBody.use { it.write(output) }
        }
        server.start()
        try {
            val profile=DesktopProfile("test-pc","Test PC","http://127.0.0.1:${server.address.port}","synthetic-test-token")
            val client=CompanionClient()
            repeat(2) { assertEquals("applied",client.saveSubscription(profile,"stable-mutation",value).getString("status")) }
            assertEquals(listOf("stable-mutation","stable-mutation"),requests.map { it.getString("mutationId") })
            client.readSubscriptionNotices(profile,listOf(12,34))
            assertEquals(34L,requests.last().getJSONArray("ids").getLong(1))
            assertEquals(17L,client.subscriptionChanges(profile,0).getLong("cursor"))
        } finally { server.stop(0) }
    }
}
