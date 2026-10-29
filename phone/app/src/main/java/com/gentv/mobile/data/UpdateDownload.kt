package com.gentv.mobile.data

import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Bounded streaming download. Never publishes a partial or unverified APK. */
object UpdateDownload {
    const val MAX_BYTES = 200L * 1024 * 1024
    /** The configured GenTV API base; empty in builds without one, which never download updates. Tests may replace it. */
    @Volatile var apiBase: String = com.gentv.mobile.BuildConfig.API_BASE_URL

    fun allowedUrl(url: String): Boolean = apiBase.isNotEmpty() && runCatching {
        val base = URI(apiBase)
        val uri = URI(url)
        uri.scheme == "https" && uri.host == base.host && uri.port == base.port &&
            uri.rawPath.startsWith(base.rawPath) && uri.userInfo == null && uri.fragment == null &&
            uri.normalize().rawPath == uri.rawPath && !uri.rawPath.contains('%')
    }.getOrDefault(false)

    fun verify(file: File, info: UpdateInfo) {
        require(info.installable && file.length() == info.sizeBytes) { "Invalid APK size or metadata" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) }.equals(info.sha256, ignoreCase = true)) { "APK checksum mismatch" }
    }

    fun copy(input: InputStream, file: File, info: UpdateInfo, progress: (Long) -> Unit, active: () -> Unit) {
        require(info.installable)
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    active()
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= info.sizeBytes && total <= MAX_BYTES) { "APK exceeds expected size" }
                    output.write(buffer, 0, n)
                    progress(total)
                }
            }
            active(); verify(file, info)
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun download(info: UpdateInfo, file: File, progress: (Long) -> Unit, active: () -> Unit) {
        require(info.installable)
        val connection = URL(info.url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            require(connection.responseCode == 200) { "Download failed" }
            val length = connection.contentLengthLong
            require(length == -1L || length == info.sizeBytes) { "Unexpected response length" }
            connection.inputStream.use { copy(it, file, info, progress, active) }
        } finally { connection.disconnect() }
    }
}
