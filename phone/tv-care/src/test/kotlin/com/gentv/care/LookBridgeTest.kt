package com.gentv.care

import com.gentv.core.lists.HomeApp
import com.gentv.core.ops.*
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LookBridgeTest {
    @Test fun themeRoundTripPreservesWallpaperAndDoesNotClearData() = runTest {
        val tv = FakeTv.googleTv()
        tv.installed += HomeApp.GENTV.pkg
        val path = Commands.configDir(HomeApp.GENTV.pkg)
        val original = buildJsonObject { put("columns", 4); put("input_key_service", false) }
        tv.pushed["$path/config.json"] = original.toString().toByteArray()
        tv.files["$path/wallpaper.jpg"] = "owner-wallpaper"
        val session = Session(Files.createTempDirectory("gentv-look-").toFile())
        val bridge = LookBridge(tv, session)
        val config = JsonObject(original + ("columns" to JsonPrimitive(5)))
        bridge.save(config, sha256(original.toString().toByteArray()), false)
        assertEquals(config, bridge.read())
        assertTrue(bridge.restoreIfChanged())
        assertEquals(original, bridge.read())
        assertEquals("owner-wallpaper", tv.files["$path/wallpaper.jpg"])
        assertFalse(tv.commands.any { it.contains("pm clear") || it.contains("*.jpg") })
    }
    @Test fun helperChangeRequiresOwnerChoiceAndStaleHashRefused() = runTest {
        val tv = FakeTv.googleTv(); tv.installed += HomeApp.GENTV.pkg
        val config = buildJsonObject { put("columns", 4) }
        tv.pushed[Commands.configDir(HomeApp.GENTV.pkg) + "/config.json"] = config.toString().toByteArray()
        val bridge = LookBridge(tv, Session(Files.createTempDirectory("gentv-look-").toFile()))
        val helper = JsonObject(config + ("home_key_service" to JsonPrimitive(true)))
        try { bridge.save(helper, sha256(config.toString().toByteArray()), false); fail("helper enabled silently") } catch (_: IllegalArgumentException) { }
        try { bridge.save(config, "stale", true); fail("stale profile accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, tv.pushed.size)
        assertEquals(config.toString(), String(tv.pushed.values.single()))
    }
}
