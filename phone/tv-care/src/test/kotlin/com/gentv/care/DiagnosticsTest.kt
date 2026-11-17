package com.gentv.care
import com.gentv.core.ops.*
import com.gentv.core.testing.FakeTv
import com.gentv.core.lists.HomeApp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class DiagnosticsTest {
    @Test fun audioPermissionAndRejectedAppOpsDoNotBecomeRecordingEvidence() = runTest {
        val commands = mutableListOf<String>()
        val tv = object : TvShell {
            override suspend fun push(bytes: ByteArray, remotePath: String) = error("audit must not push")
            override suspend fun run(command: String): String {
                commands += command
                return when {
                    command == Commands.LIST_PACKAGES -> "package:com.example.voice\n"
                    command == Commands.LIST_DISABLED -> ""
                    command.startsWith("settings get") -> "null"
                    command.startsWith("dumpsys package") -> "requested permissions: android.permission.RECORD_AUDIO\nsharedUserId=system"
                    command.startsWith("cmd appops get") -> "RECORD_AUDIO: allow; rejectTime=+2m0s ago"
                    else -> error(command)
                }
            }
        }
        val report = PrivacyAudit(tv, File(System.getProperty("gentv.repo"))).collect()
        val finding = report["findings"]!!.jsonArray.single().jsonObject
        assertFalse(finding["recordedAudioAccess"]!!.jsonPrimitive.boolean)
        assertFalse(finding["recordAudioGranted"]!!.jsonPrimitive.boolean)
        assertTrue(finding["sharedUid"]!!.jsonPrimitive.boolean)
        assertEquals("name-only", finding["evidenceLevel"]!!.jsonPrimitive.content)
        assertFalse(commands.any { it.startsWith("pm disable") || it.startsWith("settings put") })
    }
    @Test fun invalidFramesAndUnavailableCountersStayUnknown() {
        val raw = "Flags,IntendedVsync,FrameCompleted\n0,100,200\n1,100,300\n0,0,200\n0,200,100\n"
        assertEquals(listOf(100L to 200L), Metrics.frames(raw))
        assertNull(Metrics.percentile(emptyList(), .95))
        assertNull(Metrics.busy(null, 2L to 1L))
        assertNull(Metrics.busy(10L to 5L, 5L to 2L))
        assertEquals(50.0, Metrics.busy(100L to 50L, 200L to 100L)!!, .001)
    }
    @Test fun benchmarkUsesInstalledAppWithoutInstallingClearingOrChangingDefaultHome() = runTest {
        val fake = FakeTv.googleTv().apply {
            installed += HomeApp.GENTV.pkg; homeClaimants[HomeApp.GENTV.component] = 0; preferredHome = HomeApp.GENTV.component
            props["ro.build.fingerprint"] = "benchmark-fixture"
            props["ro.serialno"] = "unknown"; props["ro.boot.serialno"] = "unknown"
        }
        var cpuReads = 0
        val commands = mutableListOf<String>()
        val tv = object : TvShell by fake {
            override suspend fun run(command: String): String {
                commands += command
                return when {
                    command.startsWith("u=\$(cut") -> "GENTV_KEY_UPTIME=100.0\n"
                    command.endsWith("framestats") -> "Total frames rendered: 4\nJanky frames: 1 (25.0%)\nFlags,IntendedVsync,FrameCompleted\n0,100000000000,100020000000\n"
                    command == "dumpsys meminfo com.gentv.tv" -> "TOTAL PSS: 8192\n"
                    command == "cat /proc/stat" -> if (cpuReads++ == 0) "cpu 50 0 0 50 0 0 0 0" else "cpu 100 0 0 100 0 0 0 0"
                    command.startsWith("am start -W") -> "Status: ok\nTotalTime: 20\nWaitTime: 25\n"
                    else -> fake.run(command)
                }
            }
        }
        val report = Performance(tv, wait = {}).measure(3, 1)
        assertEquals(JsonNull, report["deviceKey"])
        try { comparePerformance(report, report); fail("placeholder serial accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(25.0, report["summary"]!!.jsonObject["jankPercent"]!!.jsonPrimitive.double, .01)
        assertEquals(3, report["summary"]!!.jsonObject["coldSamples"]!!.jsonPrimitive.int)
        assertEquals(20.0, report["summary"]!!.jsonObject["coldStartMsP50"]!!.jsonPrimitive.double, .01)
        assertEquals(8.0, report["summary"]!!.jsonObject["pssMb"]!!.jsonPrimitive.double, .01)
        assertFalse(commands.any { it.startsWith("pm install") || it.startsWith("pm clear") || it.contains("set-home-activity") })
        assertEquals(HomeApp.GENTV.component, fake.home())
    }
    @Test fun crossDevicePerformanceComparisonIsRefused() {
        val first = Json.parseToJsonElement("""{"kind":"performance","deviceKey":"a","firmware":"x","summary":{"pssMb":8}}""").jsonObject
        val second = JsonObject(first + ("deviceKey" to JsonPrimitive("b")))
        try { comparePerformance(first, second); fail("different device accepted") } catch (_: IllegalArgumentException) { }
    }
}
