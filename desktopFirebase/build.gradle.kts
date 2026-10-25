plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    `java-library`
}

group = "org.mz"
version = "0.4.5-mz1"

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val upstreamSdk by configurations.creating {
    isTransitive = false
}

// These are replacement sources from the same SDK. Keep access to its internal
// platform storage API without copying or changing the platform implementation.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.add(upstreamSdk.elements.map { files ->
        "-Xfriend-paths=${files.single().asFile.absolutePath}"
    })
}

dependencies {
    upstreamSdk("dev.gitlive:firebase-java-sdk:0.4.5")
    compileOnly(files(upstreamSdk))
    // Runtime dependencies of the pinned upstream jar. It already includes the
    // Firebase Android bytecode and JVM shims; do not ship a second copy of it.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization)
    api("org.xerial:sqlite-jdbc:3.44.1.0")
    api("javax.inject:javax.inject:1")
    api("com.squareup.okhttp3:okhttp:3.12.13")
    api("io.grpc:grpc-protobuf-lite:1.52.1")
    api("io.grpc:grpc-stub:1.52.1")
    api("io.grpc:grpc-okhttp:1.52.1")
    api("androidx.collection:collection:1.2.0")
    api("androidx.lifecycle:lifecycle-common-jvm:2.8.0-rc01")
    api("androidx.lifecycle:lifecycle-viewmodel-desktop:2.8.0-rc01")

    testImplementation(libs.kotlin.test.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.12.13")
    testImplementation("dev.gitlive:firebase-auth-jvm:2.1.0") {
        exclude(group = "dev.gitlive", module = "firebase-java-sdk")
    }
    testCompileOnly(files(upstreamSdk))
    testRuntimeOnly(files(tasks.jar))
}

tasks.jar {
    from(upstreamSdk.map { zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("com/google/firebase/auth/FirebaseAuth.class")
        exclude("com/google/firebase/auth/FirebaseAuth\$*.class")
        exclude("com/google/firebase/auth/FirebaseAuthKt.class")
        exclude("com/google/firebase/auth/FirebaseUser.class")
        exclude("com/google/firebase/auth/FirebaseUserImpl*.class")
        exclude("com/google/firebase/auth/GoogleAuthProvider*.class")
    }
    from("LICENSE") { into("META-INF/desktop-firebase") }
}
