package com.gentv.mobile.data

import com.gentv.core.ops.TvShell
import org.json.JSONObject
import java.security.MessageDigest

/** Source-built, hash-checked preview. Deliberately does not hand over Home or grant permissions. */
data class DesktopPreviewApk(val bytes: ByteArray, val sha256: String)

class DesktopPreviewInstaller(private val shell: TvShell) {
    suspend fun install(apk: DesktopPreviewApk): JSONObject {
        require(apk.bytes.size in 4..2_000_000 && apk.bytes[0] == 'P'.code.toByte() && apk.bytes[1] == 'K'.code.toByte()) { "Invalid preview APK" }
        val digest = MessageDigest.getInstance("SHA-256").digest(apk.bytes).joinToString("") { "%02x".format(it) }
        require(apk.sha256.matches(Regex("[a-f0-9]{64}")) && digest == apk.sha256) { "Preview integrity check failed. Rebuild the phone app." }
        val sdk = shell.run("getprop ro.build.version.sdk").trim().toIntOrNull()
        require(sdk != null && sdk >= 26) { "GenTV Home preview requires Android 8 or newer." }
        val remote = "/data/local/tmp/gentv-desktop-preview-${java.util.UUID.randomUUID()}.apk"
        try {
            shell.push(apk.bytes, remote)
            val installed = shell.run("pm install -r $remote")
            check(installed.lineSequence().any { it.trim() == "Success" }) {
                "The TV refused the preview installation. No retry or uninstall was performed. Check signature compatibility and the TV's installation settings."
            }
            check(shell.run("pm path com.gentv.tv").lineSequence().any { it.startsWith("package:") }) { "Preview installation was not confirmed." }
            val started = shell.run("am start -W -n com.gentv.tv/.MainActivity")
            check(started.lineSequence().any { it.trim() == "Status: ok" }) { "Preview was installed but opening it was not confirmed." }
            return DesktopClient(shell).read().also { snapshot ->
                check(snapshot.optString("backend") == "gentv" && snapshot.getJSONObject("config").getInt("schema") == 2) { "Preview configuration bridge was not confirmed." }
            }
        } finally {
            // Cleanup failure must not hide the result of a successful install or a rejected one.
            try { shell.run("rm -f $remote") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
    }
}
