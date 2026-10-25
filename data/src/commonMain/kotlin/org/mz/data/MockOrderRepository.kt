package org.mz.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.mz.killrs.shared.domain.MockOrder
import org.mz.killrs.shared.util.RequestState

/** Storage is deliberately separate from the real order and payment services. */
interface MockOrderStorage {
    suspend fun read(customerId: String): String?
    suspend fun write(customerId: String, value: String)
}

class MockOrderRepository(
    private val storage: MockOrderStorage,
    private val currentUserId: () -> String?
) {
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val serializer = ListSerializer(MockOrder.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun observeOrders(): Flow<RequestState<List<MockOrder>>> = flow {
        emit(RequestState.Loading)
        revision.collect {
            val result: RequestState<List<MockOrder>> = withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val customerId = requireCurrentCustomer()
                        val orders = readOrders(customerId)
                        check(currentUserId() == customerId) { "Your account changed. Reopen Orders to continue." }
                        RequestState.Success(orders)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        RequestState.Error(error.message ?: "Unable to load your demo orders.")
                    }
                }
            }
            emit(result)
        }
    }

    suspend fun placeOrder(order: MockOrder): RequestState<MockOrder> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val customerId = requireCurrentCustomer()
                validateOrder(order, customerId)
                val orders = readOrders(customerId)
                val existing = orders.firstOrNull { it.id == order.id }
                check(currentUserId() == customerId) { "Your account changed. Please try checkout again." }
                if (existing != null) {
                    require(existing == order) { "This demo order number is already in use." }
                    // A retry after a successful save must not create a second order.
                    RequestState.Success(existing)
                } else {
                    val updated = (orders + order).sortedWith(orderSorting)
                    storage.write(customerId, json.encodeToString(serializer, updated))
                    revision.value += 1
                    RequestState.Success(order)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                RequestState.Error(error.message ?: "Unable to save your demo order. Please try again.")
            }
        }
    }

    private fun requireCurrentCustomer(): String = currentUserId()
        ?.takeIf { it.isNotBlank() }
        ?: error("Please sign in to view or place demo orders.")

    private suspend fun readOrders(customerId: String): List<MockOrder> {
        val saved = storage.read(customerId) ?: return emptyList()
        return try {
            val orders = json.decodeFromString(serializer, saved)
            orders.forEach { validateOrder(it, customerId) }
            require(orders.map { it.id }.toSet().size == orders.size)
            orders.sortedWith(orderSorting)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Your saved demo orders could not be read. Your existing history has been preserved.",
                error
            )
        }
    }

    private fun validateOrder(order: MockOrder, customerId: String) {
        require(order.customerId == customerId) { "This demo order belongs to a different account." }
        require(order.id.isNotBlank()) { "A demo order number is required." }
        require(order.createdAt >= 0) { "The demo order date is invalid." }
        require(order.items.isNotEmpty()) { "Add an item to your cart before placing a demo order." }
        order.items.forEach { item ->
            require(item.productId.isNotBlank() && item.title.isNotBlank()) { "A cart item is missing product details." }
            require(item.quantity > 0) { "Every cart item must have a positive quantity." }
            require(item.unitPrice.isFinite() && item.unitPrice >= 0) { "A cart item has an invalid price." }
        }
        require(order.totalAmount.isFinite()) { "The demo order total is invalid." }
    }

    private companion object {
        val orderSorting = compareByDescending<MockOrder> { it.createdAt }.thenBy { it.id }
    }
}
