rootProject.name = "KillrsGenetics"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
include(":data")
include(":di")
include(":feature:admin")
include(":feature:admin:manage_product")
include(":feature:details")
include(":feature:home")
include(":feature:home:cart")
include(":feature:home:categories")
include(":feature:home:categories:category_search")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:checkout")
include(":feature:home:payment_completed")
include(":feature:home:products_overview")
include(":feature:profile")
include(":feature:auth")
include(":navigation")
include(":composeApp")
include(":shared")
includeBuild("server")
include(":shared")
include(":webApp")
