package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.mz.data.domain.OrderRepository
import org.mz.killrs.shared.domain.Order

class OrderRepositoryImpl : OrderRepository {
    override fun getCurrentUserId() = Firebase.auth.currentUser?.uid
    override suspend fun createTheOrder(order: Order, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // A client-provided order total must never become an authoritative purchase.
        onError("Payments are not configured. No order has been placed or charged.")
    }
}
