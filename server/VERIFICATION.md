# Verification — September 23, 2026

Implemented locally on `codex/secure-backend`, based on `fe7dd0b0fbc969bfafa3333881d333bed285ec2f` from `master`.

## Passing checks

| Check | Result |
| --- | --- |
| Ktor API security tests | 15 passed |
| Configuration and pricing validation tests | 4 passed |
| Firebase Authentication emulator integration tests | 4 passed |
| Firestore emulator integration tests | 8 passed |
| Firestore and Storage security-rule tests | 8 passed |
| Total security tests | **39 passed, zero failures, zero skipped in the emulator run** |
| Standalone server compilation and distribution | Passed |
| Full desktop Kotlin compilation | Passed |
| Full Android debug Kotlin compilation | Passed |
| Git diff whitespace check | Passed |

The tests exercise rejected/disabled/unverified accounts, immediate API admin-claim removal, cross-customer draft isolation, blocked direct database writes, role and price tampering, bad carts, strict JSON, body size limits, CORS, throttling, legacy price conversion, concurrent idempotent drafts, stale catalog revisions, draft expiry, atomic cart changes and profile/cart preservation. Storage checks cover upload roles, file-size limits, MIME metadata and denied paths.

Reproduce the security checks with `npm ci --ignore-scripts && npm test` inside `security-tests/`. The runner starts only local Firebase emulators under the demo project and executes the JVM suite against them. Without emulator environment variables, the regular server test command runs unit tests and explicitly skips integration tests.

App compilation command from the repository root:

```sh
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid --no-configuration-cache
```

Server distribution command:

```sh
./gradlew -p server test installDist
```

Three unused mobile-only dependencies and the missing desktop dependency-injection declaration were corrected to allow full desktop compilation. Existing deprecation warnings remain.

## Limits of verification

- No live Firebase project, rules, account privileges, payment provider, or hosting environment was modified.
- The app's screens and sign-in were not exercised on a physical device or in a live desktop session. Compilation does not establish runtime UI correctness.
- Docker is unavailable on this machine; the Dockerfile has not been built here.
- CI configuration is included but has not run on GitHub; the branch has not been pushed.
- No payment processing, tax/shipping calculation, stock reservation, email delivery or fulfillment is implemented. The app and API reject checkout.
- Deployment requires the Firebase project ID, host identity/IAM configuration, HTTPS ingress, deployed rules and the app's `MZ_BACKEND_URL`. See [README.md](README.md) for the coordinated rollout.

The passing tests validate the implemented controls; they are not a production security certification or independent penetration test.
