// The Android Gradle Plugin is put on the classpath here (not in the plugins block)
// so that `-PcoreOnly=true` can build and test the pure-Kotlin :core module on
// machines without an Android SDK or access to Google's Maven repository.
buildscript {
    val coreOnly = gradle.startParameter.projectProperties["coreOnly"] == "true"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        if (!coreOnly) classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    }
}

plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
