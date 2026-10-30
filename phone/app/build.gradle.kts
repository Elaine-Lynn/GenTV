import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from keys/signing.properties, a git-ignored folder in the repo.
val signing = rootProject.file("keys/signing.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}
// Updates, feedback and statistics stay off unless a GenTV API base is configured:
// -PgentvApiBaseUrl=..., GENTV_API_BASE_URL, or apiBaseUrl in the private keys/signing.properties.
val apiBaseUrl = (providers.gradleProperty("gentvApiBaseUrl").orNull ?: System.getenv("GENTV_API_BASE_URL")
    ?: signing?.getProperty("apiBaseUrl") ?: "").trim()
require(apiBaseUrl.isEmpty() || Regex("https://[A-Za-z0-9.-]+(:[0-9]+)?/([A-Za-z0-9._~-]+/)*").matches(apiBaseUrl)) {
    "gentvApiBaseUrl must be an https URL ending in /"
}

android {
    namespace = "com.gentv.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gentv.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "3.7.1"
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun api(path: String) = if (apiBaseUrl.isEmpty()) "" else apiBaseUrl + path
        buildConfigField("String", "API_BASE_URL", quoted(apiBaseUrl))
        buildConfigField("String", "ANALYTICS_API", quoted(api("events")))
        buildConfigField("String", "UPDATE_API", quoted(api("update?platform=mobile")))
        buildConfigField("String", "DOWNLOAD_URL", quoted("https://www.gentv.me"))
        buildConfigField("String", "FEEDBACK_API", quoted(api("feedback")))
    }

    // Without a release key, no debug signing fallback is used.
    signingConfigs {
        if (signing != null) create("release") {
            storeFile = rootProject.file("keys/" + signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            // Phones are ARM; x86 native libraries (Conscrypt's are ~3 MB each) only serve emulators.
            // Debug builds keep every ABI so any emulator still runs them.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["main"].assets.setSrcDirs(listOf("../framework/assets", "build/generated/lists", "build/generated/homes"))
    sourceSets["main"].res.srcDir("../framework/resources")
}

// Snapshot of the licensed device lists, bundled so the app works offline.
val copyLists by tasks.registering(Copy::class) {
    from(rootProject.file("framework/data/devices")) {
        include("*/debloat.txt")
        eachFile { path = "lists/" + file.parentFile.name + ".txt" }
        includeEmptyDirs = false
    }
    from(rootProject.file("framework/data/source-profiles.json"))
    into(layout.buildDirectory.dir("generated/lists"))
}
// Licensed themes for existing reference-desktop configuration records: config, wallpapers and a preview.
val copyThemes by tasks.registering(Copy::class) {
    from(rootProject.file("framework/data/themes")) {
        include("default/**", "aurora/**", "glass/**", "scenes/**")
        exclude("**/*.md")
    }
    into(layout.buildDirectory.dir("generated/lists/themes"))
}
tasks.named("preBuild") { dependsOn(copyLists, copyThemes) }

dependencies {
    implementation(project(":adb"))
    implementation(project(":core"))
    // Kadb's Wireless debugging pairing needs TLS 1.3 keying material that the platform Conscrypt hides.
    // Bundling Conscrypt makes Kadb use its own copy, so the hidden-API bypass it also ships is never needed.
    implementation(libs.conscrypt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Kadb pulls in a hidden-API bypass for the platform Conscrypt; Play flags it and it is unused with the bundled one.
configurations.configureEach { exclude(group = "org.lsposed.hiddenapibypass") }

// The Android Conscrypt jar has no JNI for the desktop JVM and would shadow Robolectric's own copy,
// so unit tests run without it (the app itself is unchanged).
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.conscrypt", module = "conscrypt-android")
}

// Screenshot and view model tests run once, on the debug variant.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }

// All bundled APKs are generated from pinned source; none are stored in this repository.
val buildHomeAssets by tasks.registering(Exec::class) {
    val repoRoot = rootProject.projectDir.parentFile
    inputs.files(fileTree(rootProject.file("../launcher")) {
        exclude("**/build/**", "**/.gradle/**", "**/local.properties")
    }, rootProject.file("../tools/build_phone_assets.py"), rootProject.file("framework/data/source-profiles.json"))
    inputs.property("apiBaseUrl", apiBaseUrl)
    inputs.property("debugSigning", signing == null)
    outputs.dir(layout.buildDirectory.dir("generated/homes"))
    val args = mutableListOf("python3", repoRoot.resolve("tools/build_phone_assets.py").absolutePath,
        "--output", layout.buildDirectory.dir("generated/homes").get().asFile.absolutePath,
        "--home-java", providers.gradleProperty("homeJavaHome").orElse(providers.environmentVariable("GENTV_HOME_JAVA_HOME")).orElse(System.getProperty("java.home")).get())
    if (gradle.startParameter.isOffline) args += "--offline"
    // Without the private release key, contributors get a debug-signed TV preview for local testing only.
    if (signing == null) args += "--debug-signing"
    commandLine(args)
    environment("JAVA_HOME", System.getProperty("java.home"))
    environment("GENTV_API_BASE_URL", apiBaseUrl)
}
tasks.named("preBuild") { dependsOn(buildHomeAssets) }
tasks.withType<Test>().configureEach {
    systemProperty("gentv.upstream", rootProject.file("framework/data").absolutePath)
}
// Upstream screenshot goldens describe its old branding; verify the redesigned UI on the emulator.
tasks.withType<Test>().configureEach {
    filter { excludeTestsMatching("com.gentv.mobile.ScreenshotTest") }
}

// Explicit opt-in only; the test itself refuses any non-emulator target.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("emulatorSerial").orNull?.let { systemProperty("gentv.emulatorSerial", it) }
}

// A distributable release must never be produced without explicit release credentials.
val requireReleaseSigning by tasks.registering {
    doLast {
        val release = android.signingConfigs.findByName("release")
        check(release != null && release.storeFile?.isFile == true && !release.storePassword.isNullOrBlank()
            && !release.keyAlias.isNullOrBlank() && !release.keyPassword.isNullOrBlank()) {
            "Release signing is required. Configure the private keys/signing.properties and keystore before packaging."
        }
    }
}
tasks.matching { it.name in setOf("packageRelease", "signReleaseBundle", "assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(requireReleaseSigning)
}

val prepareLicenseAssets by tasks.registering(Sync::class) {
    from(rootProject.file("../THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("../LICENSE"))
    from(rootProject.file("../licenses"))
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
android.sourceSets["main"].assets.srcDir("build/generated/licenseAssets")
tasks.named("preBuild") { dependsOn(prepareLicenseAssets) }
