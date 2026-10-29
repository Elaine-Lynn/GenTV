package com.gentv.mobile

import android.util.Base64
import com.gentv.mobile.data.*
import com.gentv.core.ops.TvShell
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class DesktopDeliveryTest {
    private class Shell:TvShell {
        var sdk="36";var install="Success";var pushes=0
        val commands=mutableListOf<String>()
        override suspend fun push(bytes:ByteArray,remotePath:String) { pushes++ }
        override suspend fun run(command:String):String {
            commands+=command
            return when {
                command=="getprop ro.build.version.sdk"->sdk
                command.startsWith("pm install -r ")->install
                command=="pm path com.gentv.tv"->"package:/data/app/com.gentv.tv/base.apk"
                command.startsWith("am start -W ")->"Status: ok"
                command.startsWith("am broadcast ")->"GENTV1:"+Base64.encodeToString(JSONObject().put("protocol",1).put("ok",true).put("backend","gentv").put("config",JSONObject().put("schema",2)).toString().toByteArray(),Base64.NO_WRAP)
                else->""
            }
        }
    }
    private fun apk():DesktopPreviewApk {
        val bytes="PK preview fixture".toByteArray()
        return DesktopPreviewApk(bytes,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
    }
    @Test fun previewInstallsAndOpensWithoutHomeOrPermissionChanges()=runBlocking {
        val shell=Shell()
        assertEquals("gentv",DesktopPreviewInstaller(shell).install(apk()).getString("backend"))
        assertEquals(1,shell.pushes)
        assertTrue(shell.commands.any { it.startsWith("rm -f /data/local/tmp/gentv-desktop-preview") })
        assertFalse(shell.commands.any { "set-home" in it || "disable" in it || "uninstall" in it || "settings put" in it || "pm grant" in it })
    }
    @Test fun signatureOrVerificationRefusalNeverRetriesOrUninstalls()=runBlocking {
        for (failure in listOf("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]","Failure [INSTALL_FAILED_VERIFICATION_FAILURE]")) {
            val shell=Shell().apply { install=failure }
            assertTrue(runCatching { DesktopPreviewInstaller(shell).install(apk()) }.isFailure)
            assertEquals(1,shell.commands.count { it.startsWith("pm install") })
            assertFalse(shell.commands.any { it.startsWith("am start") || "uninstall" in it })
            assertTrue(shell.commands.last().startsWith("rm -f "))
        }
    }
    @Test fun oldSystemAndCorruptApkAreRejectedBeforePush()=runBlocking {
        val old=Shell().apply { sdk="25" }
        assertTrue(runCatching { DesktopPreviewInstaller(old).install(apk()) }.isFailure)
        assertEquals(0,old.pushes)
        val shell=Shell()
        assertTrue(runCatching { DesktopPreviewInstaller(shell).install(apk().copy(sha256="0".repeat(64))) }.isFailure)
        assertTrue(shell.commands.isEmpty());assertEquals(0,shell.pushes)
    }
}
