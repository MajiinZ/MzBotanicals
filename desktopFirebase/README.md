# Desktop Firebase compatibility patch

This module replaces `dev.gitlive:firebase-java-sdk:0.4.5` on JVM targets.
Android still uses the official Android Firebase SDK. The original, pinned
Maven artifact supplies the unchanged classes; the jar task removes the
replaced classes before adding the sources in this module. No reflection or
classpath-order override is used in the packaged app.

`FirebaseAuth.kt` and `FirebaseUser.kt` originate from the upstream 0.4.5
sources, licensed under Apache 2.0 by the GitLive contributors:
https://github.com/GitLiveApp/firebase-java-sdk

Local changes implement Google credentials through Firebase's documented
`accounts:signInWithIdp` REST endpoint, expose the returned profile fields,
preserve them on refresh, and prevent a delayed refresh from signing a user
back in after sign-out. Firebase validates the Google credential and returns
the Firebase UID and tokens used by the existing Firestore/Storage clients.
The desktop app contains public Firebase client configuration only.

On Windows the app initializes Java TLS with `Windows-ROOT` and packages the
`jdk.crypto.mscapi` module, so HTTPS uses the OS-managed trusted roots. Explicit
JVM trust-store settings are preserved. Certificate and hostname validation
remain enabled; no certificates or machine-specific secrets are bundled.

Google OAuth Web client must authorize `http://localhost:8080/callback`,
the callback used by the current KMPAuth 2.3.1 browser flow.

Validation: `./gradlew :desktopFirebase:test :composeApp:desktopTest :composeApp:createDistributable`.
The tests use a local mock HTTP server and synthetic tokens, not real accounts.
The app-level test checks that Firebase callbacks can reach Swing's UI thread
using the application's own runtime dependencies.
Keep the upstream version pinned until the patch is reviewed against any
replacement SDK. Other upstream methods marked TODO remain unsupported.
