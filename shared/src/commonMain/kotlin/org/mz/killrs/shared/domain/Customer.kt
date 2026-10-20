package org.mz.killrs.shared.domain

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.mz.killrs.shared.PhoneNumber

@Serializable
data class Customer(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,

    @Contextual
    val phoneNumber: PhoneNumber? = null,

    val dateOfBirth: String,
    val gender: String,
    val cart: List<CartItem> = emptyList(),
    val isAdmin: Boolean? = false,
    val profileImageUrl: String? = null
)

@Serializable
data class PhoneNumber(
    val dialCode: Int,
    val number: String,
)
