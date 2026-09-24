package org.mz.killrs

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class ApiSecurityTest {
    private fun ApplicationTestBuilder.installApi(store: FakeStore = FakeStore(), limit: Int = 120) {
        application {
            module(ServerConfig("demo-mzbotanicals", requestsPerMinute = limit, allowedOrigins = listOf("https://shop.example.com")), TokenVerifier { token ->
                when (token) {
                    "alice" -> Identity("alice", "alice@example.com")
                    "bob" -> Identity("bob", "bob@example.com")
                    "admin" -> Identity("operator", "operator@example.com", true)
                    else -> null
                }
            }, store)
        }
    }
    @Test fun `protected routes require a verified identity`() = testApplication {
        installApi()
        for (path in listOf("/v1/me", "/v1/cart", "/v1/products", "/v1/order-drafts/d1")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { bearerAuth("invalid") }.status)
        }
    }
    @Test fun `customer cannot use admin routes even with a forged role header`() = testApplication {
        val store = FakeStore(); installApi(store)
        val result = client.put("/v1/admin/products/p1") {
            bearerAuth("alice"); header("X-Admin", "true"); header(HttpHeaders.IfMatch, "\"0\"")
            contentType(ContentType.Application.Json); setBody(apiJson.encodeToString(validProductInput))
        }
        assertEquals(HttpStatusCode.Forbidden, result.status)
        assertEquals(0, store.writes)
    }
    @Test fun `profile mass assignment is rejected`() = testApplication {
        val store = FakeStore(); installApi(store)
        for (extra in listOf("\"id\":\"bob\"", "\"isAdmin\":true", "\"email\":\"victim@example.com\"")) {
            val result = client.put("/v1/me") {
                bearerAuth("alice"); contentType(ContentType.Application.Json)
                setBody("{\"firstName\":\"Alice\",\"lastName\":\"Person\",$extra}")
            }
            assertEquals(HttpStatusCode.BadRequest, result.status, result.bodyAsText())
        }
        assertEquals(0, store.writes)
    }
    @Test fun `profile owner and email come from the token`() = testApplication {
        val store = FakeStore(); installApi(store)
        val result = client.put("/v1/me") {
            bearerAuth("alice"); contentType(ContentType.Application.Json)
            setBody("{\"firstName\":\"Alice\",\"lastName\":\"Person\"}")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("alice", store.lastOwner)
        assertTrue(result.bodyAsText().contains("alice@example.com"))
    }
    @Test fun `customers cannot access another customers draft`() = testApplication {
        installApi()
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/order-drafts/d1") { bearerAuth("bob") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/order-drafts/d1") { bearerAuth("alice") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/order-drafts/d1") { bearerAuth("admin") }.status)
    }
    @Test fun `prices owners and paid states cannot be supplied with a cart`() = testApplication {
        installApi()
        for (extra in listOf("\"subtotalMinor\":1", "\"customerId\":\"bob\"", "\"status\":\"PAID\"")) {
            val result = client.post("/v1/quotes") {
                bearerAuth("alice"); contentType(ContentType.Application.Json)
                setBody("{\"items\":[{\"productId\":\"p1\",\"quantity\":1}],$extra}")
            }
            assertEquals(HttpStatusCode.BadRequest, result.status)
        }
    }
    @Test fun `malformed duplicate negative and excessive carts are rejected`() = testApplication {
        installApi()
        val badCarts = listOf("{", "{\"items\":[]}", "{\"items\":[{\"productId\":\"p1\",\"quantity\":-1}]}",
            "{\"items\":[{\"productId\":\"p1\",\"quantity\":11}]}", "{\"items\":[{\"productId\":\"p1\",\"quantity\":1},{\"productId\":\"p1\",\"quantity\":1}]}",
            "{\"items\":[{\"productId\":\"../customer/bob\",\"quantity\":1}]}")
        for (body in badCarts) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/quotes") {
                bearerAuth("alice"); contentType(ContentType.Application.Json); setBody(body)
            }.status)
        }
    }
    @Test fun `quote uses authoritative integer prices`() = testApplication {
        installApi()
        val result = client.post("/v1/quotes") {
            bearerAuth("alice"); contentType(ContentType.Application.Json)
            setBody("{\"items\":[{\"productId\":\"p1\",\"quantity\":3}]}")
        }
        assertEquals(3702, apiJson.parseToJsonElement(result.bodyAsText()).jsonObject["subtotalMinor"]!!.jsonPrimitive.int)
    }
    @Test fun `oversized request bodies are rejected`() = testApplication {
        installApi()
        val result = client.put("/v1/me") {
            bearerAuth("alice"); contentType(ContentType.Application.Json)
            setBody("{\"firstName\":\"${"a".repeat(70_000)}\",\"lastName\":\"Person\"}")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, result.status)
    }
    @Test fun `product edits need revision and reject invalid money`() = testApplication {
        installApi()
        assertEquals(HttpStatusCode.fromValue(428), client.put("/v1/admin/products/p1") {
            bearerAuth("admin"); contentType(ContentType.Application.Json); setBody(apiJson.encodeToString(validProductInput))
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/v1/admin/products/p1") {
            bearerAuth("admin"); header(HttpHeaders.IfMatch, "\"0\""); contentType(ContentType.Application.Json)
            setBody(apiJson.encodeToString(validProductInput.copy(priceMinor = -1)))
        }.status)
    }
    @Test fun `payment endpoint fails closed without persisting or charging`() = testApplication {
        val store = FakeStore(); installApi(store)
        val result = client.post("/v1/checkout") { bearerAuth("alice") }
        assertEquals(HttpStatusCode.ServiceUnavailable, result.status)
        assertTrue(result.bodyAsText().contains("payments_unavailable"))
        assertEquals(0, store.writes)
    }
    @Test fun `errors do not reveal internal data and have correlation ids`() = testApplication {
        val store = FakeStore(fail = true); installApi(store)
        val result = client.get("/v1/me") { bearerAuth("alice") }
        assertEquals(HttpStatusCode.InternalServerError, result.status)
        assertFalse(result.bodyAsText().contains("sensitive"))
        assertNotNull(result.headers["X-Request-ID"])
        assertEquals("no-store", result.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", result.headers["X-Content-Type-Options"])
    }
    @Test fun `rate limiting is based on identity and forwarded headers cannot bypass it`() = testApplication {
        installApi(limit = 2)
        repeat(2) { assertEquals(HttpStatusCode.OK, client.get("/v1/products") { bearerAuth("alice") }.status) }
        val limited = client.get("/v1/products") { bearerAuth("alice"); header("X-Forwarded-For", "203.0.113.10") }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        assertEquals(HttpStatusCode.OK, client.get("/v1/products") { bearerAuth("bob") }.status)
    }
    @Test fun `cors only allows configured browser origins`() = testApplication {
        installApi()
        val allowed = client.options("/v1/me") { header(HttpHeaders.Origin, "https://shop.example.com"); header(HttpHeaders.AccessControlRequestMethod, "PUT"); header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type") }
        assertEquals("https://shop.example.com", allowed.headers[HttpHeaders.AccessControlAllowOrigin])
        val denied = client.options("/v1/me") { header(HttpHeaders.Origin, "https://evil.example.com"); header(HttpHeaders.AccessControlRequestMethod, "PUT") }
        assertNull(denied.headers[HttpHeaders.AccessControlAllowOrigin])
    }
    @Test fun `pagination is bounded`() = testApplication {
        installApi()
        for (value in listOf("0", "101", "-1", "NaN")) assertEquals(HttpStatusCode.BadRequest, client.get("/v1/products?limit=$value") { bearerAuth("alice") }.status)
    }
}

internal val validProductInput = ProductInput("Botanical T-shirt", "Cotton shirt", 1234, "https://example.com/shirt.png", "Merchandise")
internal val testProduct = Product("p1", "Botanical T-shirt", "Cotton shirt", 1234, "https://example.com/shirt.png", "Merchandise", createdAt = 1)
internal class FakeStore(private val fail: Boolean = false) : Store {
    var writes = 0
    var lastOwner: String? = null
    override suspend fun ready() {}
    override suspend fun products(limit: Int, after: String?) = ProductPage(listOf(testProduct), null)
    override suspend fun product(id: String) = testProduct
    override suspend fun saveProduct(id: String, input: ProductInput, expectedRevision: Long, actor: String): Product { writes++; return testProduct }
    override suspend fun deleteProduct(id: String, expectedRevision: Long, actor: String) { writes++ }
    override suspend fun profile(identity: Identity): CustomerProfile { if (fail) error("sensitive datastore details"); return CustomerProfile(identity.uid, identity.email, ProfileInput("Alice", "Person")) }
    override suspend fun saveProfile(identity: Identity, input: ProfileInput): CustomerProfile { writes++; lastOwner = identity.uid; return CustomerProfile(identity.uid, identity.email, input) }
    override suspend fun cart(uid: String) = CartInput(emptyList())
    override suspend fun saveCart(uid: String, input: CartInput): CartInput { writes++; return input }
    override suspend fun quote(input: CartInput) = quote(input, listOf(testProduct))
    override suspend fun createDraft(uid: String, key: String, input: CartInput): DraftResult { writes++; return DraftResult(OrderDraft("d1", uid, quote(input), 1, 2), true) }
    override suspend fun draft(id: String) = OrderDraft("d1", "alice", quote(CartInput(listOf(CartLine("p1", 1)))), 1, 2)
}
