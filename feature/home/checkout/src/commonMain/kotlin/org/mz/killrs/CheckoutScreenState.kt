package org.mz.killrs

import org.mz.killrs.shared.component.StateOfUs
import org.mz.killrs.shared.component.statesOfUs
import org.mz.killrs.shared.domain.CartItem
import org.mz.killrs.shared.PhoneNumber

data class CheckoutScreenState(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val city: String? = null,
    val postalCode: String? = null,
    val address: String? = null,
    val stateNames: StateOfUs = StateOfUs(name = "", abbreviation = ""),
    val phoneNumber: PhoneNumber? = null,
    val cart: List<CartItem> = emptyList(),
    val states: List<StateOfUs> = statesOfUs
)
