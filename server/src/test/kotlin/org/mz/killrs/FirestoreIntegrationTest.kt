package org.mz.killrs

import com.google.firebase.FirebaseApp
import com.google.firebase.cloud.FirestoreClient
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = "(127\\.0\\.0\\.1|localhost):[0-9]+")
class FirestoreIntegrationTest {
    private lateinit var app: FirebaseApp
    private lateinit var db: Firestore
    private lateinit var store: FirestoreStore
    private val uid = "test-${UUID.randomUUID()}"
    private val productId = "test-${UUID.randomUUID()}"
    @BeforeTest fun setup() {
        val config = ServerConfig.fromEnvironment()
        require(config.environment == "emulator" && config.projectId.startsWith("demo-"))
        app = initializeFirebase(config)
        db = FirestoreClient.getFirestore(app)
        store = FirestoreStore(db)
    }
    @AfterTest fun cleanup() { if (::db.isInitialized) db.close(); if (::app.isInitialized) app.delete() }

    @Test fun `concurrent duplicate requests create exactly one draft and audit entry`() = runBlocking<Unit> {
        store.saveProduct(productId, validProductInput, 0, "operator")
        val cart = CartInput(listOf(CartLine(productId, 3)))
        val key = UUID.randomUUID().toString()
        val results = (1..6).map { async { store.createDraft(uid, key, cart) } }.awaitAll()
        assertEquals(1, results.count { it.created })
        assertEquals(1, results.map { it.draft.id }.distinct().size)
        assertEquals(3702L, results.first().draft.quote.subtotalMinor)
        val audit = db.collection("auditLog").whereEqualTo("resource", results.first().draft.id).get().awaitResult()
        assertEquals(1, audit.size())
        assertEquals("DRAFT", results.first().draft.status)
    }
    @Test fun `same key cannot change cart and is scoped to its owner`() = runBlocking<Unit> {
        store.saveProduct(productId, validProductInput, 0, "operator")
        val cart = CartInput(listOf(CartLine(productId, 1)))
        val key = UUID.randomUUID().toString()
        val first = store.createDraft(uid, key, cart)
        val other = store.createDraft("another-$uid", key, cart)
        assertNotEquals(first.draft.id, other.draft.id)
        val error = assertFailsWith<ApiException> { store.createDraft(uid, key, CartInput(listOf(CartLine(productId, 2)))) }
        assertEquals(409, error.status)
    }
    @Test fun `replaying after a price change preserves the original draft snapshot`() = runBlocking<Unit> {
        store.saveProduct(productId, validProductInput, 0, "operator")
        val cart = CartInput(listOf(CartLine(productId, 1)))
        val key = UUID.randomUUID().toString()
        val first = store.createDraft(uid, key, cart)
        store.saveProduct(productId, validProductInput.copy(priceMinor = 5000), 1, "operator")
        assertEquals(first.draft, store.createDraft(uid, key, cart).draft)
        assertEquals(5000L, store.quote(cart).subtotalMinor)
    }
    @Test fun `stale product revisions cannot overwrite newer values`() = runBlocking<Unit> {
        store.saveProduct(productId, validProductInput, 0, "operator")
        store.saveProduct(productId, validProductInput.copy(priceMinor = 2000), 1, "operator")
        assertEquals(409, assertFailsWith<ApiException> { store.saveProduct(productId, validProductInput.copy(priceMinor = 1), 1, "operator") }.status)
        assertEquals(2000L, store.product(productId)!!.priceMinor)
        assertEquals(409, assertFailsWith<ApiException> { store.deleteProduct(productId, 1, "operator") }.status)
    }
    @Test fun `legacy dollar prices convert exactly and invalid precision fails closed`() = runBlocking<Unit> {
        db.collection("product").document(productId).set(mapOf("price" to 12.34, "title" to "Legacy shirt")).awaitResult()
        assertEquals(1234L, store.quote(CartInput(listOf(CartLine(productId, 1)))).subtotalMinor)
        db.collection("product").document(productId).update("price", 12.345).awaitResult()
        assertFailsWith<ApiException> { store.quote(CartInput(listOf(CartLine(productId, 1)))) }
        store.saveProduct(productId, validProductInput, 0, "operator")
        assertEquals(1234L, store.product(productId)!!.priceMinor)
    }
    @Test fun `profile updates preserve carts and quotes do not clear them`() = runBlocking<Unit> {
        val identity = Identity(uid, "customer@example.com")
        store.saveProfile(identity, ProfileInput("Alice", "Person", postalCode = "02108"))
        store.saveProduct(productId, validProductInput, 0, "operator")
        val cart = CartInput(listOf(CartLine(productId, 2)))
        store.saveCart(uid, cart)
        store.saveProfile(identity, ProfileInput("Updated", "Person", postalCode = "02108"))
        store.createDraft(uid, UUID.randomUUID().toString(), cart)
        assertEquals(cart, store.cart(uid))
        assertEquals("02108", store.profile(identity)!!.profile.postalCode)
        assertEquals("customer@example.com", store.profile(identity)!!.email)
        store.saveProfile(identity, ProfileInput("Alice", "Person"))
        assertNull(store.profile(identity)!!.profile.postalCode)
    }
    @Test fun `missing products cannot create drafts or persist a partial cart`() = runBlocking<Unit> {
        store.saveProfile(Identity(uid, "customer@example.com"), ProfileInput("Alice", "Person"))
        val invalid = CartInput(listOf(CartLine("missing-$productId", 1)))
        assertFailsWith<ApiException> { store.saveCart(uid, invalid) }
        assertTrue(store.cart(uid).items.isEmpty())
        val key = UUID.randomUUID().toString()
        assertFailsWith<ApiException> { store.createDraft(uid, key, invalid) }
        assertNull(store.draft(hash("$uid\u0000$key")))
    }
    @Test fun `expired drafts cannot be refreshed with the same key`() = runBlocking<Unit> {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val early = FirestoreStore(db, Clock.fixed(start, ZoneOffset.UTC))
        val late = FirestoreStore(db, Clock.fixed(start.plusSeconds(90_000), ZoneOffset.UTC))
        early.saveProduct(productId, validProductInput, 0, "operator")
        val key = UUID.randomUUID().toString()
        val cart = CartInput(listOf(CartLine(productId, 1)))
        early.createDraft(uid, key, cart)
        assertEquals(409, assertFailsWith<ApiException> { late.createDraft(uid, key, cart) }.status)
    }
}
