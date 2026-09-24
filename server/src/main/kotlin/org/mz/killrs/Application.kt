package org.mz.killrs

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val requestIdKey = AttributeKey<String>("requestId")
private val secureHeaders = createApplicationPlugin("SecureHeaders") {
    onCall { call ->
        val id = UUID.randomUUID().toString()
        call.attributes.put(requestIdKey, id)
        call.response.headers.append("X-Request-ID", id)
        call.response.headers.append("Cache-Control", "no-store")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        call.response.headers.append("Referrer-Policy", "no-referrer")
    }
}

fun main() {
    val config = ServerConfig.fromEnvironment()
    val app = initializeFirebase(config)
    val db = FirestoreClient.getFirestore(app)
    val server = embeddedServer(Netty, port = config.port, host = if (config.environment == "emulator") "127.0.0.1" else "0.0.0.0") {
        module(config, FirebaseTokenVerifier(FirebaseAuth.getInstance(app)), FirestoreStore(db))
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1000, 5000); db.close(); app.delete() })
    server.start(wait = true)
}

fun Application.module(config: ServerConfig, verifier: TokenVerifier, store: Store) {
    install(secureHeaders)
    install(ContentNegotiation) { json(apiJson) }
    install(RequestBodyLimit) { bodyLimit { 64 * 1024L } }
    install(CORS) {
        config.allowedOrigins.forEach { origin -> val uri = URI(origin); allowHost(uri.rawAuthority, schemes = listOf(uri.scheme)) }
        allowMethod(HttpMethod.Put); allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization); allowHeader(HttpHeaders.ContentType)
        allowHeader("Idempotency-Key"); allowHeader(HttpHeaders.IfMatch)
        exposeHeader("X-Request-ID"); exposeHeader(HttpHeaders.ETag)
        allowCredentials = false
    }
    install(StatusPages) {
        exception<ApiException> { call, error -> call.apiError(error.status, error.code, error.message) }
        exception<PayloadTooLargeException> { call, _ -> call.apiError(413, "payload_too_large", "Request body exceeds 64 KiB.") }
        exception<BadRequestException> { call, _ -> call.apiError(400, "invalid_request", "Malformed request body.") }
        exception<UnsupportedMediaTypeException> { call, _ -> call.apiError(415, "unsupported_media_type", "Use application/json.") }
        exception<TimeoutCancellationException> { call, _ -> call.apiError(503, "unavailable", "Service temporarily unavailable.") }
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            // Never log headers, tokens, submitted profiles or exception messages containing data.
            call.application.log.error("request_failed id={} type={}", call.attributes.getOrNull(requestIdKey), error.javaClass.simpleName)
            call.apiError(500, "internal_error", "The request could not be completed.")
        }
        status(HttpStatusCode.Unauthorized) { call, _ -> call.apiError(401, "unauthorized", "A valid, verified Firebase account is required.") }
        status(HttpStatusCode.NotFound) { call, _ -> call.apiError(404, "not_found", "Resource not found.") }
        status(HttpStatusCode.TooManyRequests) { call, _ -> call.apiError(429, "rate_limited", "Too many requests. Retry later.") }
    }
    install(RateLimit) {
        register(RateLimitName("edge")) {
            rateLimiter(limit = 600, refillPeriod = 60.seconds)
            // The direct peer is trusted; client-supplied forwarded headers are not.
            requestKey { it.request.local.remoteHost }
        }
        register(RateLimitName("user")) {
            rateLimiter(limit = config.requestsPerMinute, refillPeriod = 60.seconds)
            requestKey { it.principal<Identity>()!!.uid }
        }
        register(RateLimitName("drafts")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { it.principal<Identity>()!!.uid }
        }
    }
    install(Authentication) {
        bearer("firebase") {
            realm = "MzBotanicals"
            authenticate { credential -> if (credential.token.length > 8192) null else verifier.verify(credential.token) }
        }
    }
    routing {
        get("/health/live") { call.respond(mapOf("status" to "ok")) }
        rateLimit(RateLimitName("edge")) {
            get("/health/ready") {
                try { store.ready(); call.respond(mapOf("status" to "ready")) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { call.apiError(503, "unavailable", "Service temporarily unavailable.") }
            }
            route("/v1") {
                get("/capabilities") { call.respond(mapOf("paymentsEnabled" to false, "inventoryReservationsEnabled" to false)) }
                authenticate("firebase") {
                    rateLimit(RateLimitName("user")) {
                        get("/me") { call.respond(store.profile(call.identity()) ?: missing()) }
                        put("/me") {
                            val input = call.receive<ProfileInput>(); input.validate()
                            call.respond(store.saveProfile(call.identity(), input))
                        }
                        get("/products") {
                            val value = call.request.queryParameters["limit"]
                            val limit = value?.toIntOrNull() ?: if (value == null) 25 else invalid("Invalid limit.")
                            if (limit !in 1..100) invalid("Limit must be between 1 and 100.")
                            val after = call.request.queryParameters["after"]?.let(::validateId)
                            call.respond(store.products(limit, after))
                        }
                        get("/products/{id}") {
                            val product = store.product(call.resourceId()) ?: missing()
                            call.response.headers.append(HttpHeaders.ETag, "\"${product.revision}\"")
                            call.respond(product)
                        }
                        put("/admin/products/{id}") {
                            val admin = call.requireAdmin()
                            val input = call.receive<ProductInput>(); input.validate()
                            val product = store.saveProduct(call.resourceId(), input, call.expectedRevision(), admin.uid)
                            call.response.headers.append(HttpHeaders.ETag, "\"${product.revision}\"")
                            call.respond(product)
                        }
                        delete("/admin/products/{id}") {
                            val admin = call.requireAdmin()
                            store.deleteProduct(call.resourceId(), call.expectedRevision(), admin.uid)
                            call.respond(HttpStatusCode.NoContent)
                        }
                        get("/cart") { call.respond(store.cart(call.identity().uid)) }
                        put("/cart") { call.respond(store.saveCart(call.identity().uid, call.receive<CartInput>().validated())) }
                        post("/quotes") { call.respond(store.quote(call.receive<CartInput>().validated(false))) }
                        rateLimit(RateLimitName("drafts")) {
                            post("/order-drafts") {
                                val key = call.request.headers["Idempotency-Key"] ?: invalid("Idempotency-Key is required.")
                                val result = store.createDraft(call.identity().uid, key, call.receive<CartInput>().validated(false))
                                call.respond(if (result.created) HttpStatusCode.Created else HttpStatusCode.OK, result.draft)
                            }
                        }
                        get("/order-drafts/{id}") {
                            val draft = store.draft(call.resourceId()) ?: missing()
                            val user = call.identity()
                            if (draft.customerId != user.uid && !user.admin) missing()
                            call.respond(draft)
                        }
                        post("/checkout") { throw ApiException(503, "payments_unavailable", "Payments are not configured. No order was placed or charged.") }
                    }
                }
            }
        }
    }
}
private fun ApplicationCall.identity(): Identity = principal<Identity>() ?: throw ApiException(401, "unauthorized", "Sign in is required.")
private fun ApplicationCall.requireAdmin(): Identity = identity().also { if (!it.admin) throw ApiException(403, "forbidden", "Administrator access is required.") }
private fun ApplicationCall.resourceId(): String = validateId(parameters["id"] ?: invalid("Resource identifier is required."))
private fun ApplicationCall.expectedRevision(): Long {
    val raw = request.headers[HttpHeaders.IfMatch] ?: throw ApiException(428, "precondition_required", "If-Match is required; use the product ETag, or \"0\" to create.")
    if (!Regex("\"[0-9]{1,18}\"").matches(raw)) invalid("If-Match must contain a quoted revision number.")
    return raw.trim('"').toLong()
}
private suspend fun ApplicationCall.apiError(status: Int, code: String, message: String) {
    respond(HttpStatusCode.fromValue(status), ApiError(code, message, attributes.getOrNull(requestIdKey) ?: UUID.randomUUID().toString()))
}
