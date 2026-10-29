package com.gentv.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Optional, best-effort mobile metrics. No persistent user or device identifier. */
class Analytics(
    private val scope: CoroutineScope,
    private val version: String,
    private val sdk: Int,
    private val post: (String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Event { APP_OPEN, SCREEN_VIEW, CONNECTION_RESULT, TIDY_COMPLETE, FILE_SEND_RESULT, UPDATE_CHECK, FEEDBACK_SUBMIT }
    private data class Entry(val id: String, val at: Long, val event: Event, val value: String)
    private val pending = ArrayDeque<Entry>()
    private val sending = Mutex()
    private var drainJob: kotlinx.coroutines.Job? = null
    private var scheduled = false
    private var enabled = false
    private var session = UUID.randomUUID().toString()

    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        drainJob?.cancel()
        scheduled = false
        pending.clear()
        session = UUID.randomUUID().toString()
    }

    /** Values come exclusively from the allowlist, never user input or TV data. */
    @Synchronized fun record(event: Event, value: String = "") {
        if (!enabled || !allowed(event, value)) return
        while (pending.size >= 100) pending.removeFirst()
        pending.addLast(Entry(UUID.randomUUID().toString(), now(), event, value))
        scheduleLocked()
    }

    private fun scheduleLocked() {
        if (scheduled) return
        scheduled = true
        val currentSession = session
        drainJob = scope.launch {
            var succeeded = false
            try {
                delay(2_000)
                do {
                    succeeded = flush()
                } while (succeeded && synchronized(this@Analytics) { enabled && session == currentSession && pending.isNotEmpty() })
            } finally {
                synchronized(this@Analytics) {
                    if (session == currentSession) {
                        scheduled = false
                        if (succeeded && enabled && pending.isNotEmpty()) scheduleLocked()
                    }
                }
            }
        }
    }

    suspend fun flush(): Boolean = sending.withLock {
        val snapshot = synchronized(this) {
            while (pending.isNotEmpty() && now() - pending.first().at > 86_400_000) pending.removeFirst()
            if (!enabled || pending.isEmpty()) return@withLock true
            session to pending.take(50)
        }
        val body = JSONObject().put("schema_version", 1).put("app", "gentv_mobile")
            .put("app_version", version).put("android_sdk", sdk).put("session_id", snapshot.first)
            .put("events", JSONArray().apply {
                snapshot.second.forEach { e -> put(JSONObject().put("event_id", e.id).put("occurred_at", e.at)
                    .put("name", e.event.name.lowercase(java.util.Locale.ROOT)).put("value", e.value)) }
            }).toString()
        val accepted = runCatching {
            synchronized(this) { enabled && session == snapshot.first } && post(body)
        }.getOrDefault(false)
        if (accepted) synchronized(this) {
            val ids = snapshot.second.map { it.id }.toSet()
            pending.removeAll { it.id in ids }
        }
        accepted
    }

    companion object {
        /** Builds without a configured GenTV API never collect or send statistics. */
        val configured get() = com.gentv.mobile.BuildConfig.ANALYTICS_API.isNotEmpty()

        fun allowed(event: Event, value: String): Boolean = when (event) {
            Event.APP_OPEN, Event.TIDY_COMPLETE -> value.isEmpty()
            Event.SCREEN_VIEW -> value in com.gentv.core.flow.Step.entries.map { it.name.lowercase(java.util.Locale.ROOT) } || value in setOf("settings", "desktop")
            Event.CONNECTION_RESULT -> value in setOf("connected", "denied", "unreachable", "failed")
            Event.FILE_SEND_RESULT -> value in setOf("sent", "failed", "no_space", "cancelled")
            Event.UPDATE_CHECK -> value in setOf("current", "available", "failed", "unconfigured")
            Event.FEEDBACK_SUBMIT -> value in setOf("success", "failed", "unconfigured")
        }

        /** No redirects, cookies, identifiers, or response bodies. Server must deduplicate event_id. */
        fun post(body: String): Boolean {
            if (!configured) return false
            val connection = URL(com.gentv.mobile.BuildConfig.ANALYTICS_API).openConnection() as HttpURLConnection
            return try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode in setOf(200, 202, 204)
            } finally { connection.disconnect() }
        }
    }
}
