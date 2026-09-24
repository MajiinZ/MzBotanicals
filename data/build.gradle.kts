import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")


    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.auth.firebase.kmp)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization)

            implementation(project(path = ":shared"))


        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation("io.ktor:ktor-client-cio:3.0.3")
            implementation(compose.desktop.currentOs)
            //implementation(libs.kotlinx.coroutinesSwing)
        }
        androidMain.dependencies { implementation(libs.ktor.android.client) }
    }
}

val backendUrl = providers.gradleProperty("mzBackendUrl").orElse(providers.environmentVariable("MZ_BACKEND_URL")).orElse("")
val generatedBackendConfig = layout.buildDirectory.dir("generated/backendConfig")
val generateBackendConfig by tasks.registering {
    inputs.property("backendUrl", backendUrl)
    outputs.dir(generatedBackendConfig)
    doLast {
        val value = backendUrl.get().trimEnd('/')
        if (value.isNotEmpty()) {
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()) { "MZ_BACKEND_URL must be an HTTPS origin" }
        }
        val output = generatedBackendConfig.get().file("org/mz/data/BackendConfiguration.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("package org.mz.data\ninternal object BackendConfiguration { const val BASE_URL = \"$value\" }\n")
    }
}
kotlin.sourceSets.named("commonMain") { kotlin.srcDir(generatedBackendConfig) }
tasks.matching { it.name.startsWith("compile") }.configureEach { dependsOn(generateBackendConfig) }

android {
    namespace = "org.mz.killrs.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.testng)
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.mz.killrs.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.mz.killrs"
            packageVersion = "1.0.0"
        }
    }
}
