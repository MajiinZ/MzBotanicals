package org.mz.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.mz.killrs.shared.domain.MockOrder
import org.mz.killrs.shared.domain.MockOrderItem
import org.mz.killrs.shared.util.RequestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockOrderRepositoryTest {
    private val storage = MemoryStorage()
    private var userId: String? = "customer-a"
    private fun repository() = MockOrderRepository(storage) { userId }

    @Test
    fun historySurvivesRepositoryRecreationAndSortsNewestFirst() = runBlocking {
        val repository = repository()
        val older = order("older", createdAt = 100)
        val newer = order("newer", createdAt = 200)
        assertIs<RequestState.Success<MockOrder>>(repository.placeOrder(newer))
        assertIs<RequestState.Success<MockOrder>>(repository.placeOrder(older))
        val restored = history(repository())
        assertEquals(listOf(newer, older), restored)
        assertEquals(25.0, restored.first().totalAmount)
    }

    @Test
    fun concurrentRetriesSaveOneOrder() = runBlocking {
        val repository = repository()
        val order = order()
        List(10) { async { repository.placeOrder(order) } }.awaitAll().forEach {
            assertIs<RequestState.Success<MockOrder>>(it)
        }
        assertEquals(1, storage.writes)
        assertEquals(listOf(order), history(repository))
    }

    @Test
    fun reusedIdCannotReplaceAnExistingOrder() = runBlocking {
        val repository = repository()
        val original = order()
        repository.placeOrder(original)
        assertIs<RequestState.Error>(repository.placeOrder(original.copy(customerName = "Changed")))
        assertEquals(listOf(original), history(repository))
        assertEquals(1, storage.writes)
    }

    @Test
    fun ordersAreSeparatedByAccount() = runBlocking {
        val repository = repository()
        val original = order()
        repository.placeOrder(original)
        userId = "customer-b"
        assertTrue(history(repository).isEmpty())
        assertIs<RequestState.Error>(repository.placeOrder(original))
        val second = order("second", customerId = "customer-b")
        repository.placeOrder(second)
        assertEquals(listOf(second), history(repository))
        userId = "customer-a"
        assertEquals(listOf(original), history(repository))
    }

    @Test
    fun signedOutUserCannotReadOrSaveHistory() = runBlocking {
        userId = null
        val repository = repository()
        assertIs<RequestState.Error>(repository.observeOrders().first { !it.isLoading() })
        assertIs<RequestState.Error>(repository.placeOrder(order()))
        assertEquals(0, storage.writes)
    }

    @Test
    fun failedSavePreservesHistoryAndRetrySucceedsOnce() = runBlocking {
        val repository = repository()
        val original = order("original")
        repository.placeOrder(original)
        val saved = storage.values.toMap()
        storage.writeFailure = IllegalStateException("Disk full")
        val next = order("next", createdAt = 200)
        assertIs<RequestState.Error>(repository.placeOrder(next))
        assertEquals(saved, storage.values)
        storage.writeFailure = null
        assertIs<RequestState.Success<MockOrder>>(repository.placeOrder(next))
        assertIs<RequestState.Success<MockOrder>>(repository.placeOrder(next))
        assertEquals(listOf(next, original), history(repository))
        assertEquals(2, storage.writes)
    }

    @Test
    fun corruptHistoryIsReportedWithoutOverwritingIt() = runBlocking {
        storage.values["customer-a"] = "invalid saved history"
        val repository = repository()
        assertIs<RequestState.Error>(repository.observeOrders().first { !it.isLoading() })
        assertIs<RequestState.Error>(repository.placeOrder(order()))
        assertEquals("invalid saved history", storage.values["customer-a"])
        assertEquals(0, storage.writes)
    }

    @Test
    fun invalidCartSnapshotsAreRejected() = runBlocking {
        val repository = repository()
        val valid = order()
        val item = valid.items.single()
        listOf(
            valid.copy(items = emptyList()),
            valid.copy(items = listOf(item.copy(quantity = 0))),
            valid.copy(items = listOf(item.copy(unitPrice = Double.NaN))),
            valid.copy(items = listOf(item.copy(unitPrice = -1.0))),
            valid.copy(items = listOf(item.copy(productId = "")))
        ).forEach { assertIs<RequestState.Error>(repository.placeOrder(it)) }
        assertEquals(0, storage.writes)
    }

    @Test
    fun accountChangeDuringReadDoesNotExposePreviousAccountHistory() = runBlocking {
        val repository = repository()
        repository.placeOrder(order())
        storage.onRead = { userId = "customer-b" }
        assertIs<RequestState.Error>(repository.observeOrders().first { !it.isLoading() })
        Unit
    }

    @Test
    fun cancellationIsNotReportedAsSuccessOrStorageError() = runBlocking {
        storage.writeFailure = CancellationException("Checkout closed")
        assertFailsWith<CancellationException> { repository().placeOrder(order()) }
        assertEquals(0, storage.writes)
    }

    private suspend fun history(repository: MockOrderRepository): List<MockOrder> =
        assertIs<RequestState.Success<List<MockOrder>>>(
            repository.observeOrders().first { !it.isLoading() }
        ).data

    private fun order(
        id: String = "demo-order",
        customerId: String = "customer-a",
        createdAt: Long = 100
    ) = MockOrder(
        id = id,
        customerId = customerId,
        createdAt = createdAt,
        customerName = "Demo Customer",
        email = "demo@example.com",
        shippingAddress = "123 Example Street, Example City, CA, 01234",
        items = listOf(MockOrderItem("product-1", "Demo product", 2, 12.5))
    )

    private class MemoryStorage : MockOrderStorage {
        val values = mutableMapOf<String, String>()
        var writes = 0
        var writeFailure: Exception? = null
        var onRead: () -> Unit = {}

        override suspend fun read(customerId: String): String? {
            val value = values[customerId]
            onRead()
            return value
        }

        override suspend fun write(customerId: String, value: String) {
            writeFailure?.let { throw it }
            values[customerId] = value
            writes += 1
        }
    }
}
