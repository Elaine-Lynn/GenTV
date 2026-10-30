package com.gentv.care
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class ConfigurationTest {
    private val snapshot = Json.parseToJsonElement("""{"config":{"schema":2,"order":["com.example.a/.Main","com.example.b/.Main"],"hidden":[],"names":{},"effects":"auto","folders":{"keep":"metadata"}},"apps":[{"id":"com.example.a/.Main","package":"com.example.a"},{"id":"com.example.b/.Main","package":"com.example.b"}]}""").jsonObject
    @Test fun commonRequestsMergeAndPreserveUnrelatedSettings() {
        val look = buildJsonObject { put("wallpaper_dim", 10) }
        val draft = Configuration.draft(snapshot, look, mapOf("columns" to "5", "dock" to "com.example.b,com.example.a", "rename-app" to "com.example.a=My TV", "boot" to "home", "effects" to "smooth"))
        assertEquals("com.example.b/.Main", draft.layout["order"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals(snapshot["config"]!!.jsonObject["folders"], draft.layout["folders"])
        assertEquals(10, draft.look["wallpaper_dim"]!!.jsonPrimitive.int)
        assertTrue(draft.look["power_on_home"]!!.jsonPrimitive.boolean)
        assertFalse(draft.look["boot_to_live_tv"]!!.jsonPrimitive.boolean)
    }
    @Test fun unknownApplicationsAndInvalidValuesAreRefusedBeforeWrite() {
        for (change in listOf(mapOf("dock" to "com.missing.app"), mapOf("columns" to "99"), mapOf("rename-app" to "com.example.a=bad\nname"))) {
            try { Configuration.draft(snapshot, buildJsonObject {}, change); fail("invalid request accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
