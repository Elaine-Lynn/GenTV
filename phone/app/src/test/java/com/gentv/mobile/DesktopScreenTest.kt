package com.gentv.mobile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.gentv.mobile.data.DesktopState
import com.gentv.mobile.ui.GenTVTheme
import com.gentv.mobile.ui.screens.DesktopScreen
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DesktopScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = DesktopState(snapshot = """{"protocol":1,"revision":0,"config":{},"smooth":true,"apps":[{"id":"com.example.cinema","label":"Cinema"},{"id":"com.example.music","label":"Music"}],"sources":[]}""")

    @Test fun moveEditsDraftAndSaveSubmitsTheNewOrder() {
        var saved: String? = null
        compose.setContent { GenTVTheme { DesktopScreen(state, {}, {}, { saved = it }, {}, {}, { _, _ -> }) } }
        compose.onNodeWithText("Music").performScrollTo().performClick()
        compose.onNodeWithText("Move earlier").performScrollTo().performClick()
        assertNull("Moving should not write to the TV", saved)
        compose.onNodeWithText("Save and verify").performClick()
        compose.waitForIdle()
        assertEquals("com.example.music", JSONObject(saved!!).getJSONArray("order").getString(0))
    }
    @Test fun renderDesktopManagement() {
        RuntimeEnvironment.setQualifiers("w412dp-h892dp-xxhdpi")
        captureRoboImage(java.io.File("build/screenshots/gentv-desktop-management.png").absolutePath) {
            GenTVTheme { DesktopScreen(state, {}, {}, {}, {}, {}, { _, _ -> }) }
        }
    }
    @Test fun newDesktopSaveKeepsComponentSchemaAndDoesNotAddAppearance() {
        val modern = DesktopState(snapshot = """{"protocol":1,"revision":3,"config":{"schema":2,"order":["com.example.music/.MainActivity"],"hidden":[],"names":{},"effects":"auto","pinnedSources":[],"sourceNames":{},"sourceDevices":{},"sourceTargets":{}},"apps":[{"id":"com.example.music/.MainActivity","label":"Music"}],"sources":[]}""")
        var saved: String? = null
        compose.setContent { GenTVTheme { DesktopScreen(modern, {}, {}, { saved = it }, {}, {}, { _, _ -> }) } }
        compose.onNodeWithText("Save and verify").performClick()
        compose.waitForIdle()
        val profile=JSONObject(saved!!)
        assertEquals(2,profile.getInt("schema"))
        assertEquals("com.example.music/.MainActivity",profile.getJSONArray("order").getString(0))
        assertFalse(profile.has("appearance"))
        com.gentv.mobile.data.parseDesktopProfile(profile.toString())
    }

    @Test fun folderCreationAndDissolveStayInDraftUntilSave() {
        val modern = DesktopState(snapshot = """{"protocol":1,"revision":3,"capabilities":["folders","images"],"config":{"schema":2,"order":["com.example.music/.MainActivity"],"hidden":[],"names":{},"effects":"auto","folders":{},"images":{}},"apps":[{"id":"com.example.music/.MainActivity","label":"Music"}],"sources":[]}""")
        var saved: String? = null
        compose.setContent { GenTVTheme { DesktopScreen(modern, {}, {}, { saved = it }, {}, {}, { _, _ -> }) } }
        compose.onNodeWithText("New folder").performScrollTo().performClick()
        compose.onNodeWithText("Folder name").performTextInput("Video")
        compose.onNodeWithText("Create").performClick()
        assertNull(saved)
        compose.onNodeWithText("Save and verify").performClick()
        val profile=JSONObject(saved!!)
        assertEquals(1,profile.getJSONObject("folders").length())
        assertEquals("Video",profile.getJSONObject("folders").getJSONObject(profile.getJSONObject("folders").keys().next()).getString("name"))
        compose.onNodeWithText("Dissolve folder; keep apps").performScrollTo().performClick()
        compose.onNodeWithText("Save and verify").performClick()
        assertEquals(0,JSONObject(saved!!).getJSONObject("folders").length())
        assertEquals("com.example.music/.MainActivity",JSONObject(saved!!).getJSONArray("order").getString(0))
    }

}
