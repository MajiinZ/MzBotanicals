import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")


    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.ktor.android.client)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.firebase.common.ktx)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.messagebar.kmp)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.app)
            implementation(libs.firebase.common)


            implementation(libs.coil3)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.compose.core)
            implementation(libs.coil3.network.ktor)
            implementation(libs.coil3)

            implementation(project(path = ":shared"))
            implementation(project(path = ":data"))

        }
        commonTest.dependencies {
            //implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            //implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "org.mz.killrs.manage_product"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core)
    //implementation(libs.androidx.navigation.compose.jvmstubs)
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
