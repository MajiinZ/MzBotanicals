package org.mz.killrs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.mz.data.MockOrderRepository
import org.mz.killrs.shared.domain.MockOrder
import org.mz.killrs.shared.domain.MockOrderItem
import org.mz.data.domain.CustomerRepository
import org.mz.data.domain.ProductRepository
import org.mz.killrs.shared.component.StateOfUs
import org.mz.killrs.shared.component.statesOfUs
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.util.RequestState
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CheckoutViewModel(
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val mockOrderRepository: MockOrderRepository
) : ViewModel() {

    var screenReady: RequestState<Unit> by mutableStateOf(RequestState.Loading)
    var screenState: CheckoutScreenState by mutableStateOf(CheckoutScreenState())
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    private var pendingOrder: Pair<CheckoutScreenState, MockOrder>? = null
    private var completedOrder: MockOrder? = null

    val isFormValid: Boolean
        get() = with(screenState) {
            screenReady.isSuccess() && id.isNotBlank() && cart.isNotEmpty() &&
                    firstName.trim().length in 3..50 &&
                    lastName.trim().length in 3..50 &&
                    email.contains("@") &&
                    city?.trim()?.length in 3..50 &&
                    address?.trim()?.length in 3..50 &&
                    stateNames.abbreviation.isNotBlank() &&
                    phoneNumber?.number?.length in 5..30
        }

    init {
        viewModelScope.launch {
            customerRepository.readCustomerFlow().collectLatest { data ->
                if (data.isSuccess()) {
                    val fetchCustomer = data.getSuccessData()
                    screenState = if (screenState.id == fetchCustomer.id) {
                        // Cart refreshes must not discard delivery details being edited.
                        screenState.copy(cart = fetchCustomer.cart)
                    } else CheckoutScreenState(
                        id = fetchCustomer.id,
                        firstName = fetchCustomer.firstName,
                        lastName = fetchCustomer.lastName,
                        email = fetchCustomer.email,
                        city = fetchCustomer.city,
                        postalCode = fetchCustomer.zip,
                        address = fetchCustomer.address,
                        phoneNumber = fetchCustomer.phoneNumber ?: PhoneNumber(
                            number = "",

                        ),
                        stateNames = statesOfUs.firstOrNull { it.abbreviation == fetchCustomer.state || it.name == fetchCustomer.state }
                            ?: StateOfUs("", ""),
                        cart = fetchCustomer.cart
                    )
                    screenReady = RequestState.Success(Unit)
                } else if (data.isError()) {
                    screenReady = RequestState.Error(data.getErrorMessage())
                }
            }
        }
    }

    // Input update functions
    fun updateFirstName(value: String) = run {
        screenState = screenState.copy(firstName = value.filter { it.isLetter() || it.isWhitespace() })
    }

    fun updateLastName(value: String) = run {
        screenState = screenState.copy(lastName = value.filter { it.isLetter() || it.isWhitespace() })
    }

    fun updateEmail(value: String) {
        screenState = screenState.copy(email = value.trim())
    }

    fun updateCity(value: String) = run {
        screenState = screenState.copy(city = value.filter { it.isLetterOrDigit() || it.isWhitespace() })
    }

    fun updateAddress(value: String) = run {
        screenState = screenState.copy(address = value)
    }

    fun updateState(value: String) = run {
        val selectedState = statesOfUs.firstOrNull {
            it.abbreviation.equals(value.trim(), ignoreCase = true) ||
                    it.name.equals(value.trim(), ignoreCase = true)
        }
        // Preserve the text while typing: "Ca" must not expand halfway through "California".
        screenState = screenState.copy(
            stateNames = StateOfUs(value, selectedState?.abbreviation.orEmpty())
        )
    }

    fun updatePhoneNumber(value: String) = run {
        screenState = screenState.copy(
            phoneNumber = PhoneNumber(value.filter { it.isDigit() })
        )
    }

    fun startMockAeropayCheckout(
        expectedTotal: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isSubmitting) return
        if (completedOrder != null && completedOrder?.customerId == customerRepository.getCurrentUserId()) {
            onSuccess()
            return
        }
        if (!isFormValid) return

        val checkout = screenState
        isSubmitting = true
        viewModelScope.launch {
            try {
                check(customerRepository.getCurrentUserId() == checkout.id) {
                    "Your account changed. Please reopen checkout."
                }
                val order = pendingOrder?.takeIf { it.first == checkout }?.second
                    ?: withTimeout(15_000) { createMockOrder(checkout) }.also {
                        pendingOrder = checkout to it
                    }
                require(expectedTotal.isFinite() && abs(order.totalAmount - expectedTotal) < 0.005) {
                    "Your cart total changed. Return to your cart and reopen checkout."
                }
                when (val result = mockOrderRepository.placeOrder(order)) {
                    is RequestState.Success -> {
                        completedOrder = result.data
                        onSuccess()
                    }
                    is RequestState.Error -> onError(result.message)
                    else -> onError("Unable to save your demo order. Please try again.")
                }
            } catch (_: TimeoutCancellationException) {
                onError("Loading your cart took too long. Please try again.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.message ?: "Unable to save your demo order. Please try again.")
            } finally {
                isSubmitting = false
            }
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    private suspend fun createMockOrder(checkout: CheckoutScreenState): MockOrder = coroutineScope {
        // Read each distinct product so every cart size is supported, including more than 10 IDs.
        val products = checkout.cart.map { it.productId }.distinct().map { id ->
            async {
                when (val result = productRepository.readProductByIdFlow(id)
                    .first { it.isSuccess() || it.isError() }) {
                    is RequestState.Success -> result.data
                    is RequestState.Error -> error(result.message)
                    else -> error("Unable to load a cart item.")
                }
            }
        }.awaitAll().associateBy { it.id }

        MockOrder(
            id = Uuid.random().toString(),
            customerId = checkout.id,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            customerName = "${checkout.firstName.trim()} ${checkout.lastName.trim()}",
            email = checkout.email.trim(),
            shippingAddress = listOfNotNull(
                checkout.address?.trim(), checkout.city?.trim(),
                checkout.stateNames.abbreviation, checkout.postalCode
            ).filter { it.isNotBlank() }.joinToString(", "),
            items = checkout.cart.map { cartItem ->
                val product = products[cartItem.productId]
                    ?: error("A cart item is no longer available. Please review your cart.")
                MockOrderItem(
                    productId = product.id,
                    title = product.title,
                    quantity = cartItem.quantity,
                    unitPrice = product.price
                )
            }
        )
    }
}
