package com.gentv.core.ops

import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLookTest {
    private val dir = "/sdcard/Android/data/com.gentv.tv/files"
    private val glass = """{"dock_apps": ["com.sample.app"], "wallpaper": true, "dock_glass": true}"""

    private fun config(tv: FakeTv) = Json.parseToJsonElement(String(tv.pushed["$dir/config.json"]!!)).jsonObject

    @Test fun aLookKeepsTheOwnersDockAndRestartsHome() = runTest {
        val tv = FakeTv.googleTv()
        val look = LauncherLook(tv) { 42 }
        look.writeDock(listOf("com.netflix.ninja", "com.google.android.youtube.tv"))
        assertTrue(look.apply(LookFiles("glass", glass, wallpaper = byteArrayOf(1, 2, 3))))

        val c = config(tv)
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv"), c["dock_apps"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", c["dock_glass"]!!.jsonPrimitive.content)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(tv.pushed["$dir/wallpaper.jpg"]))
        assertEquals("42", tv.files["$dir/theme_revision"])
        assertEquals(1, tv.lookRestarts)
        assertTrue("every file went through the launcher's provider", "config.json" in tv.lookWrites && "wallpaper.jpg" in tv.lookWrites)
        assertTrue("nothing left in the staging area", tv.pushed.keys.none { it.startsWith("/data/local/tmp/gentv-look-") })
    }

    @Test fun aCollectionReplacesTheSingleWallpaper() = runTest {
        val tv = FakeTv.googleTv()
        val look = LauncherLook(tv) { 1 }
        look.apply(LookFiles("aurora", """{"wallpaper": true}""", wallpaper = byteArrayOf(9)))
        look.apply(LookFiles("scenes", """{"wallpaper": true, "wallpaper_rotation_minutes": 60}""",
            wallpapers = listOf("01-dusk.jpg" to byteArrayOf(1), "02-coast.jpg" to byteArrayOf(2)), captions = "Dusk\nCoast\n".toByteArray()))
        assertNull(tv.pushed["$dir/wallpaper.jpg"])
        assertTrue(tv.pushed.containsKey("$dir/wallpapers/01-dusk.jpg") && tv.pushed.containsKey("$dir/wallpapers/02-coast.jpg"))
        assertTrue(tv.pushed.containsKey("$dir/wallpapers/captions.txt"))

        look.apply(LookFiles("default", "{}"))
        assertTrue("switching looks clears the old collection", tv.pushed.keys.none { it.startsWith("$dir/wallpapers/") || it == "$dir/wallpaper.jpg" })
    }

    @Test fun noDockOnTheTvMeansNoSampleDockEither() {
        val out = Json.parseToJsonElement(LauncherLook.merged(glass, emptyList())).jsonObject
        assertFalse("the theme's sample apps never become the owner's dock", out.containsKey("dock_apps"))
        assertEquals("""{"dock_apps":["com.a.b"],"tile_menu":true,"app_library":true,"launcher_settings_button":true}""", LauncherLook.merged("not json", listOf("com.a.b")).trim())
        // A theme that sets one of them itself keeps its value.
        val off = Json.parseToJsonElement(LauncherLook.merged("""{"tile_menu": false}""", emptyList())).jsonObject
        assertEquals("false", off["tile_menu"]!!.jsonPrimitive.content)
        assertEquals("true", off["app_library"]!!.jsonPrimitive.content)
    }

    @Test fun fileNamesCantEscapeTheFolder() {
        for (bad in listOf("../x.jpg", "a b.jpg", "x;rm.jpg", "wallpapers/../x", ".hidden"))
            assertTrue(bad, runCatching { Commands.lookPut("com.gentv.tv", bad) }.isFailure)
        assertTrue(runCatching { LookFiles("x", "{}", wallpapers = listOf("\$(reboot).jpg" to byteArrayOf())) }.isFailure)
        assertEquals("/data/local/tmp/gentv-look-wallpapers-a.jpg", Commands.lookStage("wallpapers/a.jpg"))
    }

    @Test fun oldAndroidsUnsupportedWriteFallsBackToCopy() {
        val put = Commands.lookPut("com.gentv.tv", "config.json")
        assertTrue(put.contains("*'Unsupported operation'*) false"))
        assertTrue(put.contains("|| cp /data/local/tmp/gentv-look-config.json $dir/config.json"))
    }
    @Test fun deviceRoutesSurviveThemesAndDockUpdates() = runTest {
        val tv = FakeTv.googleTv()
        val look = LauncherLook(tv) { 9 }
        look.configureSources("""{"input_action":"vendor.HDMI","input_sources":["HW5=23"],"live_tv_package":"vendor.tv","coocaa_sources":false}""")
        look.writeDock(listOf("com.netflix.ninja"))
        look.apply(LookFiles("glass", """{"input_action":"wrong.ACTION","live_tv_package":"wrong.tv","wallpaper":true}"""))
        val c = config(tv)
        assertEquals("vendor.HDMI", c["input_action"]!!.jsonPrimitive.content)
        assertEquals("vendor.tv", c["live_tv_package"]!!.jsonPrimitive.content)
        assertEquals("HW5=23", c["input_sources"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("false", c["coocaa_sources"]!!.jsonPrimitive.content)
        assertEquals("com.netflix.ninja", c["dock_apps"]!!.jsonArray.single().jsonPrimitive.content)
        look.configureSources("""{"input_action":"new.ACTION"}""")
        assertEquals("vendor.HDMI", config(tv)["input_action"]!!.jsonPrimitive.content)
    }

    @Test fun inaccessibleConfigStopsBeforeOverwritingAnything() = runTest {
        val fake = FakeTv.googleTv()
        val shell = object : TvShell by fake {
            override suspend fun run(command: String): String =
                if (command == Commands.lookReadConfig("com.gentv.tv")) "INPUT_CONFIG_READ_FAILED" else fake.run(command)
        }
        assertTrue(runCatching { LauncherLook(shell).apply(LookFiles("default", "{}")) }.isFailure)
        assertTrue(fake.pushed.isEmpty())
    }

    @Test fun providerReadPreservesRoutesWhenAndroidDataIsNotReadableByShell() = runTest {
        val fake = FakeTv.googleTv()
        val bytes = """{"source_panel_service":"vendor/.Inputs","input_uri_scheme":"live-tv"}""".toByteArray()
        val shell = object : TvShell by fake {
            override suspend fun run(command: String): String =
                if (command == Commands.lookReadConfig("com.gentv.tv"))
                    "Result: Bundle[{config_base64=${java.util.Base64.getEncoder().encodeToString(fake.pushed["$dir/config.json"] ?: bytes)}}]"
                else fake.run(command)
        }
        assertTrue(LauncherLook(shell).apply(LookFiles("default", "{}")))
        assertEquals("vendor/.Inputs", config(fake)["source_panel_service"]!!.jsonPrimitive.content)
        assertEquals("live-tv", config(fake)["input_uri_scheme"]!!.jsonPrimitive.content)
    }

}
