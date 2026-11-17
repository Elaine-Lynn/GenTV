package com.gentv.care

import com.gentv.core.ops.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class DesktopBridgeTest {
    private val original = Json.parseToJsonElement("""{"schema":2,"order":[],"hidden":[],"names":{},"effects":"auto"}""").jsonObject
    private class BridgeTv(var config: JsonObject) : TvShell {
        var revision = 1
        var saves = 0
        var dropAfterSave = false
        override suspend fun push(bytes: ByteArray, remotePath: String) = Unit
        override suspend fun run(command: String): String {
            if (command.contains("--es operation save")) {
                val expected = Regex("--ei revision (\\d+)").find(command)!!.groupValues[1].toInt()
                if (expected != revision) return "GENTV_ERROR:revision"
                val payload = Regex("--es payload '([^']+)'").find(command)!!.groupValues[1]
                config = Json.parseToJsonElement(String(Base64.getDecoder().decode(payload))).jsonObject
                revision++; saves++
                if (dropAfterSave) { dropAfterSave = false; throw TvConnectionLost("disconnected after commit") }
            }
            val response = buildJsonObject { put("protocol", 1); put("backend", "gentv"); put("ok", true); put("revision", revision); put("config", config) }
            return "GENTV1:" + Base64.getEncoder().encodeToString(response.toString().toByteArray())
        }
    }
    private fun session(): Session {
        val directory = Files.createTempDirectory("gentv-desktop-test-").toFile()
        directory.deleteOnExit()
        return Session(directory)
    }
    private fun changed() = JsonObject(original + ("effects" to JsonPrimitive("smooth")))

    @Test fun profileRoundTripRetainsAnOriginalSnapshot() = runTest {
        val tv = BridgeTv(original); val session = session(); val bridge = DesktopBridge(tv, session)
        bridge.save(changed(), 1)
        assertEquals(changed(), tv.config)
        assertTrue(File(session.directory, "desktop-original.json").isFile)
        assertTrue(bridge.restoreIfChanged())
        assertEquals(original, tv.config)
        assertEquals(2, tv.saves)
    }
    @Test fun uncertainWriteCanBeReconciledWithoutReplay() = runTest {
        val tv = BridgeTv(original); val bridge = DesktopBridge(tv, session())
        tv.dropAfterSave = true
        try { bridge.save(changed(), 1); fail("expected disconnect") } catch (_: TvConnectionLost) { }
        assertEquals(1, tv.saves)
        assertTrue(bridge.restoreIfChanged())
        assertEquals(original, tv.config)
        assertEquals(2, tv.saves)
    }
    @Test fun secondCommittedButUnconfirmedWriteCanStillBeRestored() = runTest {
        val tv = BridgeTv(original); val bridge = DesktopBridge(tv, session())
        bridge.save(changed(), 1)
        val second = JsonObject(original + ("effects" to JsonPrimitive("full")))
        tv.dropAfterSave = true
        try { bridge.save(second, 2); fail("expected disconnect") } catch (_: TvConnectionLost) { }
        assertEquals(2, tv.saves)
        assertTrue(bridge.restoreIfChanged())
        assertEquals(original, tv.config)
        assertEquals(3, tv.saves)
    }
    @Test fun concurrentDesktopChangesArePreserved() = runTest {
        val tv = BridgeTv(original); val bridge = DesktopBridge(tv, session())
        bridge.save(changed(), 1)
        tv.revision++
        tv.config = JsonObject(original + ("effects" to JsonPrimitive("full")))
        try { bridge.restoreIfChanged(); fail("concurrent profile overwritten") } catch (_: IllegalArgumentException) { }
        assertEquals("full", tv.config["effects"]!!.jsonPrimitive.content)
        assertEquals(1, tv.saves)
    }
    @Test fun staleRevisionAndForeignSchemaRefusedBeforeWrite() = runTest {
        val tv = BridgeTv(original); val bridge = DesktopBridge(tv, session())
        for ((profile, revision) in listOf(changed() to 0, JsonObject(original + ("schema" to JsonPrimitive(1))) to 1)) {
            try { bridge.save(profile, revision); fail("invalid save accepted") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, tv.saves)
    }
}
