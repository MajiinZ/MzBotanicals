package org.mz.killrs.profile

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
import org.mz.killrs.shared.domain.Customer
import org.mz.killrs.shared.PhoneNumber
import org.mz.killrs.shared.util.RequestState

data class ProfileScreenState(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val city: String = "",
    val zipCode: String = "",      // ✅ keep as String so TextField input works
    val address: String = "",
    val phoneNumber: PhoneNumber? = null,
    val state: StateOfUs = StateOfUs("", "")
)

class ProfileViewModel(
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    var screenReady: RequestState<Unit> by mutableStateOf(RequestState.Loading)
    var screenState: ProfileScreenState by mutableStateOf(ProfileScreenState())
        private set

    private var customer: Customer? = null
    var profileImageUrl: String? by mutableStateOf(null)
        private set
    var isSavingPhoto by mutableStateOf(false)
        private set

    fun savePhoto(file: dev.gitlive.firebase.storage.File?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isSavingPhoto) return
        isSavingPhoto = true
        viewModelScope.launch {
            try {
                when (val result = customerRepository.updateProfilePhoto(file)) {
                    is RequestState.Success -> {
                        profileImageUrl = result.data
                        onSuccess()
                    }
                    is RequestState.Error -> onError(result.message)
                    else -> Unit
                }
            } finally { isSavingPhoto = false }
        }
    }

    // ✅ Improved validation
    val isFormValid: Boolean
        get() = with(screenState) {
            firstName.length in 2..50 &&
                    lastName.length in 2..50 &&
                    city.length in 3..50 &&
                    state.abbreviation.length == 2 &&
                    zipCode.length in 5..10 && zipCode.all { it.isDigit() }
        }

    init {
        viewModelScope.launch {
            customerRepository.readCustomerFlow().collectLatest { data ->
                if (data.isSuccess()) {
                    val fetchedCustomer = data.getSuccessData()
                    customer = fetchedCustomer
                    profileImageUrl = fetchedCustomer.profileImageUrl
                    screenState = ProfileScreenState(
                        id = fetchedCustomer.id,
                        firstName = fetchedCustomer.firstName,
                        lastName = fetchedCustomer.lastName,
                        email = fetchedCustomer.email,
                        city = fetchedCustomer.city.orEmpty(),
                        address = fetchedCustomer.address.orEmpty(),
                        phoneNumber = fetchedCustomer.phoneNumber,
                        zipCode = fetchedCustomer.zip.orEmpty(), // ✅ use actual zip, not length
                        state = statesOfUs.firstOrNull { it.abbreviation == fetchedCustomer.state || it.name == fetchedCustomer.state }
                            ?: StateOfUs("", "")
                    )
                    screenReady = RequestState.Success(Unit)
                } else if (data.isError()) {
                    screenReady = RequestState.Error(data.getErrorMessage())
                }
            }
        }
    }

    // ✅ Update functions
    fun updateFirstName(value: String) {
        screenState = screenState.copy(firstName = value)
    }

    fun updateLastName(value: String) {
        screenState = screenState.copy(lastName = value)
    }

    fun updateCity(value: String) {
        screenState = screenState.copy(city = value)
    }

    fun updatePostalCode(value: String) {
        screenState = screenState.copy(zipCode = value.filter(Char::isDigit).take(10))
    }

    fun updateAddress(value: String) {
        screenState = screenState.copy(address = value)
    }

    fun updatePhoneNumber(value: String) {
        screenState = screenState.copy(phoneNumber = PhoneNumber(value.take(30)))
    }

    fun updateState(value: String) {
        val selectedState = statesOfUs.firstOrNull { it.abbreviation == value || it.name == value }
            ?: StateOfUs(value, value.uppercase().take(2))
        screenState = screenState.copy(state = selectedState)
    }

    // ✅ Save changes back to repository
    fun updateCustomer(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val original = customer
            if (original == null) {
                onError("Profile is still loading.")
                return@launch
            }
            customerRepository.updateCustomer(
                customer = original.copy(
                    firstName = screenState.firstName.trim(),
                    lastName = screenState.lastName.trim(),
                    city = screenState.city.trim(),
                    zip = screenState.zipCode,
                    address = screenState.address.trim(),
                    phoneNumber = screenState.phoneNumber,
                    state = screenState.state.abbreviation
                ),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }
}
