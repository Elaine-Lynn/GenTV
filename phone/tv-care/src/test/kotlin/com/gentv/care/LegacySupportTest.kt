package com.gentv.care
import com.gentv.core.ops.*
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LegacySupportTest {
    @Test fun homeParserNeverUsesAReceiverOrAnUnrelatedActivity() {
        val dump = """Activity Resolver Table:
  abcd com.vendor.home/.Main filter 12
    Category: "android.intent.category.LEANBACK_LAUNCHER"
  1234 com.vendor.home/.Home filter 13
    Category: "android.intent.category.HOME"
Receiver Resolver Table:
  1234 com.bad.receiver/.Fake filter 14
    Category: "android.intent.category.HOME"
"""
        assertEquals(setOf("com.vendor.home/.Home"), LegacyShell.homeComponents(dump))
    }
    @Test fun oldHomeQueryUsesActualHomeActivityEvidence() = runTest {
        val tv = object : TvShell {
            override suspend fun push(bytes: ByteArray, remotePath: String) = Unit
            override suspend fun run(command: String): String = when {
                command.startsWith("cmd ") || command.startsWith("pm resolve") || command.startsWith("pm query") -> "unknown command"
                command == "dumpsys activity activities" -> "mHomeActivity: ActivityRecord{1 u0 com.vendor.home/.Home t1}"
                command == "dumpsys package" -> "Activity Resolver Table:\n  aa com.vendor.home/.Home filter x\n    Category: android.intent.category.HOME\n"
                else -> ""
            }
        }
        assertEquals("com.vendor.home/.Home", LegacyShell(tv).run(Commands.RESOLVE_HOME))
        assertEquals(setOf("com.vendor.home"), Parse.componentsIn(LegacyShell(tv).run(Commands.QUERY_HOME)))
    }
    @Test fun confirmedLegacyDeviceWithoutSerialOrFingerprintCanBindAndRejectAnotherDevice() = runTest {
        val fake = FakeTv.googleTv().apply {
            props.remove("ro.serialno"); props.remove("ro.boot.serialno"); props.remove("ro.build.fingerprint")
            props["ro.build.version.sdk"] = "22"; props["ro.build.id"] = "LEGACY1"
        }
        val files = mutableMapOf<String,String>()
        val tv = object : TvShell by fake {
            override suspend fun run(command: String): String = when {
                command.startsWith("settings list ") -> "fixture=1"
                command.startsWith("if [ -f ${DeviceIdentity.MARKER}") -> files[DeviceIdentity.MARKER].orEmpty()
                command.startsWith("mkdir -p") && command.contains(DeviceIdentity.MARKER) -> { files[DeviceIdentity.MARKER] = Regex("'([a-f0-9]{64})'").find(command)!!.groupValues[1]; "" }
                command == "cat ${DeviceIdentity.MARKER}" -> files[DeviceIdentity.MARKER].orEmpty()
                else -> fake.run(command)
            }
        }
        val directory = File(Files.createTempDirectory("gentv-old-").toFile(), "session")
        val session = Session(directory)
        session.create(tv, deviceConfirmed = true)
        session.verify(tv)
        files[DeviceIdentity.MARKER] = "another-device"
        try { session.verify(tv); fail("different legacy target accepted") } catch (_: IllegalArgumentException) { }
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun aModelNameAloneCannotBindARecoverySession() {
        try { DeviceIdentity.firmware(mapOf("ro.product.model" to "Smart TV")); fail("model treated as firmware") } catch (_: IllegalArgumentException) { }
    }
}
