package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.mz.data.domain.CustomerRepository
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.domain.CartItem
import org.mz.killrs.shared.domain.Customer
import org.mz.killrs.shared.util.RequestState

class CustomerRepositoryImpl(private val backend: BackendClient) : CustomerRepository {
    private val cartMutex = Mutex()
    override fun getCurrentUserId() = Firebase.auth.currentUser?.uid

    override suspend fun createCustomer(user: FirebaseUser?, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        require(user != null && user.uid == getCurrentUserId()) { "Please sign in." }
        try { backend.request(HttpMethod.Get, "/me") }
        catch (e: BackendFailure) {
            if (e.status != 404) throw e
            backend.request(HttpMethod.Put, "/me", buildJsonObject {
                put("firstName", user.displayName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Customer")
                put("lastName", user.displayName?.split(" ")?.lastOrNull()?.takeIf { it.isNotBlank() } ?: "Customer")
            })
        }
    }
    override fun readCustomerFlow(): Flow<RequestState<Customer>> = flow {
        val uid = getCurrentUserId()
        if (uid == null) { emit(RequestState.Error("Please sign in.")); return@flow }
        try {
            Firebase.firestore.collection("customer").document(uid).snapshots.collect { document ->
                if (!document.exists) { emit(RequestState.Error("Customer profile is not available.")); return@collect }
                emit(RequestState.Success(Customer(
                    id = uid, firstName = document.get("firstName"), lastName = document.get("lastName"),
                    email = Firebase.auth.currentUser?.email.orEmpty(),
                    address = document.get("address"), city = document.get("city"), state = document.get("state"),
                    zip = document.get<String?>("postalCode") ?: document.get<String?>("zip"),
                    phoneNumber = document.get<PhoneNumber?>("phoneNumber"), cart = document.get("cart"),
                    isAdmin = backend.isAdmin(), dateOfBirth = document.get<String?>("dateOfBirth").orEmpty(),
                    gender = document.get<String?>("gender").orEmpty(),
                )))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { emit(RequestState.Error("Unable to load your customer profile.")) }
    }
    override suspend fun updateCustomer(customer: Customer, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        require(customer.id == getCurrentUserId()) { "You can only update your own profile." }
        backend.request(HttpMethod.Put, "/me", buildJsonObject {
            put("firstName", customer.firstName); put("lastName", customer.lastName)
            customer.address?.takeIf { it.isNotBlank() }?.let { put("address", it) }
            customer.city?.takeIf { it.isNotBlank() }?.let { put("city", it) }
            customer.state?.takeIf { it.isNotBlank() }?.let { put("state", it) }
            customer.zip?.takeIf { it.isNotBlank() }?.let { put("postalCode", it) }
            customer.phoneNumber?.number?.takeIf { it.isNotBlank() }?.let { put("phoneNumber", it) }
        })
    }
    private suspend fun updateCart(change: (MutableMap<String, Int>) -> Unit) = cartMutex.withLock {
        val items = backend.request(HttpMethod.Get, "/cart")["items"]!!.jsonArray
        val cart = items.associate { it.jsonObject["productId"]!!.jsonPrimitive.content to it.jsonObject["quantity"]!!.jsonPrimitive.int }.toMutableMap()
        change(cart)
        backend.request(HttpMethod.Put, "/cart", buildJsonObject {
            put("items", JsonArray(cart.map { (id, quantity) -> buildJsonObject { put("productId", id); put("quantity", quantity) } }))
        })
    }
    // Resolve legacy random cart-line IDs before switching to stable product IDs.
    private suspend fun productIdForCartLine(id: String): String {
        val uid = getCurrentUserId() ?: throw BackendFailure(401, "Please sign in.")
        val cart = Firebase.firestore.collection("customer").document(uid).get().get<List<CartItem>>("cart")
        return cart.firstOrNull { it.id == id }?.productId ?: id
    }
    override suspend fun addItemToCart(cartItem: CartItem, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        updateCart { cart -> cart[cartItem.productId] = (cart[cartItem.productId] ?: 0) + cartItem.quantity }
    }
    override suspend fun updateCartItemQuantity(id: String, quantity: Int, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        val productId = productIdForCartLine(id)
        updateCart { cart -> require(cart.containsKey(productId)) { "Cart item no longer exists." }; cart[productId] = quantity }
    }
    override suspend fun deleteCartItem(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        val productId = productIdForCartLine(id)
        updateCart { it.remove(productId) }
    }
    override suspend fun deleteAllCartItems(onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        cartMutex.withLock { backend.request(HttpMethod.Put, "/cart", buildJsonObject { put("items", JsonArray(emptyList())) }) }
    }
    override suspend fun signOut(): RequestState<Unit> = try {
        Firebase.auth.signOut(); RequestState.Success(Unit)
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { RequestState.Error("Unable to sign out. Please try again.") }
}
