package com.gentv.mobile

import com.gentv.mobile.data.*
import com.gentv.core.ops.TvShell
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Opt-in end-to-end contract test against a source-built desktop on an authorized emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class DesktopPreviewEmulatorTest {
    @Test fun sourceBuiltPreviewInstallsOpensAndKeepsHomeAndConfiguration()=runBlocking {
        val serial=System.getProperty("gentv.emulatorSerial").orEmpty()
        assumeTrue("Opt in with -PemulatorSerial=emulator-xxxx",serial.matches(Regex("emulator-[0-9]+")))
        val sdk=System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: error("Android SDK required")
        val adb=File(sdk,"platform-tools/adb").absolutePath
        fun execute(vararg args:String):String {
            val process=ProcessBuilder(adb,"-s",serial,*args).redirectErrorStream(true).start()
            val text=process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor()==0) { "ADB operation failed" }
            return text
        }
        check(execute("shell","getprop ro.kernel.qemu").trim()=="1") { "Refusing a real device" }
        val shell=object:TvShell {
            override suspend fun run(command:String)=execute("shell",command)
            override suspend fun push(bytes:ByteArray,remotePath:String) {
                val temporary=File.createTempFile("gentv-preview-",".apk")
                try { temporary.writeBytes(bytes);execute("push",temporary.absolutePath,remotePath) }
                finally { temporary.delete() }
            }
        }
        val homeCommand="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        val homeBefore=shell.run(homeCommand)
        val before=DesktopClient(shell).read().getJSONObject("config").toString()
        val assets=File("build/generated/homes/launcher")
        val manifest=JSONObject(File(assets,"build-manifest.json").readText())
        val preview=DesktopPreviewApk(File(assets,"gentv-home-preview.apk").readBytes(),manifest.getJSONObject("sha256").getString("gentv-home-preview.apk"))
        val result=DesktopPreviewInstaller(shell).install(preview)
        assertEquals(2,result.getJSONObject("config").getInt("schema"))
        assertEquals(before,result.getJSONObject("config").toString())
        assertEquals(homeBefore,shell.run(homeCommand))
        val files=shell.run("ls /data/local/tmp/gentv-desktop-preview-*.apk 2>/dev/null || true")
        assertTrue("Temporary preview should be cleaned up",files.isBlank())
    }
}
