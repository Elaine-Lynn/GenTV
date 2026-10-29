package com.gentv.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data object Unconfigured : UpdateStatus
    data object Failed : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
}

/** A newer GenTV release from the configured service. */
@Serializable
data class UpdateInfo(val version: String, val url: String, val versionCode: Long = 0,
    val sha256: String = "", val sizeBytes: Long = 0, val minSdk: Int = 26) {
    val installable get() = versionCode > 0 && sha256.matches(Regex("[a-fA-F0-9]{64}")) && sizeBytes in 1..UpdateDownload.MAX_BYTES && UpdateDownload.allowedUrl(url)
}

/**
 * Only for copies not installed from Google Play (see [needed]): Play updates its own installs, but a
 * sideloaded APK hears about nothing. Once a day this asks the configured service
 * for the latest release (a plain public request; nothing about the user or the TV is sent) and
 * says so when it is newer than this build. Offline or any error means silence, never a crash.
 */
class UpdateCheck(
    private val file: File,
    private val current: String,
    private val fetch: (String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private data class State(val checkedAt: Long = 0, val latest: UpdateInfo? = null, val dismissed: String? = null)

    /** The release to offer, or null. Reads the cache when it is less than a day old. */
    fun check(): UpdateInfo? {
        val cached = read()
        val state = if (cached != null && now() - cached.checkedAt < DAY_MS) cached else {
            val body = runCatching { fetch(LATEST_URL) }.getOrNull()
            // Only a real answer counts as "checked today"; offline tries again next time.
            val latest = body?.let(::parse) ?: return offer(cached)
            State(now(), latest, cached?.dismissed).also(::write)
        }
        return offer(state)
    }

    /** An explicit check bypasses the daily cache and previously dismissed releases. */
    fun checkNow(): UpdateStatus {
        val latest = runCatching { fetch(LATEST_URL)?.let(::parse) }.getOrNull() ?: return UpdateStatus.Failed
        write(State(now(), latest, read()?.dismissed))
        return if (newer(latest)) UpdateStatus.Available(latest) else UpdateStatus.Current
    }

    /** "Not now" for this version: it stays quiet until a newer one appears. */
    fun dismiss(version: String) {
        write((read() ?: State()).copy(dismissed = version))
    }

    private fun offer(s: State?): UpdateInfo? =
        s?.latest?.takeIf { newer(it) && it.version != s.dismissed }

    private fun newer(info: UpdateInfo) = if (info.versionCode > 0) info.versionCode > com.gentv.mobile.BuildConfig.VERSION_CODE else isNewer(info.version, current)

    private fun read(): State? = runCatching { json.decodeFromString(State.serializer(), file.readText()) }.getOrNull()

    private fun write(s: State) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(State.serializer(), s))
            tmp.renameTo(file)
        }
    }

    companion object {
        const val LATEST_URL = com.gentv.mobile.BuildConfig.UPDATE_API
        /** Where "Get it" on the banner goes: the configured download page. */
        const val SITE_URL = com.gentv.mobile.BuildConfig.DOWNLOAD_URL
        const val PLAY_STORE = "com.android.vending"

        /** Play installs update through Play, so only sideloaded copies (any other installer) check the configured service. */
        fun needed(installer: String?): Boolean = installer != PLAY_STORE
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val json = Json { ignoreUnknownKeys = true }

        /** GitHub's latest-release JSON → version and page. Drafts and pre-releases don't count. */
        fun parse(body: String): UpdateInfo? = runCatching {
            val o = Json.parseToJsonElement(body).jsonObject
            if (o["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
            if (o["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
            if (o.containsKey("version_code")) {
                val version = o["version"]?.jsonPrimitive?.contentOrNull ?: return null
                val code = o["version_code"]?.jsonPrimitive?.longOrNull ?: return null
                val url = o["apk_url"]?.jsonPrimitive?.contentOrNull ?: return null
                val hash = o["sha256"]?.jsonPrimitive?.contentOrNull ?: return null
                val size = o["size_bytes"]?.jsonPrimitive?.longOrNull ?: return null
                val sdk = o["min_sdk"]?.jsonPrimitive?.intOrNull ?: return null
                if (parts(version) == null || sdk < 26) return null
                return UpdateInfo(version, url, code, hash, size, sdk).takeIf { it.installable }
            }
            val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val version = tag.trim().removePrefix("v").removePrefix("V")
            if (parts(version) == null) return null
            UpdateInfo(version, o["html_url"]?.jsonPrimitive?.contentOrNull ?: SITE_URL)
        }.getOrNull()

        /** Numeric major.minor.patch compare; "0.10.0" is newer than "0.9.1". A suffix like "-debug" is ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = parts(candidate) ?: return false
            val b = parts(current.substringBefore('-').removePrefix("v")) ?: return true
            for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }

        private fun parts(v: String): List<Int>? {
            val nums = v.split('.').map { it.toIntOrNull() ?: return null }
            if (nums.isEmpty() || nums.size > 3 || nums.any { it < 0 }) return null
            return nums + List(3 - nums.size) { 0 }
        }
    }
}
