package org.mz.killrs

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.*

@EnabledIfEnvironmentVariable(named = "FIREBASE_AUTH_EMULATOR_HOST", matches = "(127\\.0\\.0\\.1|localhost):[0-9]+")
class FirebaseAuthIntegrationTest {
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var verifier: FirebaseTokenVerifier
    @BeforeTest fun setup() {
        val config = ServerConfig.fromEnvironment()
        require(config.environment == "emulator")
        app = initializeFirebase(config)
        auth = FirebaseAuth.getInstance(app)
        verifier = FirebaseTokenVerifier(auth)
    }
    @AfterTest fun cleanup() { if (::app.isInitialized) app.delete() }
    private fun account(verified: Boolean, admin: Boolean = false): Pair<String, String> {
        val uid = "test-${UUID.randomUUID()}"
        val email = "$uid@example.com"
        val password = UUID.randomUUID().toString()
        auth.createUser(UserRecord.CreateRequest().setUid(uid).setEmail(email).setPassword(password).setEmailVerified(verified))
        if (admin) auth.setCustomUserClaims(uid, mapOf("admin" to true))
        val host = System.getenv("FIREBASE_AUTH_EMULATOR_HOST")
        val body = buildJsonObject { put("email", email); put("password", password); put("returnSecureToken", true) }
        val request = HttpRequest.newBuilder(URI("http://$host/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator-only"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200)
        return uid to apiJson.parseToJsonElement(response.body()).jsonObject.getValue("idToken").jsonPrimitive.content
    }
    @Test fun `verified users are accepted and unverified users are denied`() = runBlocking<Unit> {
        val (uid, token) = account(true)
        assertEquals(uid, verifier.verify(token)?.uid)
        assertFalse(verifier.verify(token)!!.admin)
        assertNull(verifier.verify(account(false).second))
    }
    @Test fun `disabled accounts are denied even with an existing token`() = runBlocking<Unit> {
        val (uid, token) = account(true)
        auth.updateUser(UserRecord.UpdateRequest(uid).setDisabled(true))
        assertNull(verifier.verify(token))
    }
    @Test fun `removed admin claims take effect on API requests with old tokens`() = runBlocking<Unit> {
        val (uid, token) = account(true, admin = true)
        assertTrue(verifier.verify(token)!!.admin)
        auth.setCustomUserClaims(uid, emptyMap())
        assertFalse(verifier.verify(token)!!.admin)
    }
    @Test fun `malformed tokens are denied`() = runBlocking<Unit> {
        assertNull(verifier.verify("not-a-token"))
    }
}
