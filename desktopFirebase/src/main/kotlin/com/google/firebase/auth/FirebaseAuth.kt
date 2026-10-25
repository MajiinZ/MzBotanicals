// Derived from GitLive firebase-java-sdk 0.4.5 (Apache-2.0). Modified for desktop Google sign-in.
package com.google.firebase.auth

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebasePlatform
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.internal.InternalTokenResult
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

val jsonParser = Json { ignoreUnknownKeys = true }

@Serializable
class FirebaseUserImpl private constructor(
    @Transient
    private val app: FirebaseApp = FirebaseApp.getInstance(),
    override val isAnonymous: Boolean,
    override val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val createdAt: Long,
    override val email: String? = null,
    override val displayName: String? = null,
    override val photoUrl: String? = null,
    override val isEmailVerified: Boolean = false,
) : FirebaseUser() {

    constructor(
        app: FirebaseApp,
        data: JsonObject,
        isAnonymous: Boolean = data["isAnonymous"]?.jsonPrimitive?.booleanOrNull ?: false,
        previous: FirebaseUserImpl? = null,
    ) : this(
        app,
        isAnonymous,
        data["uid"]?.jsonPrimitive?.contentOrNull ?: data["user_id"]?.jsonPrimitive?.contentOrNull ?: data["localId"]?.jsonPrimitive?.contentOrNull ?: "",
        data["idToken"]?.jsonPrimitive?.contentOrNull ?: data.getValue("id_token").jsonPrimitive.content,
        data["refreshToken"]?.jsonPrimitive?.contentOrNull ?: data.getValue("refresh_token").jsonPrimitive.content,
        data["expiresIn"]?.jsonPrimitive?.intOrNull ?: data.getValue("expires_in").jsonPrimitive.int,
        data["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
        data["email"]?.jsonPrimitive?.contentOrNull ?: previous?.email,
        data["displayName"]?.jsonPrimitive?.contentOrNull ?: previous?.displayName,
        data["photoUrl"]?.jsonPrimitive?.contentOrNull ?: previous?.photoUrl,
        data["emailVerified"]?.jsonPrimitive?.booleanOrNull
            ?: data["isEmailVerified"]?.jsonPrimitive?.booleanOrNull
            ?: previous?.isEmailVerified ?: false,
    )

    val claims: Map<String, Any?> by lazy {
        jsonParser
            .parseToJsonElement(String(Base64.getUrlDecoder().decode(idToken.split(".")[1])))
            .jsonObject
            .run { value as Map<String, Any?>? }
            .orEmpty()
    }

    val JsonElement.value get(): Any? = when (this) {
        is JsonNull -> null
        is JsonArray -> map { it.value }
        is JsonObject -> jsonObject.mapValues { (_, it) -> it.value }
        is JsonPrimitive -> booleanOrNull ?: doubleOrNull ?: content
        else -> TODO()
    }

    override fun delete(): Task<Void> {
        val source = TaskCompletionSource<Void>()
        val body = RequestBody.create(FirebaseAuth.getInstance(app).json, JsonObject(mapOf("idToken" to JsonPrimitive(idToken))).toString())
        val request = Request.Builder()
            .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/deleteAccount?key=" + app.options.apiKey)
            .post(body)
            .build()
        FirebaseAuth.getInstance(app).client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(FirebaseException(e.toString(), e))
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    FirebaseAuth.getInstance(app).signOut()
                    source.setException(
                        FirebaseAuth.getInstance(app).createAuthInvalidUserException(
                            "deleteAccount",
                            request,
                            response,
                        )
                    )
                } else {
                    source.setResult(null)
                }
            }
        })
        return source.task
    }

    override fun reload(): Task<Void> {
        val source = TaskCompletionSource<Void>()
        FirebaseAuth.getInstance(app).refreshToken(this, source) { null }
        return source.task
    }

    override fun getIdToken(forceRefresh: Boolean) = FirebaseAuth.getInstance(app).getAccessToken(forceRefresh)
}

class FirebaseAuth constructor(val app: FirebaseApp) : InternalAuthProvider {

    val json = MediaType.parse("application/json; charset=utf-8")
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {

        @JvmStatic
        fun getInstance(): FirebaseAuth = getInstance(FirebaseApp.getInstance())

        @JvmStatic
        fun getInstance(app: FirebaseApp): FirebaseAuth = app.get(FirebaseAuth::class.java)
    }

    private val internalIdTokenListeners = CopyOnWriteArrayList<com.google.firebase.auth.internal.IdTokenListener>()
    private val idTokenListeners = CopyOnWriteArrayList<IdTokenListener>()
    private val authStateListeners = CopyOnWriteArrayList<AuthStateListener>()
    private val sessionLock = Any()
    private val sessionVersion = AtomicLong()
    private var emulatorBaseUrl: String? = null

    private fun authUrl(path: String): String =
        "${emulatorBaseUrl ?: "https://"}$path?key=${app.options.apiKey}"

    val currentUser: FirebaseUser?
        get() = user

    val FirebaseApp.key get() = "com.google.firebase.auth.FIREBASE_USER${"[$name]".takeUnless { isDefaultApp }.orEmpty()}"

    @Volatile
    private var user: FirebaseUserImpl? = FirebasePlatform.firebasePlatform
        .runCatching { retrieve(app.key)?.let { FirebaseUserImpl(app, jsonParser.parseToJsonElement(it).jsonObject) } }
        .onFailure { it.printStackTrace() }
        .getOrNull()

        private set(value) {
            if (field != value) {
                val prev = field
                field = value

                if (value == null)
                    FirebasePlatform.firebasePlatform.clear(app.key)
                else {
                    FirebasePlatform.firebasePlatform.store(app.key, jsonParser.encodeToString(FirebaseUserImpl.serializer(), value))
                }

                GlobalScope.launch(Dispatchers.Main) {
                    if (prev?.uid != value?.uid) {
                        authStateListeners.forEach { l -> l.onAuthStateChanged(this@FirebaseAuth) }
                    }

                    if (prev?.idToken != value?.idToken) {
                        val result = InternalTokenResult(value?.idToken)
                        for (listener in internalIdTokenListeners) {
                            Log.i("FirebaseAuth", "Calling onIdTokenChanged for ${value?.uid} on listener $listener")
                            listener.onIdTokenChanged(result)
                        }
                        for (listener in idTokenListeners) {
                            listener.onIdTokenChanged(this@FirebaseAuth)
                        }
                    }
                }
            }
        }

    fun signInAnonymously(): Task<AuthResult> {
        val source = TaskCompletionSource<AuthResult>()
        val body = RequestBody.create(json, JsonObject(mapOf("returnSecureToken" to JsonPrimitive(true))).toString())
        val request = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + app.options.apiKey)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(FirebaseException(e.toString(), e))
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    source.setException(
                        createAuthInvalidUserException("accounts:signUp", request, response)
                    )
                } else {
                    val body = response.body()!!.use { it.string() }
                    user = FirebaseUserImpl(app, jsonParser.parseToJsonElement(body).jsonObject, true)
                    source.setResult(AuthResult { user })
                }
            }
        })
        return source.task
    }

    fun signInWithCustomToken(customToken: String): Task<AuthResult> {
        val source = TaskCompletionSource<AuthResult>()
        val body = RequestBody.create(
            json,
            JsonObject(mapOf("token" to JsonPrimitive(customToken), "returnSecureToken" to JsonPrimitive(true))).toString()
        )
        val request = Request.Builder()
            .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken?key=" + app.options.apiKey)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(FirebaseException(e.toString(), e))
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    source.setException(
                        createAuthInvalidUserException("verifyCustomToken", request, response)
                    )
                } else {
                    val body = response.body()!!.use { it.string() }
                    val user = FirebaseUserImpl(app, jsonParser.parseToJsonElement(body).jsonObject)
                    refreshToken(user, source) { AuthResult { it } }
                }
            }
        })
        return source.task
    }

    fun signInWithEmailAndPassword(email: String, password: String): Task<AuthResult> {
        val source = TaskCompletionSource<AuthResult>()
        val body = RequestBody.create(
            json,
            JsonObject(mapOf("email" to JsonPrimitive(email), "password" to JsonPrimitive(password), "returnSecureToken" to JsonPrimitive(true))).toString()
        )
        val request = Request.Builder()
            .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=" + app.options.apiKey)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(FirebaseException(e.toString(), e))
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    source.setException(
                        createAuthInvalidUserException("verifyPassword", request, response)
                    )
                } else {
                    val body = response.body()!!.use { it.string() }
                    val user = FirebaseUserImpl(app, jsonParser.parseToJsonElement(body).jsonObject)
                    refreshToken(user, source) { AuthResult { it } }
                }
            }
        })
        return source.task
    }

    internal fun createAuthInvalidUserException(
        action: String,
        request: Request,
        response: Response,
    ): FirebaseAuthInvalidUserException {
        val body = response.body()!!.use { it.string() }
        val jsonObject = jsonParser.parseToJsonElement(body).jsonObject

        return FirebaseAuthInvalidUserException(
            jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive
                ?.contentOrNull
                ?: "UNKNOWN_ERROR",
            "$action API returned an error, " +
                "with url [${request.method()}] ${request.url()} ${request.body()} -- " +
                "response [${response.code()}] ${response.message()} $body"
        )
    }

    fun signOut() {
        synchronized(sessionLock) {
            sessionVersion.incrementAndGet()
            user = null
        }
    }

    override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
        val user = user ?: return Tasks.forException(FirebaseNoSignedInUserException("Please sign in before trying to get a token."))

        if (!forceRefresh && user.createdAt + user.expiresIn * 1000 - 5 * 60 * 1000 > System.currentTimeMillis()) {
//            Log.i("FirebaseAuth", "returning existing token for user ${user.uid} from getAccessToken")
            return Tasks.forResult(GetTokenResult(user.idToken, user.claims))
        }
//        Log.i("FirebaseAuth", "Refreshing access token forceRefresh=$forceRefresh createdAt=${user.createdAt} expiresIn=${user.expiresIn}")
        val source = TaskCompletionSource<GetTokenResult>()
        refreshToken(user, source) { GetTokenResult(it.idToken, it.claims) }
        return source.task
    }

    private var refreshSource = TaskCompletionSource<FirebaseUserImpl>().apply { setException(Exception()) }

    @Synchronized
    internal fun <T> refreshToken(user: FirebaseUserImpl, source: TaskCompletionSource<T>, map: (user: FirebaseUserImpl) -> T?) {
        refreshSource = refreshSource
            .takeUnless { it.task.isComplete }
            ?: enqueueRefreshTokenCall(user)
        refreshSource.task.addOnSuccessListener { source.setResult(map(it)) }
        refreshSource.task.addOnFailureListener { source.setException(it) }
    }

    private fun enqueueRefreshTokenCall(user: FirebaseUserImpl): TaskCompletionSource<FirebaseUserImpl> {
        val version = sessionVersion.get()
        val source = TaskCompletionSource<FirebaseUserImpl>()
        val body = RequestBody.create(
            json,
            JsonObject(
                mapOf(
                    "refresh_token" to JsonPrimitive(user.refreshToken),
                    "grant_type" to JsonPrimitive("refresh_token")
                )
            ).toString()
        )
        val request = Request.Builder()
            .url(authUrl("securetoken.googleapis.com/v1/token"))
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(e)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body()?.string().orEmpty()
                        if (!it.isSuccessful) {
                            throw FirebaseAuthInvalidUserException("TOKEN_REFRESH_FAILED", "Your session expired. Please sign in again.")
                        }
                        val refreshed = FirebaseUserImpl(app, jsonParser.parseToJsonElement(body).jsonObject, user.isAnonymous, user)
                        require(refreshed.claims["aud"] == app.options.projectId && refreshed.uid == user.uid)
                        synchronized(sessionLock) {
                            check(version == sessionVersion.get()) { "Sign-in was cancelled." }
                            this@FirebaseAuth.user = refreshed
                        }
                        source.setResult(refreshed)
                    } catch (e: Exception) {
                        synchronized(sessionLock) {
                            if (version == sessionVersion.get()) signOut()
                        }
                        source.setException(FirebaseAuthInvalidUserException("TOKEN_REFRESH_FAILED", "Your session expired. Please sign in again."))
                    }
                }
            }
        })
        return source
    }

    override fun getUid(): String? {
        return user?.uid
    }

    override fun addIdTokenListener(listener: com.google.firebase.auth.internal.IdTokenListener) {
        internalIdTokenListeners.addIfAbsent(listener)
        GlobalScope.launch(Dispatchers.Main) {
            listener.onIdTokenChanged(InternalTokenResult(user?.idToken))
        }
    }

    override fun removeIdTokenListener(listener: com.google.firebase.auth.internal.IdTokenListener) {
        internalIdTokenListeners.remove(listener)
    }

    @Synchronized
    fun addAuthStateListener(listener: AuthStateListener) {
        authStateListeners.addIfAbsent(listener)
        GlobalScope.launch(Dispatchers.Main) {
            listener.onAuthStateChanged(this@FirebaseAuth)
        }
    }

    @Synchronized
    fun removeAuthStateListener(listener: AuthStateListener) {
        authStateListeners.remove(listener)
    }

    @FunctionalInterface
    interface AuthStateListener {
        fun onAuthStateChanged(auth: FirebaseAuth)
    }

    @FunctionalInterface
    interface IdTokenListener {
        fun onIdTokenChanged(auth: FirebaseAuth)
    }

    fun addIdTokenListener(listener: IdTokenListener) {
        idTokenListeners.addIfAbsent(listener)
        GlobalScope.launch(Dispatchers.Main) {
            listener.onIdTokenChanged(this@FirebaseAuth)
        }
    }

    fun removeIdTokenListener(listener: IdTokenListener) {
        idTokenListeners.remove(listener)
    }

    fun sendPasswordResetEmail(email: String, settings: ActionCodeSettings?): Task<Unit> = TODO()
    fun createUserWithEmailAndPassword(email: String, password: String): Task<AuthResult> = TODO()
    fun signInWithCredential(authCredential: AuthCredential): Task<AuthResult> {
        val credential = authCredential as? GoogleAuthProvider.GoogleCredential
            ?: return Tasks.forException(IllegalArgumentException("This desktop build supports Google sign-in."))
        val version = synchronized(sessionLock) { sessionVersion.incrementAndGet() }
        val parameters = linkedMapOf("providerId" to "google.com")
        credential.idToken?.let { parameters["id_token"] = it }
        credential.accessToken?.let { parameters["access_token"] = it }
        val postBody = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8") }"
        }
        val body = JsonObject(mapOf(
            "postBody" to JsonPrimitive(postBody),
            "requestUri" to JsonPrimitive("http://localhost:8080/callback"),
            "returnSecureToken" to JsonPrimitive(true),
        ))
        val request = Request.Builder()
            .url(authUrl("identitytoolkit.googleapis.com/v1/accounts:signInWithIdp"))
            .post(RequestBody.create(json, body.toString()))
            .build()
        val source = TaskCompletionSource<AuthResult>()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val message = when (e) {
                    is javax.net.ssl.SSLHandshakeException ->
                        "A secure connection to Firebase could not be verified. Check your computer's certificate settings."
                    is java.net.SocketTimeoutException ->
                        "Firebase took too long to respond. Please try again."
                    else -> "Could not reach Firebase. Check your internet connection and try again."
                }
                source.setException(FirebaseException(message, e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val data = jsonParser.parseToJsonElement(it.body()?.string().orEmpty()).jsonObject
                        if (!it.isSuccessful || data["needConfirmation"]?.jsonPrimitive?.booleanOrNull == true) {
                            val code = data["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
                            val message = when {
                                code.startsWith("OPERATION_NOT_ALLOWED") -> "Enable Google sign-in in Firebase Authentication."
                                code.startsWith("EMAIL_EXISTS") || data["needConfirmation"]?.jsonPrimitive?.booleanOrNull == true ->
                                    "This email uses another sign-in method. Sign in with that method and link your Google account."
                                else -> "Firebase rejected Google sign-in. Check the Google provider configuration and try again."
                            }
                            throw FirebaseAuthInvalidCredentialsException("GOOGLE_SIGN_IN_FAILED", message)
                        }
                        val signedIn = FirebaseUserImpl(app, data)
                        require(signedIn.uid.isNotBlank() && signedIn.idToken.isNotBlank() && signedIn.refreshToken.isNotBlank())
                        require(signedIn.claims["aud"] == app.options.projectId)
                        synchronized(sessionLock) {
                            check(version == sessionVersion.get()) { "Sign-in was cancelled." }
                            user = signedIn
                        }
                        source.setResult(AuthResult { signedIn })
                    } catch (e: Exception) {
                        // Never propagate response bodies or credentials to the UI or logs.
                        source.setException(if (e is FirebaseAuthInvalidCredentialsException) e else
                            FirebaseException("Google sign-in could not finish. Please try again."))
                    }
                }
            }
        })
        return source.task
    }
    fun checkActionCode(code: String): Task<ActionCodeResult> = TODO()
    fun confirmPasswordReset(code: String, newPassword: String): Task<Unit> = TODO()
    fun fetchSignInMethodsForEmail(email: String): Task<SignInMethodQueryResult> = TODO()
    fun sendSignInLinkToEmail(email: String, actionCodeSettings: ActionCodeSettings): Task<Unit> = TODO()
    fun verifyPasswordResetCode(code: String): Task<String> = TODO()
    fun updateCurrentUser(user: FirebaseUser): Task<Unit> = TODO()
    fun applyActionCode(code: String): Task<Unit> = TODO()
    val languageCode: String get() = TODO()
    fun isSignInWithEmailLink(link: String): Boolean = TODO()
    fun signInWithEmailLink(email: String, link: String): Task<AuthResult> = TODO()

    fun setLanguageCode(value: String): Nothing = TODO()
    fun useEmulator(host: String, port: Int) {
        require(port in 1..65535)
        emulatorBaseUrl = "http://$host:$port/"
    }
}
