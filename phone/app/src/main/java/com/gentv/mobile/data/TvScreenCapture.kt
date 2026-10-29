package com.gentv.mobile.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gentv.core.ops.TvShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** One best-effort capture per homepage visit. No polling, upload or persistent phone copy. */
object TvScreenCapture {
    suspend fun capture(shell: TvShell): Bitmap? = withContext(Dispatchers.IO) {
        val path = "/data/local/tmp/gentv-screen-${UUID.randomUUID()}.png"
        try {
            withTimeoutOrNull(4_000) {
                shell.run("toybox timeout 3 screencap -p $path")
                val length = shell.run("stat -c %s $path").trim().toLongOrNull() ?: return@withTimeoutOrNull null
                if (length !in 1..8_388_608) return@withTimeoutOrNull null
                val bytes = shell.pull(path) ?: return@withTimeoutOrNull null
                if (bytes.size.toLong() != length) return@withTimeoutOrNull null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) return@withTimeoutOrNull null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1280) inSampleSize *= 2
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        finally {
            withContext(NonCancellable) {
                try { withTimeoutOrNull(1_000) { shell.run("rm -f $path") } } catch (_: Exception) { }
            }
        }
    }
}
