plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Resolve upstream fixtures relative to this integration project without changing the shared modules.
project(":core") {
    tasks.withType<Test>().configureEach {
        doFirst {
        systemProperty("gentv.deviceData", rootProject.file("framework/data").absolutePath)
        systemProperty("gentv.icons", rootProject.file("framework/assets/icons.json").absolutePath)
        }
    }
}
