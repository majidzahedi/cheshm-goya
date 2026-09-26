pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CheshmGoya"

include(":core")
include(":ai-claude")
// The Android module needs an Android SDK (ANDROID_HOME or local.properties).
// Pass -PcoreOnly=true to build/test only the pure-Kotlin logic without an SDK.
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":android")
}
