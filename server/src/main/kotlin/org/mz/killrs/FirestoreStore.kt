package org.mz.killrs

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.cloud.firestore.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> ApiFuture<T>.awaitResult(): T = withTimeout(15_000) {
    suspendCancellableCoroutine { continuation ->
        ApiFutures.addCallback(this@awaitResult, object : ApiFutureCallback<T> {
            override fun onSuccess(result: T) { continuation.resume(result) }
            override fun onFailure(error: Throwable) {
                val domainError = generateSequence(error) { it.cause }.filterIsInstance<ApiException>().firstOrNull()
                continuation.resumeWithException(domainError ?: error)
            }
        }, Executor { it.run() })
        continuation.invokeOnCancellation { this@awaitResult.cancel(true) }
    }
}

internal fun JsonElement.nativeValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { it.value.nativeValue() }
    is JsonArray -> map { it.nativeValue() }
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull
}
internal fun Any?.jsonValue(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { it.key.toString() to it.value.jsonValue() })
    is List<*> -> JsonArray(map { it.jsonValue() })
    else -> error("Unsupported stored data type")
}
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T> encodeDocument(value: T): Map<String, Any?> = apiJson.encodeToJsonElement(value).nativeValue() as Map<String, Any?>
internal inline fun <reified T> decodeDocument(data: Map<String, Any?>): T = apiJson.decodeFromJsonElement(data.jsonValue())
internal fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

class FirestoreStore(private val db: Firestore, private val clock: Clock = Clock.systemUTC()) : Store {
    private val catalog = db.collection("product")
    private val customers = db.collection("customer")
    private val drafts = db.collection("orderDraft")

    override suspend fun ready() { db.document("_health/probe").get().awaitResult() }

    private fun readProduct(snapshot: DocumentSnapshot): Product? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        // Import legacy dollars without floating-point arithmetic or silent rounding.
        val cents = (data["priceMinor"] as? Long) ?: runCatching {
            BigDecimal(data["price"].toString()).movePointRight(2).longValueExact()
        }.getOrElse { conflict("A product needs a pricing update.") }
        return Product(
            id = snapshot.id, title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "", priceMinor = cents,
            thumbnail = data["thumbnail"] as? String ?: "", category = data["category"] as? String ?: "",
            amountOfSeeds = (data["amountOfSeeds"] as? String)?.takeUnless { it == "null" },
            strains = (data["strains"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isPopular = data["isPopular"] == true, isNew = data["isNew"] == true, isDiscounted = data["isDiscounted"] == true,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0,
            revision = (data["revision"] as? Number)?.toLong() ?: 0,
            currency = data["currency"] as? String ?: "USD",
        )
    }
    override suspend fun products(limit: Int, after: String?): ProductPage {
        var query: Query = catalog.orderBy(FieldPath.documentId()).limit(limit + 1)
        if (after != null) query = query.startAfter(after)
        val all = query.get().awaitResult().documents.mapNotNull(::readProduct)
        return ProductPage(all.take(limit), if (all.size > limit) all[limit - 1].id else null)
    }
    override suspend fun product(id: String): Product? = readProduct(catalog.document(validateId(id)).get().awaitResult())

    override suspend fun saveProduct(id: String, input: ProductInput, expectedRevision: Long, actor: String): Product {
        validateId(id); input.validate()
        val ref = catalog.document(id)
        val audit = db.collection("auditLog").document()
        return db.runTransaction { tx ->
            val previous = tx.get(ref).get()
            if ((previous.getLong("revision") ?: 0) != expectedRevision) conflict("Product changed; reload it before saving.")
            val product = Product(id, input.title, input.description, input.priceMinor, input.thumbnail, input.category,
                input.amountOfSeeds, input.strains, input.isPopular, input.isNew, input.isDiscounted,
                previous.getLong("createdAt") ?: clock.millis(), Math.addExact(expectedRevision, 1))
            tx.set(ref, encodeDocument(product) + ("price" to input.priceMinor / 100.0))
            tx.set(audit, auditRecord(actor, "product.saved", id))
            product
        }.awaitResult()
    }
    override suspend fun deleteProduct(id: String, expectedRevision: Long, actor: String) {
        val ref = catalog.document(validateId(id))
        val audit = db.collection("auditLog").document()
        db.runTransaction { tx ->
            val product = tx.get(ref).get()
            if (!product.exists()) missing()
            if ((product.getLong("revision") ?: 0) != expectedRevision) conflict("Product changed; reload it before deleting.")
            tx.delete(ref)
            tx.set(audit, auditRecord(actor, "product.deleted", id))
            true
        }.awaitResult()
    }
    override suspend fun profile(identity: Identity): CustomerProfile? {
        val data = customers.document(identity.uid).get().awaitResult().data ?: return null
        return CustomerProfile(identity.uid, identity.email, ProfileInput(
            firstName = data["firstName"] as? String ?: "", lastName = data["lastName"] as? String ?: "",
            address = data["address"] as? String, city = data["city"] as? String, state = data["state"] as? String,
            postalCode = (data["postalCode"] ?: data["zip"]) as? String,
            phoneNumber = (data["phoneNumber"] as? Map<*, *>)?.get("number") as? String,
        ))
    }
    override suspend fun saveProfile(identity: Identity, input: ProfileInput): CustomerProfile {
        input.validate()
        val ref = customers.document(identity.uid)
        db.runTransaction { tx ->
            val exists = tx.get(ref).get().exists()
            val fields = encodeDocument(input) + mapOf("id" to identity.uid, "email" to identity.email, "zip" to input.postalCode,
                "phoneNumber" to input.phoneNumber?.let { mapOf("number" to it) })
            val initial = if (exists) emptyMap() else mapOf("cart" to emptyList<Any>(), "isAdmin" to false, "dateOfBirth" to "", "gender" to "")
            tx.set(ref, fields + initial, SetOptions.merge())
            true
        }.awaitResult()
        return CustomerProfile(identity.uid, identity.email, input)
    }
    override suspend fun cart(uid: String): CartInput {
        val data = customers.document(uid).get().awaitResult().data ?: missing()
        val rows = (data["cart"] as? List<*>) ?: emptyList<Any>()
        return CartInput(rows.map { row ->
            val item = row as? Map<*, *> ?: conflict("Cart needs to be reset.")
            val quantity = item["quantity"] as? Long ?: conflict("Cart needs to be reset.")
            if (quantity !in 1..10) conflict("Cart needs to be reset.")
            CartLine(item["productId"] as? String ?: conflict("Cart needs to be reset."), quantity.toInt())
        }).validated()
    }
    override suspend fun saveCart(uid: String, input: CartInput): CartInput {
        val normalized = input.validated()
        val ref = customers.document(uid)
        db.runTransaction { tx ->
            if (!tx.get(ref).get().exists()) conflict("Create your profile first.")
            val products = normalized.items.map { readProduct(tx.get(catalog.document(it.productId)).get()) ?: conflict("A product is no longer available.") }
            val byId = products.associateBy { it.id }
            val rows = normalized.items.map { line -> mapOf(
                "id" to line.productId, "productId" to line.productId, "quantity" to line.quantity,
                "amountOfSeeds" to byId[line.productId]?.amountOfSeeds?.toIntOrNull(),
            ) }
            tx.update(ref, "cart", rows)
            true
        }.awaitResult()
        return normalized
    }
    override suspend fun quote(input: CartInput): Quote {
        val normalized = input.validated(false)
        return db.runTransaction { tx ->
            quote(normalized, normalized.items.mapNotNull { readProduct(tx.get(catalog.document(it.productId)).get()) })
        }.awaitResult()
    }
    override suspend fun createDraft(uid: String, key: String, input: CartInput): DraftResult {
        if (!Regex("[A-Za-z0-9_-]{16,128}").matches(key)) invalid("Idempotency-Key must be 16 to 128 letters, digits, underscores or hyphens.")
        val normalized = input.validated(false)
        val id = hash("$uid\u0000$key")
        val fingerprint = hash(apiJson.encodeToString(normalized))
        val ref = drafts.document(id)
        return db.runTransaction { tx ->
            val existing = tx.get(ref).get()
            if (existing.exists()) {
                val record = decodeDocument<DraftRecord>(existing.data!!)
                if (record.fingerprint != fingerprint) conflict("Idempotency key was already used with a different cart.")
                if (record.draft.expiresAt <= clock.millis()) conflict("Draft expired; use a new idempotency key for a fresh quote.")
                DraftResult(record.draft, false)
            } else {
                val priced = quote(normalized, normalized.items.mapNotNull { readProduct(tx.get(catalog.document(it.productId)).get()) })
                val now = clock.millis()
                val draft = OrderDraft(id, uid, priced, now, now + 86_400_000)
                tx.create(ref, encodeDocument(DraftRecord(draft, fingerprint)))
                tx.create(db.collection("auditLog").document("draft-$id"), auditRecord(uid, "draft.created", id))
                DraftResult(draft, true)
            }
        }.awaitResult()
    }
    override suspend fun draft(id: String): OrderDraft? = drafts.document(validateId(id)).get().awaitResult().data?.let { decodeDocument<DraftRecord>(it).draft }
    private fun auditRecord(actor: String, action: String, resource: String) = mapOf("actor" to actor, "action" to action, "resource" to resource, "at" to clock.millis())
}
