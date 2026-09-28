import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
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




        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization)
            implementation(libs.compose.navigation)

            implementation(libs.auth.firebase.kmp)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(project(path = ":feature:auth"))
            implementation(project(path = ":shared"))
            implementation(project(path = ":feature:home"))
            implementation(project(path = ":feature:profile"))
            implementation(project(path = ":feature:admin"))
            implementation(project(path = ":feature:admin:manage_product"))
            implementation(project(path = ":feature:details"))
            implementation(project(path = ":feature:home:cart"))
            implementation(project(path = ":feature:home:categories"))
            implementation(project(path= ":feature:home:categories:category_search"))
            implementation(project(path= ":feature:home:checkout"))
           // implementation(project(path= ":feature:home:checkout:payment_completed"))
            implementation(project(path= ":feature:home:payment_completed"))

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
    namespace = "org.mz.killrs.navigation"
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
    implementation(project(":shared"))
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
