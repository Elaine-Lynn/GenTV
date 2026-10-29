package com.gentv.mobile

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gentv.mobile.data.UpdateStatus
import com.gentv.mobile.ui.*
import com.gentv.mobile.ui.screens.SettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:org.junit.Rule val configuredApi = ConfiguredApi()
    @get:Rule val compose = createComposeRule()
    @Test fun languageChangesImmediatelyAndSettingsCanGoBack() {
        var selected = AppLanguage.ENGLISH
        var back = false
        compose.setContent {
            var language by remember { mutableStateOf(AppLanguage.ENGLISH) }
            CompositionLocalProvider(LocalAppLanguage provides language, LocalChangeLanguage provides { selected = it; language = it }) {
                GenTVTheme { SettingsScreen(UpdateStatus.Idle, { back = true }, {}, {}) }
            }
        }
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("简体中文").performClick()
        assertEquals(AppLanguage.CHINESE, selected)
        compose.onNodeWithText("设置").assertExists()
        compose.onNodeWithText("检查更新").assertExists()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Back").performClick()
        assertTrue(back)
    }
    @Test fun unconfiguredUpdateServiceOffersOfficialWebsite() {
        var website: String? = null
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Unconfigured, {}, {}, { website = it }) } }
        compose.onNodeWithText("Automatic update service is not configured yet. Visit the official website for downloads.").performScrollTo().assertExists()
        compose.onNodeWithText("Official website").performScrollTo().performClick()
        assertEquals(BuildConfig.DOWNLOAD_URL, website)
    }
    @Test fun checkingDisablesDuplicateRequests() {
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Checking, {}, {}, {}) } }
        compose.onNodeWithText("Checking for updates…").performScrollTo().assertIsNotEnabled()
    }
    @Test fun feedbackOpensFromSettings() {
        var opened = false
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Idle, {}, {}, {}, onFeedback = { opened = true }) } }
        compose.onNodeWithText("Feedback").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun analyticsIsOffByDefaultAndCanBeToggled() {
        var enabled = false
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Idle, {}, {}, {}, onAnalyticsChanged = { enabled = it }, analyticsAvailable = true) } }
        compose.onNode(isToggleable()).performScrollTo().assertIsOff().performClick().assertIsOn()
        assertTrue(enabled)
        compose.onNode(isToggleable()).performClick().assertIsOff()
        assertFalse(enabled)
    }

    @Test fun buildsWithoutAnApiOfferNoStatistics() {
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Idle, {}, {}, {}, analyticsAvailable = false) } }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test fun verifiedMetadataStartsDownloadInsteadOfOpeningWebsite() {
        val info = com.gentv.mobile.data.UpdateInfo("1.0.0", "https://api.gentv.app/tv/app.apk", 12, "a".repeat(64), 100)
        var downloaded = false
        var website = false
        compose.setContent { GenTVTheme { SettingsScreen(UpdateStatus.Available(info), {}, {}, { website = true }, onDownloadUpdate = { downloaded = it == info }) } }
        compose.onNodeWithText("Download update").performScrollTo().performClick()
        assertTrue(downloaded); assertFalse(website)
    }

}
