package org.mz.killrs

import com.google.firebase.auth.FirebaseAuth

fun main(args: Array<String>) {
    require(args.size == 2 && args[1] in setOf("grant", "remove")) { "Usage: adminClaims --args='<Firebase UID> grant|remove'" }
    require(args[0].isNotBlank() && args[0].length <= 128) { "Invalid Firebase UID" }
    val app = initializeFirebase(ServerConfig.fromEnvironment())
    try {
        val auth = FirebaseAuth.getInstance(app)
        val user = auth.getUser(args[0])
        require(user.isEmailVerified && !user.isDisabled) { "Admin account must be enabled and email verified" }
        val claims = user.customClaims.toMutableMap()
        if (args[1] == "grant") claims["admin"] = true else claims.remove("admin")
        auth.setCustomUserClaims(user.uid, claims)
        auth.revokeRefreshTokens(user.uid)
        println("Admin claim updated; the account must sign in again.")
    } finally { app.delete() }
}
