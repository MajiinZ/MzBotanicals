package org.mz.killrs.shared.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CheckoutForm(
    modifier: Modifier = Modifier,
    firstName: String,
    onFirstNameChanged: (String) -> Unit,
    lastName: String,
    onLastNameChanged: (String) -> Unit,
    email: String,
    onEmailChanged: (String) -> Unit,
    city: String,
    onCityChanged: (String) -> Unit,
    state: String,
    onStateSelect: (String) -> Unit,
    address: String,
    onAddressChanged: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChanged: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CustomTextField(
            value = firstName,
            onValueChange = onFirstNameChanged,
            placeholder = "First Name",
            error = firstName.length !in 3..50
        )
        CustomTextField(
            value = lastName,
            onValueChange = onLastNameChanged,
            placeholder = "Last Name",
            error = lastName.length !in 3..50
        )
        CustomTextField(
            value = email,
            onValueChange = onEmailChanged,
            placeholder = "Email",
            error = !email.contains("@")
        )
        CustomTextField(
            value = city,
            onValueChange = onCityChanged,
            placeholder = "City",
            error = city.length !in 3..50
        )
        CustomTextField(
            value = state,
            onValueChange = onStateSelect,
            placeholder = "State (name or abbreviation)",
            error = statesOfUs.none {
                it.name.equals(state.trim(), ignoreCase = true) ||
                        it.abbreviation.equals(state.trim(), ignoreCase = true)
            }
        )
        CustomTextField(
            value = address,
            onValueChange = onAddressChanged,
            placeholder = "Address",
            error = address.length !in 3..50
        )
        CustomTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChanged,
            placeholder = "Phone Number",
            error = phoneNumber.length !in 5..30 && phoneNumber.isNotEmpty()
        )
    }
}
