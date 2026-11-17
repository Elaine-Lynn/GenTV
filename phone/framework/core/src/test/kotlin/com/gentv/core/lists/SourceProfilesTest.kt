package com.gentv.core.lists

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SourceProfilesTest {
    private fun tv(model: String = "Model X55", device: String = "boardx", release: String = "11") =
        TvInfo(model, device, device, "Acme", "Acme", 30, release, "test")
    private fun row(id: String, title: String, action: String, android: String = "11") =
        """{"id":"$id","title":"$title","android":["$android"],"config":{"input_action":"$action","dock_apps":["com.example.app"]}}"""

    @Test fun exactModelOnlyAndCorrectAndroidVersion() {
        val profiles = SourceProfiles("[${row("acme-model-x55", "Acme Model X55", "acme.INPUT")}]")
        assertEquals("acme.INPUT", Json.parseToJsonElement(profiles.select(tv())!!).jsonObject["input_action"]!!.jsonPrimitive.content)
        assertNull(profiles.select(tv("Model X66")))
        assertNull(profiles.select(tv(release = "12")))
        assertNull(profiles.select(tv().copy(brand = "Other", manufacturer = "Other")))
        assertFalse(profiles.select(tv())!!.contains("dock_apps"))
    }

    @Test fun conflictingSharedBoardsDoNotGuessAPlayersSourceNumbers() {
        val profiles = SourceProfiles("[${row("acme-a55", "Acme A55 boardx", "acme.A")},${row("acme-b55", "Acme B55 boardx", "acme.B")}]")
        assertNull(profiles.select(tv()))
    }

    @Test fun unrelatedAndMalformedFieldsCannotBecomeRoutes() {
        val out = SourceProfiles.routing(Json.parseToJsonElement("""{"input_action":false,"input_sources":[1],"coocaa_sources":"true","input_key_keycodes":["178"],"live_tv_package":"","dock_apps":[]} """).jsonObject)
        assertEquals(setOf("live_tv_package"), out.keys)
    }

    @Test fun bundledProfilesMatchTheirConfigFilesAndKnownVendorFamilies() {
        val data = File(System.getProperty("gentv.deviceData") ?: "../data")
        val text = File(data, "source-profiles.json").readText()
        val profiles = SourceProfiles(text)
        val rows = Json.parseToJsonElement(text).jsonArray
        assertTrue(rows.size >= 71)
        assertEquals(rows.size, rows.map { it.jsonObject["id"] }.distinct().size)
        for (element in rows) {
            val row = element.jsonObject
            val id = row["id"]!!.jsonPrimitive.content
            val file = File(data, "devices/$id/config.json").takeIf { it.exists() }
                ?: File(data, "input-profiles/$id/config.json")
            val config = Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals(SourceProfiles.routing(config), row["config"])
        }
        val skyworth = TvInfo("7S60_A5", "sky8S34", "sky8S34", "Skyworth", "Skyworth", 26, "8", "test")
        assertEquals("true", Json.parseToJsonElement(profiles.select(skyworth)!!).jsonObject["coocaa_sources"]!!.jsonPrimitive.content)
        val tcl = TvInfo("tc8000_ay30a2", "tc8000_ay30a2", "tc8000_ay30a2", "TCL", "TCL", 30, "11", "test")
        assertTrue(profiles.select(tcl)!!.contains("com.tcl.settings"))
        assertNull(profiles.select(TvInfo("DBB3 Pro", "galilei", "galilei", "Droidlogic", "Droidlogic", 28, "9", "test")))
    }
}
