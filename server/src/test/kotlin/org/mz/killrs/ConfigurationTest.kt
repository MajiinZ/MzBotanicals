package org.mz.killrs

import kotlin.test.*

class ConfigurationTest {
    @Test fun `production never accepts emulator configuration`() {
        for (name in listOf("FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST", "FIREBASE_STORAGE_EMULATOR_HOST")) {
            assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(mapOf("FIREBASE_PROJECT_ID" to "real-project", name to "localhost:9099")) }
        }
    }
    @Test fun `emulators require a demo project and loopback addresses`() {
        val env = mapOf("APP_ENV" to "emulator", "FIREBASE_PROJECT_ID" to "demo-mzbotanicals", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099", "FIRESTORE_EMULATOR_HOST" to "127.0.0.1:8085")
        assertEquals("emulator", ServerConfig.fromEnvironment(env).environment)
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(env + ("FIREBASE_PROJECT_ID" to "real-project")) }
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(env + ("FIREBASE_AUTH_EMULATOR_HOST" to "attacker.example.com:9099")) }
    }
    @Test fun `production needs an explicit project and safe browser origins`() {
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(emptyMap()) }
        for (origin in listOf("*", "http://shop.example.com", "https://shop.example.com/path", "https://user:pass@shop.example.com")) {
            assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(mapOf("FIREBASE_PROJECT_ID" to "real-project", "ALLOWED_ORIGINS" to origin)) }
        }
    }
    @Test fun `quantity and money validation prevents invalid quotes`() {
        assertFailsWith<ApiException> { quote(CartInput(List(51) { CartLine("p$it", 1) }), listOf(testProduct)) }
        assertFailsWith<ApiException> { quote(CartInput(listOf(CartLine("p1", 1))), listOf(testProduct.copy(priceMinor = -1))) }
        assertFailsWith<ApiException> { quote(CartInput(listOf(CartLine("p1", 1))), emptyList()) }
        assertEquals(12340, quote(CartInput(listOf(CartLine("p1", 10))), listOf(testProduct)).subtotalMinor)
    }
}
