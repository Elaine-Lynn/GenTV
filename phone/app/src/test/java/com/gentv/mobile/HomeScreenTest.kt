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

    @Test fun renderDarkHomepage() {
        RuntimeEnvironment.setQualifiers("w412dp-h1000dp-xxhdpi")
        val out = File("build/screenshots/gentv-dark-home.png").apply { parentFile.mkdirs() }
        captureRoboImage(out.absolutePath) {
            GenTVTheme { HubScreen(tv, 3, false, {}, {}, {}, {}, {}, {}) }
        }
    }
}
