rootProject.name = "intellij-ai-chat-exporter"

pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        gradlePluginPortal()
        mavenCentral()
    }
}

// LERNHINWEIS: dependencyResolutionManagement hier NICHT verwenden –
// das IntelliJ Platform Gradle Plugin 2.x verwaltet Repositories selbst
// über intellijPlatform { defaultRepositories() } in build.gradle.kts.
