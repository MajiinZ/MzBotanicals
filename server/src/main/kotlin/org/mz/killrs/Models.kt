package org.mz.killrs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val apiJson = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
@Serializable data class ApiError(val code: String, val message: String, val requestId: String)
class ApiException(val status: Int, val code: String, override val message: String) : RuntimeException(message)
fun invalid(message: String): Nothing = throw ApiException(400, "invalid_request", message)
fun missing(): Nothing = throw ApiException(404, "not_found", "Resource not found.")
fun conflict(message: String): Nothing = throw ApiException(409, "conflict", message)
data class Identity(val uid: String, val email: String, val admin: Boolean = false)

@Serializable data class ProductInput(
    val title: String,
    val description: String,
    val priceMinor: Long,
    val thumbnail: String,
    val category: String,
    val amountOfSeeds: String? = null,
    val strains: List<String> = emptyList(),
    val isPopular: Boolean = false,
    val isNew: Boolean = false,
    val isDiscounted: Boolean = false,
)
@Serializable data class Product(
    val id: String,
    val title: String,
    val description: String,
    val priceMinor: Long,
    val thumbnail: String,
    val category: String,
    val amountOfSeeds: String? = null,
    val strains: List<String> = emptyList(),
    val isPopular: Boolean = false,
    val isNew: Boolean = false,
    val isDiscounted: Boolean = false,
    val createdAt: Long,
    val revision: Long = 1,
    val currency: String = "USD",
)
@Serializable data class ProductPage(val items: List<Product>, val nextCursor: String?)
@Serializable data class ProfileInput(
    val firstName: String,
    val lastName: String,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val phoneNumber: String? = null,
)
@Serializable data class CustomerProfile(val id: String, val email: String, val profile: ProfileInput)
@Serializable data class CartLine(val productId: String, val quantity: Int)
@Serializable data class CartInput(val items: List<CartLine>)
@Serializable data class QuoteLine(
    val productId: String, val title: String, val quantity: Int,
    val unitPriceMinor: Long, val subtotalMinor: Long,
)
@Serializable data class Quote(
    val items: List<QuoteLine>, val subtotalMinor: Long, val currency: String = "USD",
    val shippingMinor: Long? = null, val taxMinor: Long? = null,
    val paymentsEnabled: Boolean = false,
)
@Serializable data class OrderDraft(
    val id: String, val customerId: String, val quote: Quote,
    val createdAt: Long, val expiresAt: Long, val status: String = "DRAFT",
)
@Serializable data class DraftRecord(val draft: OrderDraft, val fingerprint: String)
data class DraftResult(val draft: OrderDraft, val created: Boolean)

fun validateId(id: String): String {
    if (!Regex("[A-Za-z0-9_-]{1,128}").matches(id)) invalid("Invalid resource identifier.")
    return id
}
fun validateText(value: String, field: String, max: Int, required: Boolean = true) {
    if (value.length > max || (required && value.isBlank()) || value.any { it.code < 32 || it.code == 127 }) invalid("Invalid $field.")
}
fun ProductInput.validate() {
    validateText(title, "title", 160)
    validateText(description, "description", 5000)
    if (priceMinor !in 1..10_000_000) invalid("priceMinor must be between 1 and 10000000 USD cents.")
    validateText(category, "category", 80)
    val url = runCatching { java.net.URI(thumbnail) }.getOrNull()
    if (thumbnail.length > 2048 || url?.scheme != "https" || url.host.isNullOrBlank() || url.userInfo != null) invalid("thumbnail must be an HTTPS URL.")
    if (strains.size > 20) invalid("Too many strains.")
    strains.forEach { validateText(it, "strain", 100) }
    amountOfSeeds?.let { if (it.toIntOrNull() !in 1..1000) invalid("Invalid amountOfSeeds.") }
}
fun ProfileInput.validate() {
    validateText(firstName, "firstName", 80)
    validateText(lastName, "lastName", 80)
    address?.let { validateText(it, "address", 240) }
    city?.let { validateText(it, "city", 100) }
    state?.let { validateText(it, "state", 80) }
    postalCode?.let { validateText(it, "postalCode", 20) }
    phoneNumber?.let { if (!Regex("\\+?[0-9 ()-]{5,30}").matches(it)) invalid("Invalid phoneNumber.") }
}
fun CartInput.validated(allowEmpty: Boolean = true): CartInput {
    if (items.size > 50 || (!allowEmpty && items.isEmpty())) invalid("A cart must contain ${if (allowEmpty) 0 else 1} to 50 products.")
    items.forEach { validateId(it.productId); if (it.quantity !in 1..10) invalid("Quantity must be between 1 and 10.") }
    if (items.map { it.productId }.distinct().size != items.size) invalid("Duplicate products are not allowed.")
    return copy(items = items.sortedBy { it.productId })
}
fun quote(items: CartInput, products: List<Product>): Quote {
    val byId = products.associateBy { it.id }
    val lines = items.validated(allowEmpty = false).items.map { line ->
        val product = byId[line.productId] ?: conflict("A product is no longer available.")
        if (product.priceMinor !in 1..10_000_000 || product.currency != "USD") conflict("A product needs a pricing update.")
        QuoteLine(product.id, product.title, line.quantity, product.priceMinor, Math.multiplyExact(product.priceMinor, line.quantity.toLong()))
    }
    return Quote(lines, lines.sumOf { it.subtotalMinor })
}
