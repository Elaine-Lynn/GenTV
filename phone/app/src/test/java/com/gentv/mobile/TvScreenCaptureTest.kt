package com.gentv.mobile

import android.graphics.Bitmap
import com.gentv.mobile.data.TvScreenCapture
import com.gentv.core.ops.TvShell
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TvScreenCaptureTest {
    private class Shell(val bytes: ByteArray?, val size: Long = bytes?.size?.toLong() ?: 0) : TvShell {
        val commands = mutableListOf<String>()
        var pulled = false
        override suspend fun push(bytes: ByteArray, remotePath: String) = Unit
        override suspend fun pull(remotePath: String): ByteArray? { pulled = true; return bytes }
        override suspend fun run(command: String): String {
            commands += command
            return if (command.startsWith("stat ")) "$size" else ""
        }
    }
    @Test fun displaysDecodedCaptureAndRemovesOnlyItsOwnTemporaryFile() = runBlocking {
        val bytes = ByteArrayOutputStream().also { Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val shell = Shell(bytes)
        val result = TvScreenCapture.capture(shell)
        assertNotNull(result)
        assertEquals(64, result!!.width)
        assertTrue(shell.commands.first().startsWith("toybox timeout 3 screencap -p /data/local/tmp/gentv-screen-"))
        assertEquals("rm -f " + shell.commands.first().substringAfter("screencap -p "), shell.commands.last())
    }
    @Test fun refusesOversizedCaptureBeforePullAndFallsBackOnInvalidImage() = runBlocking {
        val large = Shell(null, 9_000_000)
        assertNull(TvScreenCapture.capture(large)); assertFalse(large.pulled)
        assertTrue(large.commands.last().startsWith("rm -f "))
        assertNull(TvScreenCapture.capture(Shell(byteArrayOf(1, 2, 3))))
    }
}
