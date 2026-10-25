package org.mz.killrs.shared.domain

import kotlinx.serialization.Serializable

/** A local demonstration order. It never represents a payment or a fulfillment request. */
@Serializable
data class MockOrder(
    val id: String,
    val customerId: String,
    val createdAt: Long,
    val customerName: String,
    val email: String,
    val shippingAddress: String,
    val items: List<MockOrderItem>
) {
    val totalAmount: Double
        get() = items.sumOf { it.unitPrice * it.quantity }
}

@Serializable
data class MockOrderItem(
    val productId: String,
    val title: String,
    val quantity: Int,
    val unitPrice: Double
)
