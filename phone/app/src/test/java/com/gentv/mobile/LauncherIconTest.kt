package com.gentv.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Render the real Android vectors, including adaptive-layer inset and themed alpha artwork. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LauncherIconTest {
    @Test fun rendersLegacyAdaptiveAndMonochromeMasks() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(context.getDrawable(R.mipmap.ic_gentv) is AdaptiveIconDrawable)
        assertNotNull((context.getDrawable(R.mipmap.ic_gentv) as AdaptiveIconDrawable).monochrome)
        val directory = File("build/screenshots/launcher-icons").apply { mkdirs() }
        fun save(name: String, draw: (Canvas) -> Unit) {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("Artwork must render", Color.alpha(bitmap.getPixel(256, 256)) > 0)
        }
        save("legacy") { canvas -> context.getDrawable(R.drawable.ic_gentv)!!.apply { setBounds(0, 0, 512, 512); draw(canvas) } }
        for (round in listOf(false, true)) save(if (round) "circle" else "rounded") { canvas ->
            canvas.clipPath(Path().apply { if (round) addCircle(256f, 256f, 256f, Path.Direction.CW) else addRoundRect(RectF(0f, 0f, 512f, 512f), 112f, 112f, Path.Direction.CW) })
            for (id in listOf(R.drawable.ic_gentv_background, R.drawable.ic_gentv_foreground)) {
                context.getDrawable(id)!!.apply { setBounds(-128, -128, 640, 640); draw(canvas) }
            }
        }
        save("monochrome") { canvas ->
            canvas.drawColor(Color.rgb(212, 229, 255))
            canvas.clipPath(Path().apply { addCircle(256f, 256f, 256f, Path.Direction.CW) })
            context.getDrawable(R.drawable.ic_gentv_monochrome)!!.mutate().apply {
                setTint(Color.rgb(34, 55, 80)); setBounds(-128, -128, 640, 640); draw(canvas)
            }
        }
    }
}
