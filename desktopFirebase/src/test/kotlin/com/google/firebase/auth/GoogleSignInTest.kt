package com.google.firebase.auth

import android.app.Application
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.auth.internal.InternalAuthProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GoogleSignInTest {
    private lateinit var server: MockWebServer
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private val storage = ConcurrentHashMap<String, String>()

    @Before fun setUp() {
        FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
            override fun store(key: String, value: String) { storage[key] = value }
            override fun retrieve(key: String): String? = storage[key]
            override fun clear(key: String) { storage.remove(key) }
            override fun log(msg: String) = Unit
        })
        server = MockWebServer().apply { start() }
        app = FirebaseApp.initializeApp(Application(), FirebaseOptions.Builder()
            .setApplicationId("test-app").setApiKey("test-key").setProjectId("test-project").build(),
            UUID.randomUUID().toString())
        auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", server.port)
    }

    @After fun tearDown() {
        auth.signOut()
        auth.client.dispatcher().executorService().shutdown()
        auth.client.connectionPool().evictAll()
        server.shutdown()
        app.delete()
    }

    @Test fun googleCredentialCreatesSessionUsedByFirestore() {
        server.enqueue(MockResponse().setBody(signInResponse()))
        val change = CountDownLatch(1)
        auth.addAuthStateListener(object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(auth: FirebaseAuth) {
                if (auth.currentUser != null) change.countDown()
            }
        })
        val result = await(auth.signInWithCredential(GoogleAuthProvider.getCredential("google+token&value=", null)))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=test-key", request.path)
        val payload = jsonParser.parseToJsonElement(request.body.readUtf8()).jsonObject
        val fields = payload.getValue("postBody").jsonPrimitive.content.split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }
        assertEquals("google+token&value=", fields["id_token"])
        assertEquals("google.com", fields["providerId"])
        assertEquals(true, payload.getValue("returnSecureToken").jsonPrimitive.boolean)
        assertEquals("http://localhost:8080/callback", payload.getValue("requestUri").jsonPrimitive.content)
        assertEquals("firebase-uid", result.user!!.uid)
        assertEquals("person@example.test", auth.currentUser!!.email)
        assertEquals("Test Person", auth.currentUser!!.displayName)
        assertEquals("https://example.test/photo.png", auth.currentUser!!.photoUrl)
        assertTrue(auth.currentUser!!.isEmailVerified)
        assertTrue(change.await(5, TimeUnit.SECONDS))
        // Firestore obtains its credentials from this registered interface.
        val provider = app.get(InternalAuthProvider::class.java)
        assertEquals("firebase-uid", provider.uid)
        assertEquals(token(), await(provider.getAccessToken(false)).token)
        auth.signOut()
        assertNull(auth.currentUser)
        assertNull(provider.uid)
        assertTrue(storage.isEmpty())
    }

    @Test fun invalidTokenDoesNotCreateSessionOrExposeTokens() {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"error":{"message":"INVALID_IDP_RESPONSE secret-token"}}"""))
        val result = runCatching { await(auth.signInWithCredential(GoogleAuthProvider.getCredential("secret-token", null))) }
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull().toString().contains("secret-token"))
        assertNull(auth.currentUser)
    }

    @Test fun malformedResponseFailsInsteadOfLeavingLoginPending() {
        server.enqueue(MockResponse().setBody("<html>bad gateway</html>"))
        assertFails { await(auth.signInWithCredential(GoogleAuthProvider.getCredential("test-token", null))) }
        assertNull(auth.currentUser)
    }

    @Test fun linkingRequirementIsNotTreatedAsSuccessfulLogin() {
        server.enqueue(MockResponse().setBody("""{"needConfirmation":true,"email":"person@example.test"}"""))
        assertFails { await(auth.signInWithCredential(GoogleAuthProvider.getCredential("test-token", null))) }
        assertNull(auth.currentUser)
    }

    @Test fun rejectsMissingCredentialWithoutRequest() {
        assertFailsWith<IllegalArgumentException> { GoogleAuthProvider.getCredential(" ", null) }
        assertEquals(0, server.requestCount)
    }

    @Test fun existingGitLiveWrapperCanSignInReadProfileAndSignOut() = runBlocking {
        server.enqueue(MockResponse().setBody(signInResponse()))
        val wrapper = Firebase.auth(Firebase.app(app.name))
        val credential = dev.gitlive.firebase.auth.GoogleAuthProvider.credential("google-token", null)
        val result = wrapper.signInWithCredential(credential)
        assertEquals("firebase-uid", result.user!!.uid)
        assertEquals("Test Person", result.user!!.displayName)
        assertEquals("person@example.test", result.user!!.email)
        assertEquals(token(), result.user!!.getIdToken(false))
        assertEquals("firebase-uid", wrapper.currentUser!!.uid)
        wrapper.signOut()
        assertNull(wrapper.currentUser)
    }

    @Test fun refreshedTokenPreservesFirebaseUidAndProfile() {
        signIn()
        server.enqueue(MockResponse().setBody(refreshResponse()))
        assertEquals(token("refreshed"), await(auth.getAccessToken(true)).token)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/securetoken.googleapis.com/v1/token?key=test-key", request.path)
        assertEquals("firebase-refresh-token", jsonParser.parseToJsonElement(request.body.readUtf8())
            .jsonObject.getValue("refresh_token").jsonPrimitive.content)
        assertEquals("firebase-uid", auth.currentUser!!.uid)
        assertEquals("Test Person", auth.currentUser!!.displayName)
        assertEquals("person@example.test", auth.currentUser!!.email)
        assertEquals("https://example.test/photo.png", auth.currentUser!!.photoUrl)
    }

    @Test fun delayedRefreshCannotRestoreSignedOutSession() {
        signIn()
        server.enqueue(MockResponse().setBody(refreshResponse()).setBodyDelay(400, TimeUnit.MILLISECONDS))
        val refreshing = auth.getAccessToken(true)
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        auth.signOut()
        assertFails { await(refreshing) }
        assertNull(auth.currentUser)
        assertTrue(storage.isEmpty())
    }

    @Test fun delayedSignInCannotRestoreSignedOutSession() {
        server.enqueue(MockResponse().setBody(signInResponse()).setBodyDelay(400, TimeUnit.MILLISECONDS))
        val pending = auth.signInWithCredential(GoogleAuthProvider.getCredential("test-token", null))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        auth.signOut()
        assertFails { await(pending) }
        assertNull(auth.currentUser)
    }

    private fun signIn() {
        server.enqueue(MockResponse().setBody(signInResponse()))
        await(auth.signInWithCredential(GoogleAuthProvider.getCredential("test-token", null)))
        server.takeRequest(5, TimeUnit.SECONDS)
    }

    private fun signInResponse(): String = """{"localId":"firebase-uid","idToken":"${token()}","refreshToken":"firebase-refresh-token","expiresIn":"3600","email":"person@example.test","displayName":"Test Person","photoUrl":"https://example.test/photo.png","emailVerified":true}"""
    private fun refreshResponse(): String = """{"user_id":"firebase-uid","id_token":"${token("refreshed")}","refresh_token":"new-refresh-token","expires_in":"3600"}"""
    private fun token(marker: String = "original"): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"aud":"test-project","sub":"firebase-uid","marker":"$marker"}""".toByteArray())
        return "eyJhbGciOiJub25lIn0.$payload.test-signature"
    }
    private fun <T> await(task: Task<T>): T = Tasks.await(task, 10, TimeUnit.SECONDS)
}
