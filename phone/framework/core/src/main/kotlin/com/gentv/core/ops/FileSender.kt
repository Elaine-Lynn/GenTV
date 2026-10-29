package com.gentv.core.ops

/** What happened to one file. */
sealed interface SendResult {
    data class Sent(val remotePath: String) : SendResult
    /** The TV hasn't room for it; [freeBytes] is what's left. */
    data class NoSpace(val freeBytes: Long) : SendResult
    data object Disconnected : SendResult
}

/**
 * Sends files from the phone to the TV's Downloads › GenTV folder, where the TV's own file
 * manager or media player can open them. Names are made safe and unique on the TV; free space is
 * checked first so a big video fails up front rather than halfway.
 */
class FileSender(private val tv: TvShell) {
    /** Bytes free on the TV's shared storage, or null when `df` can't be read. */
    suspend fun freeBytes(): Long? = Parse.dfAvailableKb(tv.run(Commands.DF_SDCARD))?.let { it * 1024 }

    /** Names already in the folder (creating it if needed), so new files never overwrite. */
    suspend fun takenNames(): Set<String> =
        tv.run(Commands.LIST_SEND_DIR).lines().map { it.trim() }.filter { it.isNotEmpty() && it != "ok" }.toSet()

    suspend fun send(
        name: String,
        size: Long,
        open: () -> java.io.InputStream,
        taken: MutableSet<String>,
        onProgress: (Long) -> Unit = {},
    ): SendResult = try {
        val free = freeBytes()
        if (free != null && size > free - RESERVE_BYTES) SendResult.NoSpace(free)
        else {
            val safe = SafeName.unique(SafeName.clean(name), taken)
            val path = "${Commands.SEND_DIR}/$safe"
            open().use { tv.pushStream(it, size, path, onProgress) }
            taken += safe
            SendResult.Sent(path)
        }
    } catch (e: TvConnectionLost) {
        SendResult.Disconnected
    }

    private companion object {
        /** Leave the TV some breathing room: never fill it to the last 200 MB. */
        const val RESERVE_BYTES = 200L * 1024 * 1024
    }
}
