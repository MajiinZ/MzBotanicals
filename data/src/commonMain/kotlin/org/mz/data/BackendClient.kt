package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.mz.killrs.shared.domain.Product
import kotlin.math.roundToLong

class BackendFailure(val status: Int, override val message: String) : Exception(message)

class BackendClient {
    private val client = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 20_000 }
    }
    suspend fun request(method: HttpMethod, path: String, body: JsonElement? = null, revision: Long? = null): JsonObject {
        val base = BackendConfiguration.BASE_URL
        if (base.isEmpty()) throw BackendFailure(503, "The store service is not configured yet.")
        val user = Firebase.auth.currentUser ?: throw BackendFailure(401, "Please sign in.")
        if (!user.isEmailVerified) throw BackendFailure(401, "Verify your email before using the store.")
        suspend fun send(refresh: Boolean): io.ktor.client.statement.HttpResponse {
            val token = user.getIdToken(refresh) ?: throw BackendFailure(401, "Please sign in again.")
            return client.request("$base/v1$path") {
                this.method = method
                bearerAuth(token)
                revision?.let { header(HttpHeaders.IfMatch, "\"$it\"") }
                if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
            }
        }
        var response = send(false)
        if (response.status == HttpStatusCode.Unauthorized) response = send(true)
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (!response.status.isSuccess()) {
            val message = json?.get("message")?.jsonPrimitive?.contentOrNull ?: "The store service could not complete the request."
            throw BackendFailure(response.status.value, message)
        }
        return json ?: JsonObject(emptyMap())
    }
    suspend fun isAdmin(): Boolean {
        val user = Firebase.auth.currentUser ?: return false
        return user.isEmailVerified && user.getIdTokenResult(false).claims["admin"] == true
    }
    suspend fun product(id: String): JsonObject = request(HttpMethod.Get, "/products/${safeId(id)}")
    suspend fun saveProduct(product: Product, revision: Long) {
        val cents = product.price * 100
        require(cents.isFinite() && cents > 0 && kotlin.math.abs(cents - cents.roundToLong()) < 0.000001) { "Enter a valid price with at most two decimal places." }
        request(HttpMethod.Put, "/admin/products/${safeId(product.id)}", buildJsonObject {
            put("title", product.title.lowercase()); put("description", product.description)
            put("priceMinor", cents.roundToLong()); put("thumbnail", product.thumbnail); put("category", product.category)
            put("amountOfSeeds", product.amountOfSeeds?.takeUnless { it == "null" }?.let(::JsonPrimitive) ?: JsonNull)
            put("strains", JsonArray(product.strains.orEmpty().map(::JsonPrimitive)))
            put("isPopular", product.isPopular); put("isNew", product.isNew); put("isDiscounted", product.isDiscounted)
        }, revision)
    }
    fun safeId(id: String): String {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(id)) { "Invalid resource identifier." }
        return id
    }
}

internal suspend fun backendAction(onSuccess: () -> Unit, onError: (String) -> Unit, action: suspend () -> Unit) {
    try { action(); onSuccess() }
    catch (e: CancellationException) { throw e }
    catch (e: BackendFailure) { onError(e.message) }
    catch (e: IllegalArgumentException) { onError(e.message ?: "Please check the information entered.") }
    catch (_: Exception) { onError("Unable to reach the store service. Please try again.") }
}
