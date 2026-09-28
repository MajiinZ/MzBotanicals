plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// GitLive's JVM compatibility SDK leaves Google sign-in unimplemented.
// Substitute our narrowly patched desktop jar; Android uses Google's own SDK.
subprojects {
    if (name != "desktopFirebase") {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("dev.gitlive:firebase-java-sdk"))
                    .using(project(":desktopFirebase"))
            }
        }
    }
}
