package org.mz.killrs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.mz.data.domain.CustomerRepository
import org.mz.killrs.shared.component.StateOfUs
import org.mz.killrs.shared.component.statesOfUs
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.util.RequestState

class CheckoutViewModel(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    var screenReady: RequestState<Unit> by mutableStateOf(RequestState.Loading)
    var screenState: CheckoutScreenState by mutableStateOf(CheckoutScreenState())
        private set

    val isFormValid: Boolean
        get() = with(screenState) {
            firstName.length in 3..50 &&
                    lastName.length in 3..50 &&
                    city?.length in 3..50 &&
                    address?.length in 3..50 &&
                    phoneNumber?.number?.length in 5..30
        }

    init {
        viewModelScope.launch {
            customerRepository.readCustomerFlow().collectLatest { data ->
                if (data.isSuccess()) {
                    val fetchCustomer = data.getSuccessData()
                    screenState = CheckoutScreenState(
                        id = fetchCustomer.id,
                        firstName = fetchCustomer.firstName,
                        lastName = fetchCustomer.lastName,
                        email = fetchCustomer.email,
                        city = fetchCustomer.city,
                        postalCode = fetchCustomer.zip?.toIntOrNull(),
                        address = fetchCustomer.address,
                        phoneNumber = fetchCustomer.phoneNumber ?: PhoneNumber(
                            number = "",

                        ),
                        stateNames = statesOfUs.firstOrNull { it.abbreviation == fetchCustomer.state || it.name == fetchCustomer.state }
                            ?: StateOfUs("Unknown", "UK"),
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

    fun updateCity(value: String) = run {
        screenState = screenState.copy(city = value.filter { it.isLetterOrDigit() || it.isWhitespace() })
    }

    fun updateAddress(value: String) = run {
        screenState = screenState.copy(address = value.filter { it.isLetterOrDigit() || it.isWhitespace() })
    }

    fun updateState(value: String) = run {
        val selectedState = statesOfUs.firstOrNull { it.abbreviation == value || it.name == value }
            ?: StateOfUs("Unknown", "UK")
        screenState = screenState.copy(stateNames = selectedState)
    }

    fun updatePhoneNumber(value: String) = run {
        screenState = screenState.copy(
            phoneNumber = PhoneNumber(value.filter { it.isDigit() })
        )
    }

    fun startMockAeropayCheckout(onSuccess: () -> Unit) {
        if (!isFormValid) return

        // Demo only: no payment request, order creation, or cart mutation.
        onSuccess()
    }
}
