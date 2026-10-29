package com.gentv.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What the rating prompt remembers: asks used, when the last was, and whether the person rated or gave feedback. */
@Serializable
data class RatingState(val asks: Int = 0, val lastAskAt: Long = 0, val done: Boolean = false)

/**
 * When to ask "Enjoying GenTV?". Rarely and at a good moment: right after a clean-up that really turned
 * something off. At most two automatic asks per install, 30 days apart; never again once the person rated or
 * went to feedback ("Maybe later" counts as an ask used).
 */
object RatingPolicy {
    const val MAX_ASKS = 2
    const val GAP_MS = 30L * 24 * 60 * 60 * 1000

    fun shouldAsk(state: RatingState, now: Long, goodMoment: Boolean, busy: Boolean): Boolean =
        goodMoment && !busy && !state.done && state.asks < MAX_ASKS &&
            (state.asks == 0 || now - state.lastAskAt >= GAP_MS)
}

/** The rating state on disk. A missing or unreadable file means "never asked". */
class RatingStore(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): RatingState = runCatching { json.decodeFromString(RatingState.serializer(), file.readText()) }.getOrDefault(RatingState())

    private fun write(s: RatingState) {
        file.parentFile?.mkdirs()
        runCatching { file.writeText(json.encodeToString(RatingState.serializer(), s)) }
    }

    /** Counts an automatic ask. */
    fun recordAsk() = write(read().let { it.copy(asks = it.asks + 1, lastAskAt = now()) })

    /** The person rated or went to feedback: never ask again. */
    fun recordDone() = write(read().copy(done = true))
}
