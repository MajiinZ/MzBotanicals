plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
            kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }
    }




    js(IR) {
        val cssModules = true
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
                cssSupport {
                    enabled.set(true)


                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.html.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.html.testUtils)
        }
    }
}
