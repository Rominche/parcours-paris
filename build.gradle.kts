// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}

// Fix Room + kotlinx.serialization : Room schema export requiert kotlinx.serialization >= 1.8.0
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion("1.8.0")
                because("Room schema export requires kotlinx.serialization >= 1.8.0 (AbstractMethodError fix)")
            }
        }
    }
}
