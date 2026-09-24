package org.mz.killrs

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Date

data class ServerConfig(
    val projectId: String,
    val environment: String = "production",
    val port: Int = 8080,
    val allowedOrigins: List<String> = emptyList(),
    val requestsPerMinute: Int = 120,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            val mode = env["APP_ENV"] ?: "production"
            require(mode in setOf("production", "emulator")) { "APP_ENV must be production or emulator" }
            val project = requireNotNull(env["FIREBASE_PROJECT_ID"]) { "FIREBASE_PROJECT_ID is required" }
            require(Regex("[a-z][a-z0-9-]{4,62}").matches(project)) { "Invalid FIREBASE_PROJECT_ID" }
            val emulatorNames = listOf("FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST", "FIREBASE_STORAGE_EMULATOR_HOST")
            if (mode == "production") {
                require(emulatorNames.none { env.containsKey(it) }) { "Emulator settings are forbidden in production" }
                require(!project.startsWith("demo-")) { "Production requires a real project" }
            } else {
                require(project.startsWith("demo-")) { "Emulators require a demo- project" }
                emulatorNames.take(2).forEach { name ->
                    val host = URI("http://${requireNotNull(env[name]) { "$name is required" }}")
                    require(host.host in setOf("127.0.0.1", "localhost", "[::1]") && host.port in 1..65535 && host.rawPath.isNullOrEmpty() && host.userInfo == null && host.rawQuery == null && host.rawFragment == null) { "Emulators must use a loopback host and port" }
                }
            }
            val port = env["PORT"]?.toIntOrNull() ?: if (env.containsKey("PORT")) error("Invalid PORT") else 8080
            require(port in 1..65535) { "Invalid PORT" }
            val origins = env["ALLOWED_ORIGINS"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            origins.forEach {
                val uri = URI(it)
                require(uri.host != null && uri.userInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null &&
                    (uri.scheme == "https" || (mode == "emulator" && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1")))) { "Invalid allowed origin" }
            }
            return ServerConfig(project, mode, port, origins)
        }
    }
}
fun initializeFirebase(config: ServerConfig): FirebaseApp {
    val credentials = if (config.environment == "emulator") GoogleCredentials.create(AccessToken("emulator-only", Date(Long.MAX_VALUE))) else GoogleCredentials.getApplicationDefault()
    return FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(config.projectId).setCredentials(credentials).build())
}
fun interface TokenVerifier { suspend fun verify(token: String): Identity? }
class FirebaseTokenVerifier(private val auth: FirebaseAuth) : TokenVerifier {
    override suspend fun verify(token: String): Identity? = withContext(Dispatchers.IO) {
        try {
            // Includes signature, expiry, issuer, audience, revocation and disabled-user checks.
            val decoded = auth.verifyIdToken(token, true)
            if (!Regex("[A-Za-z0-9_-]{1,128}").matches(decoded.uid)) return@withContext null
            if (!decoded.isEmailVerified || decoded.email.isNullOrBlank()) return@withContext null
            val admin = decoded.claims["admin"] == true && auth.getUser(decoded.uid).customClaims["admin"] == true
            Identity(decoded.uid, decoded.email, admin)
        } catch (_: FirebaseAuthException) { null }
          catch (_: IllegalArgumentException) { null }
    }
}
