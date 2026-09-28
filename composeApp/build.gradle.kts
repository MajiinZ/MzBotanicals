import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker.Companion.android
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties
import groovy.json.JsonSlurper
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.jvm.tasks.ProcessResources

val releaseSigningPropertiesFile =
    File(System.getProperty("user.home"), ".android/killrsgenetics-upload.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigningProperties = releaseSigningPropertiesFile.isFile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.services)

}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    // The browser entry point is provided by :webApp.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)

               // implementation(libs.splash.screen)



                implementation(libs.auth.kmp)
                implementation(libs.firebase.app)

                implementation(libs.koin.compose)

                implementation(project(path = ":navigation"))
                implementation(project(path = ":shared"))
                implementation(project(path = ":di"))
                implementation(project(path = ":data"))


            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.splash.screen)
                implementation(libs.koin.android)
                implementation(libs.firebase.common.ktx)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.coroutines.get()}")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }

    }
}

android {
    namespace = "org.mz.killrs"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        if (hasReleaseSigningProperties) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "org.mz.killrs"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigningProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":navigation"))
    implementation(project(":data"))
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.mz.killrs.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            // Firebase, gRPC, and SQLite load JDK classes indirectly. Bundle the
            // full runtime so native installers do not omit those dependencies.
            includeAllModules = true
            packageName = "org.mz.killrs"
            packageVersion = "1.0.1"
        }
    }
}

abstract class GenerateDesktopFirebaseConfig : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceConfig: RegularFileProperty

    @get:Input
    abstract val androidPackageName: Property<String>

    @get:OutputFile
    abstract val outputConfig: RegularFileProperty

    @TaskAction
    fun generate() {
        val config = JsonSlurper().parse(sourceConfig.get().asFile) as Map<*, *>
        val firebaseProject = config["project_info"] as Map<*, *>
        val client = (config["client"] as List<*>).map { it as Map<*, *> }.single {
            val info = it["client_info"] as Map<*, *>
            val androidInfo = info["android_client_info"] as Map<*, *>
            androidInfo["package_name"] == androidPackageName.get()
        }
        val clientInfo = client["client_info"] as Map<*, *>
        val apiKey = (client["api_key"] as List<*>).first() as Map<*, *>
        val values = Properties().apply {
            setProperty("applicationId", clientInfo["mobilesdk_app_id"] as String)
            setProperty("apiKey", apiKey["current_key"] as String)
            setProperty("projectId", firebaseProject["project_id"] as String)
            firebaseProject["project_number"]?.let { setProperty("gcmSenderId", it.toString()) }
            firebaseProject["storage_bucket"]?.let { setProperty("storageBucket", it.toString()) }
            firebaseProject["firebase_url"]?.let { setProperty("databaseUrl", it.toString()) }
        }
        val output = outputConfig.get().asFile
        output.parentFile.mkdirs()
        output.outputStream().use { values.store(it, "Firebase public client configuration") }
    }
}

val generateDesktopFirebaseConfig = tasks.register<GenerateDesktopFirebaseConfig>("generateDesktopFirebaseConfig") {
    sourceConfig.set(layout.projectDirectory.file("google-services.json"))
    androidPackageName.set("org.mz.killrs")
    outputConfig.set(layout.buildDirectory.file("generated/desktopFirebaseConfig/firebase-desktop.properties"))
}

tasks.named<ProcessResources>("desktopProcessResources") {
    from(generateDesktopFirebaseConfig.flatMap { it.outputConfig })
}

tasks.matching {
    it.name == "jsBrowserTest" || it.name == "jsTest" || it.name == "jsNodeTest"
}.configureEach {
    enabled = false
}
