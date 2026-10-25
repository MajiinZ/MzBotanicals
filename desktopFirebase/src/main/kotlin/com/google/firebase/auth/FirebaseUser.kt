// Derived from GitLive firebase-java-sdk 0.4.5 (Apache-2.0). Modified for desktop Google sign-in.
package com.google.firebase.auth

import com.google.android.gms.tasks.Task

abstract class FirebaseUser {
    abstract val uid: String
    abstract val isAnonymous: Boolean
    abstract fun delete(): Task<Void>
    abstract fun reload(): Task<Void>

    abstract val email: String?
    abstract val displayName: String?
    val phoneNumber: String get() = TODO()
    abstract val photoUrl: String?
    abstract val isEmailVerified: Boolean
    val metadata: FirebaseUserMetadata get() = TODO()
    val multiFactor: MultiFactor get() = TODO()
    val providerData: List<UserInfo> get() = TODO()
    val providerId: String get() = TODO()
    abstract fun getIdToken(forceRefresh: Boolean): Task<GetTokenResult>
    fun linkWithCredential(credential: AuthCredential): Task<AuthResult> = TODO()
    fun sendEmailVerification(): Task<Unit> = TODO()
    fun sendEmailVerification(actionCodeSettings: ActionCodeSettings): Task<Unit> = TODO()
    fun unlink(provider: String): Task<AuthResult> = TODO()
    fun updateEmail(email: String): Task<Unit> = TODO()
    fun updatePassword(password: String): Task<Unit> = TODO()
    fun updatePhoneNumber(credential: AuthCredential): Task<Unit> = TODO()
    fun updateProfile(request: UserProfileChangeRequest): Task<Unit> = TODO()
    fun verifyBeforeUpdateEmail(newEmail: String, actionCodeSettings: ActionCodeSettings?): Task<Unit> = TODO()
    fun reauthenticate(credential: AuthCredential): Task<Unit> = TODO()
    fun reauthenticateAndRetrieveData(credential: AuthCredential): Task<AuthResult> = TODO()
}
