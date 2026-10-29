pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.Flyfish233") } }
    }
    versionCatalogs { create("libs") { from(files("framework/gradle/libs.versions.toml")) } }
}
rootProject.name = "GenTV"
include(":app", ":core", ":adb")
// Original modules, without copying or rewriting protection, parsing or ADB code.
project(":core").projectDir = file("framework/core")
project(":adb").projectDir = file("framework/adb")
