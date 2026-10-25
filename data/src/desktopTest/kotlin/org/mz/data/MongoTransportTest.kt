package org.mz.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.domain.Customer
import kotlin.test.*

class MongoTransportTest {
    @Test fun profileSerializationRetainsNullableFieldsAndPhoneNumber() {
        val customer = Customer("uid", "A", "B", "a@example.test", phoneNumber = PhoneNumber("5550000"), dateOfBirth = "", gender = "")
        val encoded = MongoStore.json.encodeToJsonElement(customer)
        assertEquals(customer, MongoStore.json.decodeFromJsonElement<Customer>(encoded))
        assertTrue(encoded.jsonObject.containsKey("cart"))
    }
    @Test fun authenticatedTransportRejectsRedirectsAndReportsServerFailure() = runBlocking {
        val previous = System.getProperty("mz.api.url")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirected = false
        server.createContext("/ok") { exchange ->
            assertEquals("Bearer test-token", exchange.requestHeaders.getFirst("Authorization"))
            val bytes = "{\"saved\":true}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/leak")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/leak") { exchange -> redirected = true; exchange.sendResponseHeaders(200, -1); exchange.close() }
        server.createContext("/failure") { exchange ->
            val bytes = "{\"detail\":\"Database is offline\"}".toByteArray()
            exchange.sendResponseHeaders(503, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        System.setProperty("mz.api.url", "http://127.0.0.1:${server.address.port}")
        try {
            assertEquals("{\"saved\":true}", mongoHttp("GET", "/ok", "test-token", null).decodeToString())
            assertFails { mongoHttp("GET", "/redirect", "test-token", null) }
            assertFalse(redirected)
            assertTrue(assertFails { mongoHttp("GET", "/failure", "test-token", null) }.message!!.contains("Database is offline"))
        } finally {
            server.stop(0)
            if (previous == null) System.clearProperty("mz.api.url") else System.setProperty("mz.api.url", previous)
        }
    }
}
