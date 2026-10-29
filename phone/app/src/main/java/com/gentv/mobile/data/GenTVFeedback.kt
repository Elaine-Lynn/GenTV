package com.gentv.mobile.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** What kind of message the user is sending. [wire] is the value the GenTV endpoint expects. */
enum class FeedbackKind(val wire: String, val label: String) {
    BUG("bug", "Something went wrong"),
    PRAISE("praise", "It worked great"),
    FEATURE("feature", "An idea"),
    QUESTION("question", "A question"),
}

/** One message from the feedback sheet, before device details are added. */
data class FeedbackDraft(
    val kind: FeedbackKind,
    val message: String,
    val email: String = "",
    val page: String = "",
    /** The shared log text (already scrubbed of serials, Wi-Fi names and addresses), or null. */
    val log: String? = null,
    /** A JPEG, already resized. */
    val screenshot: ByteArray? = null,
)

/**
 * Sends feedback to the configured GenTV service.
 */
class GenTVFeedback(
    private val version: String,
    private val device: String,
    private val os: String,
    private val userId: String,
    private val http: Http = Http.URL_CONNECTION,
) {
    /** The HTTP call the endpoint needs, separate so the logic can be tested without a network. */
    interface Http {
        /** POSTs [json] without following redirects. Returns the status and a bounded JSON response body. */
        fun post(url: String, json: String): Pair<Int, String?>

        companion object {
            val URL_CONNECTION = object : Http {
                override fun post(url: String, json: String): Pair<Int, String?> {
                    val c = URL(url).openConnection() as HttpURLConnection
                    try {
                        c.requestMethod = "POST"
                        c.instanceFollowRedirects = false
                        c.connectTimeout = 15_000
                        c.readTimeout = 45_000
                        c.doOutput = true
                        c.setRequestProperty("Content-Type", "application/json")
                        c.outputStream.use { it.write(json.toByteArray()) }
                        val status = c.responseCode
                        val body = if (status in 200..299) c.inputStream.bufferedReader().use { reader ->
                            val buffer = CharArray(8193)
                            var count = 0
                            while (count < buffer.size) { val n = reader.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                            if (count > 8192) null else String(buffer, 0, count)
                        } else null
                        return status to body
                    } finally {
                        c.disconnect()
                    }
                }


            }
        }
    }

    fun payload(d: FeedbackDraft): JSONObject = JSONObject().apply {
        put("app", APP_NAME)
        put("type", d.kind.wire)
        put("feedback", buildString {
            append(d.message.trim())
            // The log goes in the message: the endpoint has no field for it, and it is what
            // turns "it didn't work on my TV" into a test fixture.
            d.log?.takeIf { it.isNotBlank() }?.let { append("\n\n--- GenTV log ---\n").append(it.takeLast(MAX_LOG)) }
        })
        put("page", d.page)
        put("platform", "android")
        put("version", version)
        put("device", device)
        put("os", os)
        put("email", d.email.trim())
        put("user_id", userId)
        put("source", "in-app")
        d.screenshot?.let { put("screenshot_base64", Base64.encodeToString(it, Base64.NO_WRAP)) }
    }

    /** True once the message is saved. Never throws; the caller keeps the user's text on false. */
    suspend fun send(d: FeedbackDraft): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (status, response) = http.post(ENDPOINT, payload(d).toString())
            status in setOf(200, 201, 202) && response != null && JSONObject(response).opt("success") == true
        }.getOrDefault(false)
    }

    companion object {
        const val ENDPOINT = com.gentv.mobile.BuildConfig.FEEDBACK_API // Configure the GenTV feedback service before release.

        /** GenTV application name included in feedback requests. */
        const val APP_NAME = "GenTV"
        private const val MAX_LOG = 20_000

        /** Decodes a picked image and re-encodes it as a JPEG of at most 1280 px, quality 70. */
        fun shrinkScreenshot(bytes: ByteArray): ByteArray? = runCatching {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scale = minOf(1f, 1280f / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        }.getOrNull()
    }
}
