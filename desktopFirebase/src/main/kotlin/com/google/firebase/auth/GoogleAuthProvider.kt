package com.google.firebase.auth

object GoogleAuthProvider {
    @JvmStatic
    fun getCredential(idToken: String?, accessToken: String?): AuthCredential {
        require(!idToken.isNullOrBlank() || !accessToken.isNullOrBlank()) {
            "Google sign-in did not return a token. Please try again."
        }
        return GoogleCredential(idToken?.takeIf { it.isNotBlank() }, accessToken?.takeIf { it.isNotBlank() })
    }

    // Deliberately not a data class: toString must never expose credentials.
    internal class GoogleCredential(val idToken: String?, val accessToken: String?) : AuthCredential() {
        override fun getProvider(): String = "google.com"
    }
}
