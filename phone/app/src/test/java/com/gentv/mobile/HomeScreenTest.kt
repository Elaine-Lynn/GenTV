package com.gentv.mobile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.gentv.mobile.ui.GenTVTheme
import com.gentv.mobile.ui.screens.HubScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val tv = TvSummary("客厅 TV", "SONY BRAVIA XR", "192.168.1.50", "fixture", 34, "14")

    @Test fun navigationKeepsTheOriginalActions() {
        var chosen = ""
        compose.setContent {
            GenTVTheme {
                HubScreen(tv, 3, false, { chosen = "tidy" }, { chosen = "remote" },
                    { chosen = "files" }, { chosen = "home" }, { chosen = "restore" }, { chosen = "devices" },
                    onSettings = { chosen = "settings" }, onCheck = { chosen = "check" }, onApps = { chosen = "apps" }, onDesktop = { chosen = "desktop" })
            }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        assertEquals("settings", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("切换设备", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        assertEquals("devices", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("已连接", com.gentv.mobile.ui.AppLanguage.ENGLISH)).assertDoesNotExist()
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("Android 14", com.gentv.mobile.ui.AppLanguage.ENGLISH)).assertDoesNotExist()
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("触控遥控中心", com.gentv.mobile.ui.AppLanguage.ENGLISH)).assertDoesNotExist()
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("遥控器", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        assertEquals("remote", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("文件快传", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals("files", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("桌面整理", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals("desktop", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("电视诊断", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals("check", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("配置还原", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals("restore", chosen)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("发现设备", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        assertEquals("devices", chosen)
    }

    @Test fun desktopOfferHasOneInstallActionWithoutLauncherSelection() {
        var installed = false
        compose.setContent {
            GenTVTheme {
                com.gentv.mobile.ui.screens.HomeScreenStep(
                    HomeState.Offer(), 0, {}, { error("No launcher choice should be shown") },
                    { installed = true }, {}, { _, _ -> }, {}, {},
                )
            }
        }
        compose.onNodeWithText("GenTV desktop").assertExists()
        compose.onNodeWithContentDescription("GenTV desktop preview").assertExists()
        compose.onNodeWithText("Simple").assertDoesNotExist()
        compose.onNodeWithText("compatible desktop").assertDoesNotExist()
        compose.onNodeWithText("Projectivy").assertDoesNotExist()
        compose.onNodeWithText("Install GenTV desktop").performClick()
        assertEquals(true, installed)
    }

    @Test fun fullInstallAsksBeforeHomeAndPreviewStaysAChoice() {
        var state by androidx.compose.runtime.mutableStateOf<HomeState>(HomeState.Offer())
        val calls = mutableListOf<String>()
        compose.setContent {
            GenTVTheme {
                com.gentv.mobile.ui.screens.HomeScreenStep(
                    state, 0, {}, {}, { calls += "install" }, {}, { a, _ -> calls += a.name }, { calls += "continue" }, {},
                    onPreview = { calls += "preview" }, onMakeHome = { calls += "home" },
                )
            }
        }
        compose.onNodeWithText("Just preview it, keep my home screen").performClick()
        state = HomeState.GenTVResult(true)
        compose.waitForIdle()
        compose.onNodeWithText("Is GenTV on your TV?").assertExists()
        compose.onNodeWithText("Not now, keep my home screen").performClick()
        compose.onNodeWithText("Yes, make GenTV my home screen").performClick()
        state = HomeState.GenTVResult(true, previewOnly = true)
        compose.waitForIdle()
        compose.onNodeWithText("Yes, make GenTV my home screen").assertDoesNotExist()
        compose.onNodeWithText("GenTV is open on your TV. Your home screen wasn't changed.").assertExists()
        state = HomeState.Problem("Firmware kept Home.", worried = false, action = HomeAction.COMPAT_HANDOVER, installed = true)
        compose.waitForIdle()
        compose.onNodeWithText("GenTV installed, Home not switched").assertExists()
        compose.onNodeWithText("Turn off the old home screen and try").performClick()
        state = HomeState.Problem("Restart first.", worried = false, action = HomeAction.CONFIRM_REBOOT, installed = true)
        compose.waitForIdle()
        compose.onNodeWithText("I restarted the TV, continue").performClick()
        assertEquals(listOf("preview", "continue", "home", "COMPAT_HANDOVER", "CONFIRM_REBOOT"), calls)
    }

    @Test fun handoverCopyHasChineseTranslations() {
        val zh = com.gentv.mobile.ui.AppLanguage.CHINESE
        for (en in listOf(
            "Just preview it, keep my home screen", "Is GenTV on your TV?", "Yes, make GenTV my home screen",
            "Not now, keep my home screen", "GenTV installed, Home not switched", "Turn off the old home screen and try",
            "I restarted the TV, continue",
        )) assertTrue(en, com.gentv.mobile.ui.AppCopy.translate(en, zh) != en)
        assertEquals("已停用（未卸载）：Google TV。恢复所有更改会重新启用它们，并把原桌面设回默认桌面。",
            com.gentv.mobile.ui.AppCopy.translate("Turned off, never uninstalled: Google TV. Restore everything turns them back on and makes your original home screen Home again.", zh))
    }

    @Test fun renderDarkHomepage() {
        RuntimeEnvironment.setQualifiers("w412dp-h1000dp-xxhdpi")
        val out = File("build/screenshots/gentv-dark-home.png").apply { parentFile.mkdirs() }
        captureRoboImage(out.absolutePath) {
            GenTVTheme { HubScreen(tv, 3, false, {}, {}, {}, {}, {}, {}) }
        }
    }
}
