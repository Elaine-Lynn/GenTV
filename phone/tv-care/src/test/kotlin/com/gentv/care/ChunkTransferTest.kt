package com.gentv.care
import com.gentv.core.ops.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
class ChunkTransferTest {
    private class MemoryShell : TvShell {
        val files = mutableMapOf<String, ByteArray>()
        var dropAfterAppend = false
        override suspend fun push(bytes: ByteArray, remotePath: String) = Unit
        override suspend fun run(command: String): String = when {
            command.startsWith("if [ -f ") -> files[command.substringAfter("-f ").substringBefore(" ]")]?.size?.toString() ?: "0"
            command.startsWith(": > ") -> { files[command.substringAfter(": > ")] = byteArrayOf(); "" }
            command.startsWith("printf '%s' ") -> {
                val encoded = command.substringAfter("printf '%s' '").substringBefore("'")
                val path = command.substringAfter(">> ")
                files[path] = (files[path] ?: byteArrayOf()) + Base64.getDecoder().decode(encoded)
                if (dropAfterAppend) { dropAfterAppend = false; throw TvConnectionLost("append committed, response lost") }
                ""
            }
            command.startsWith("wc -c < ") -> files[command.substringAfter("wc -c < ")]?.size?.toString() ?: "error"
            command.startsWith("sha256sum ") -> files[command.split(' ')[1]]?.let { sha256(it) + "  file\n" } ?: ""
            command.startsWith("mv ") -> { val parts = command.split(' '); files[parts[2]] = files.remove(parts[1])!!; "" }
            else -> error(command)
        }
    }
    @Test fun binaryUploadResumesAfterAnUncertainAppendWithoutDuplicatingData() = runTest {
        val tv = MemoryShell(); val bytes = ByteArray(40_000) { (it % 255).toByte() }
        val transfer = ChunkTransfer(tv, 1024); tv.dropAfterAppend = true
        try { transfer.push(bytes, "/data/local/tmp/gentv-test.apk"); fail("expected interrupted acknowledgement") } catch (_: TvConnectionLost) { }
        transfer.push(bytes, "/data/local/tmp/gentv-test.apk")
        assertArrayEquals(bytes, tv.files["/data/local/tmp/gentv-test.apk"])
        assertEquals(1, tv.files.size)
    }
    @Test fun corruptPartialUploadAndShellPathsAreRefused() = runTest {
        val tv = MemoryShell(); val bytes = ByteArray(100) { 5 }
        val path = "/data/local/tmp/gentv-test.apk"
        tv.files["$path.part-${sha256(bytes).take(16)}"] = byteArrayOf(8)
        try { ChunkTransfer(tv).push(bytes, path); fail("corrupt prefix accepted") } catch (_: IllegalStateException) { }
        try { ChunkTransfer(tv).push(bytes, "/data/local/tmp/gentv-test;reboot"); fail("shell path accepted") } catch (_: IllegalArgumentException) { }
        assertFalse(path in tv.files)
    }
}
