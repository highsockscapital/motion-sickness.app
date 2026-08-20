// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Target JVM 17 for Kotlin and Android Gradle plugins
plugins {
    // Android Gradle Plugin - required for Android projects
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    // Kotlin Gradle Plugin (JVM target 17 configured in app module via jvmToolchain(17) and kotlinOptions)
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin Compose Compiler Plugin (enables Compose compiler features)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
