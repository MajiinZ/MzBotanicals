package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.mz.killrs.shared.domain.MockOrder

/** Import local demo history once per process; each remote record is immutable and idempotent. */
class MongoMockOrderStorage(private val legacy: MockOrderStorage) : MockOrderStorage {
    private val imported = mutableSetOf<String>()
    private val serializer = ListSerializer(MockOrder.serializer())
    override suspend fun read(customerId: String): String {
        check(Firebase.auth.currentUser?.uid == customerId) { "Sign in to this account first." }
        if (customerId !in imported) {
            legacy.read(customerId)?.let { saved ->
                val orders = MongoStore.json.decodeFromString(serializer, saved)
                require(orders.all { it.customerId == customerId }) { "Local history belongs to another account." }
                orders.forEach { MongoStore.collection("mock_order").document(it.id).set(it) }
            }
            imported += customerId
        }
        val rows = MongoStore.collection("mock_order").get().documents
        return JsonArray(rows.map { it.data }).toString()
    }
    override suspend fun write(customerId: String, value: String) {
        check(Firebase.auth.currentUser?.uid == customerId) { "Sign in to this account first." }
        val orders = MongoStore.json.decodeFromString(serializer, value)
        require(orders.all { it.customerId == customerId })
        orders.forEach { MongoStore.collection("mock_order").document(it.id).set(it) }
    }
}
