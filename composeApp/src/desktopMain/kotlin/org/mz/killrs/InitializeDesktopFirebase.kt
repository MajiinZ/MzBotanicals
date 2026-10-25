package org.mz.killrs

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Initializes the Firebase JVM client without starting a sign-in flow. */
internal fun initializeDesktopFirebase() {
    DesktopFirebaseInitializer.initialize()
}

private object DesktopFirebaseInitializer {
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return

        // Use the same trusted root certificates as Windows (including roots
        // managed by the user's network). Keep explicit JVM overrides intact.
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
            System.getProperty("javax.net.ssl.trustStore") == null &&
            System.getProperty("javax.net.ssl.trustStoreType") == null
        ) {
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT")
            System.setProperty("javax.net.ssl.trustStore", "NONE")
        }

        val properties = Properties()
        val resource = checkNotNull(
            javaClass.getResourceAsStream("/firebase-desktop.properties")
        ) {
            "Desktop Firebase configuration is missing. Rebuild the desktop application " +
                "to generate firebase-desktop.properties from google-services.json."
        }
        resource.use(properties::load)

        val options = FirebaseOptions(
            applicationId = properties.requiredValue("applicationId"),
            apiKey = properties.requiredValue("apiKey"),
            projectId = properties.requiredValue("projectId"),
            storageBucket = properties.optionalValue("storageBucket"),
            gcmSenderId = properties.optionalValue("gcmSenderId"),
            databaseUrl = properties.optionalValue("databaseUrl"),
        )

        FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
            // Sessions live only for this process; App requests a fresh sign-in on launch.
            private val storage = ConcurrentHashMap<String, String>()

            override fun store(key: String, value: String) {
                storage[key] = value
            }

            override fun retrieve(key: String): String? = storage[key]

            override fun clear(key: String) {
                storage.remove(key)
            }

            override fun log(msg: String) {
                println(msg)
            }
        })
        Firebase.initialize(Application(), options)
        initialized = true
    }

    private fun Properties.optionalValue(key: String): String? =
        getProperty(key)?.trim()?.takeIf(String::isNotEmpty)

    private fun Properties.requiredValue(key: String): String =
        checkNotNull(optionalValue(key)) {
            "Desktop Firebase configuration is missing the '$key' property."
        }
}
