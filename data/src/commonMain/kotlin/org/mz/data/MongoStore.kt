package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.domain.CartItem
import org.mz.killrs.shared.domain.Product

/** Authenticated API only. No MongoDB credentials or direct database ports in the app. */
object MongoStore {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun collection(collectionPath: String) = MongoCollection(collectionPath)
    suspend fun request(method: String, path: String, data: ByteArray? = null): ByteArray {
        val account = Firebase.auth.currentUser ?: error("Please sign in.")
        val token = account.getIdToken(false) ?: error("Please sign in again.")
        val result = mongoHttp(method, path, token, data)
        check(Firebase.auth.currentUser?.uid == account.uid) { "Your account changed. Reopen this screen." }
        return result
    }
    suspend fun cart(action: String, id: String = "unused", quantity: Int = 1, item: CartItem? = null) {
        request("POST", "/cart", buildJsonObject { put("action", action); put("id", id); put("quantity", quantity); if (item != null) put("item", json.encodeToJsonElement(item)) }.toString().encodeToByteArray())
    }
}

expect suspend fun mongoHttp(method: String, path: String, token: String, data: ByteArray?): ByteArray
expect fun mongoBaseUrl(): String

class MongoSnapshot(val id: String, val exists: Boolean, val data: JsonObject) {
    inline fun <reified T> get(field: String): T = MongoStore.json.decodeFromJsonElement(data[field] ?: JsonNull)
}

data class MongoQuerySnapshot(val documents: List<MongoSnapshot>)
enum class MongoDirection { ASCENDING, DESCENDING }

class MongoFilter {
    var test: (MongoSnapshot) -> Boolean = { true }
    infix fun String.equalTo(value: Any) {
        val expected = value.toString()
        test = { it.data[this]?.jsonPrimitive?.content == expected }
    }
    infix fun String.inArray(values: List<String>) {
        test = { it.data[this]?.jsonPrimitive?.content in values }
    }
}

class MongoCollection(private val name: String, private val filter: (MongoSnapshot) -> Boolean = { true }, private val sort: String? = null, private val direction: MongoDirection = MongoDirection.ASCENDING, private val maximum: Int? = null) {
    fun document(id: String) = MongoDocument(name, id)
    fun where(block: MongoFilter.() -> Unit): MongoCollection {
        val match = MongoFilter().apply(block).test
        return MongoCollection(name, { filter(it) && match(it) }, sort, direction, maximum)
    }
    fun orderBy(field: String, direction: MongoDirection = MongoDirection.ASCENDING) = MongoCollection(name, filter, field, direction, maximum)
    fun limit(count: Int) = MongoCollection(name, filter, sort, direction, count)
    suspend fun get(): MongoQuerySnapshot {
        val all = mutableListOf<MongoSnapshot>()
        var after: String? = null
        do {
            val response = MongoStore.json.parseToJsonElement(MongoStore.request("GET", "/data/$name" + (after?.let { "?after=$it" } ?: "")).decodeToString()).jsonObject
            all += response.getValue("documents").jsonArray.map { row ->
                val obj = row.jsonObject
                MongoSnapshot(obj.getValue("id").jsonPrimitive.content, obj.getValue("exists").jsonPrimitive.boolean, obj.getValue("data").jsonObject)
            }
            after = response["next"]?.jsonPrimitive?.contentOrNull
        } while (after != null)
        var selected = all.filter(filter)
        if (sort != null) {
            selected = selected.sortedWith { a, b ->
                val left = a.data[sort]?.jsonPrimitive
                val right = b.data[sort]?.jsonPrimitive
                if (left?.doubleOrNull != null && right?.doubleOrNull != null) left.double.compareTo(right.double)
                else left?.content.orEmpty().compareTo(right?.content.orEmpty())
            }
            if (direction == MongoDirection.DESCENDING) selected = selected.reversed()
        }
        return MongoQuerySnapshot(maximum?.let { selected.take(it) } ?: selected)
    }
    val snapshots: Flow<MongoQuerySnapshot> get() = flow {
        while (true) { emit(get()); delay(2000) }
    }
}

class MongoDocument(val collection: String, val id: String) {
    init { require(Regex("[A-Za-z0-9_.:@-]{1,128}").matches(id)) { "Invalid record ID" } }
    val path get() = "/data/$collection/$id"
    suspend fun get(): MongoSnapshot {
        val response = MongoStore.json.parseToJsonElement(MongoStore.request("GET", path).decodeToString()).jsonObject
        return MongoSnapshot(id, response.getValue("exists").jsonPrimitive.boolean, response.getValue("data").jsonObject)
    }
    val snapshots: Flow<MongoSnapshot> get() = flow {
        while (true) { emit(get()); delay(2000) }
    }
    suspend inline fun <reified T> set(value: T) { MongoStore.request("PUT", path, MongoStore.json.encodeToString(value).encodeToByteArray()) }
    suspend fun update(value: Product) { MongoStore.request("PATCH", path, MongoStore.json.encodeToString(value).encodeToByteArray()) }
    suspend fun update(vararg values: Pair<String, Any?>) {
        val fields = buildJsonObject { values.forEach { (key, value) -> put(key, encodeField(value)) } }
        MongoStore.request("PATCH", path, fields.toString().encodeToByteArray())
    }
    suspend fun delete() { MongoStore.request("DELETE", path) }
}

private fun encodeField(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is PhoneNumber -> MongoStore.json.encodeToJsonElement(value)
    else -> error("Unsupported profile field")
}
