package com.gentv.care

import com.gentv.core.ops.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** One explicitly authorised transport. Never scans, connects, pairs, or chooses a device. */
class AdbShell(private val adb: String, val serial: String, private val python: String? = null) : TvShell {
    init { require(serial.isNotBlank() && !serial.startsWith("-") && serial.none { it.isWhitespace() || it.isISOControl() }) }
    private suspend fun execute(args: List<String>, binary: Boolean = false): ByteArray = withContext(Dispatchers.IO) {
        val stdout = Files.createTempFile("gentv-adb-", ".out").toFile()
        val stderr = Files.createTempFile("gentv-adb-", ".err").toFile()
        try {
            val process = ProcessBuilder(listOfNotNull(python) + listOf(adb, "-s", serial) + args)
                .redirectOutput(stdout).redirectError(stderr).start()
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly(); process.waitFor()
                throw TvConnectionLost("ADB timed out; reconcile the session before retrying")
            }
            if (process.exitValue() != 0) throw TvConnectionLost(LogRedactor.redact(stderr.readText(), listOf(serial)))
            val bytes = stdout.readBytes()
            if (binary) bytes else bytes + stderr.readBytes()
        } finally { stdout.delete(); stderr.delete() }
    }
    suspend fun requireAuthorised() { require(execute(listOf("get-state")).decodeToString().trim() == "device") { "Device is not authorised" } }
    override suspend fun run(command: String) = execute(listOf("shell", command)).decodeToString()
    override suspend fun push(bytes: ByteArray, remotePath: String) {
        val local = File.createTempFile("gentv-push-", ".tmp")
        try {
            local.writeBytes(bytes)
            try { execute(listOf("push", local.absolutePath, remotePath)) }
            catch (_: TvConnectionLost) { ChunkTransfer(this).push(bytes, remotePath); return }
            ChunkTransfer(this).verify(bytes, remotePath)
        } finally { local.delete() }
    }
    override suspend fun pull(remotePath: String): ByteArray? {
        val local = File.createTempFile("gentv-pull-", ".tmp")
        return try { execute(listOf("pull", remotePath, local.absolutePath)); local.readBytes() }
        catch (_: TvConnectionLost) { null } finally { local.delete() }
    }
    suspend fun screenshot(): ByteArray {
        val bytes = execute(listOf("exec-out", "screencap", "-p"), binary = true)
        require(bytes.size > 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {
            "No valid screenshot; protected content may require owner confirmation"
        }
        return bytes
    }
}
