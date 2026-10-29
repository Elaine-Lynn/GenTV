package com.gentv.mobile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.gentv.mobile.ui.GenTVTheme
import com.gentv.mobile.ui.screens.DiscoveryScreen
import com.gentv.mobile.ui.screens.RemoteScreen
import com.gentv.mobile.ui.screens.SendFilesScreen
import com.gentv.core.flow.ConnectEvent
import com.gentv.core.flow.ConnectState
import com.gentv.core.flow.FoundTv
import com.gentv.core.ops.RemoteKey
import org.junit.Assert.*
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
class CompanionPagesTest {
    @get:Rule val compose = createComposeRule()
    private val tv = TvSummary("客厅 TV", "SONY BRAVIA XR", "192.168.1.50", "fixture", 34, "14")
    private val other = FoundTv("192.168.1.51", name = "书房 小米电视", legacyOpen = true)
    private val scan = ConnectState.Scanning(listOf(other, FoundTv("192.168.1.52", name = "卧室 Android TV", tlsPort = 37155)), done = true)
    private val remote = RemoteState(apps = listOf(
        RemoteApp("com.netflix.ninja", "com.netflix.ninja/.Main", "Netflix"),
        RemoteApp("com.google.android.youtube.tv", "com.google.android.youtube.tv/.Main", "YouTube"),
        RemoteApp("org.xbmc.kodi", "org.xbmc.kodi/.Main", "Kodi"),
        RemoteApp("com.plexapp.android", "com.plexapp.android/.Main", "Plex"),
        RemoteApp("com.android.tv.settings", "com.android.tv.settings/.Main", "设置"),
        RemoteApp("com.gentv.tv", "com.gentv.tv/.MainActivity", "GenTV"),
    ))

    @Test fun discoveryUsesRealPickAndValidatedManualInput() {
        var event: ConnectEvent? = null
        compose.setContent { GenTVTheme { DiscoveryScreen(scan, tv, { event = it }, {}) } }
        compose.onNodeWithText(other.title).performScrollTo().performClick()
        assertEquals(ConnectEvent.Pick(other), event)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("重新检测", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals(ConnectEvent.StartScan, event)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("手动通过 IP 地址直连", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("立即连接", com.gentv.mobile.ui.AppLanguage.ENGLISH)).assertIsNotEnabled()
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("电视 IP 地址", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performTextInput("192.168.1.60")
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("立即连接", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        assertEquals(ConnectEvent.TypedIp("192.168.1.60"), event)
    }

    @Test fun remoteKeepsRealHardwareCallbacks() {
        val keys = mutableListOf<RemoteKey>()
        compose.setContent { GenTVTheme { RemoteScreen(remote, tv.title, { keys += it }, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Power").performClick()
        assertEquals(RemoteKey.POWER, keys.last())
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("方向按键", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        compose.onNodeWithContentDescription("Up").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(RemoteKey.UP, keys.last())
        compose.onNodeWithContentDescription("Volume up").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(RemoteKey.VOLUME_UP, keys.last())
    }

    @Test fun transferKeepsLaunchCancelAndRetryCallbacks() {
        var launched: RemoteApp? = null
        var cancelled = -1
        var retried = -1
        val files = FilesState(items = listOf(SendItem(7, "photo.jpg", 1024, status = SendStatus.WAITING), SendItem(8, "video.mp4", 2048, status = SendStatus.FAILED)))
        compose.setContent { GenTVTheme { SendFilesScreen(files, tv.title, {}, { cancelled = it }, { retried = it }, {}, remote = remote, initialApps = true, onLaunch = { launched = it }) } }
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("Netflix", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performClick()
        assertEquals(remote.apps.first(), launched)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("取消", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals(7, cancelled)
        compose.onNodeWithText(com.gentv.mobile.ui.AppCopy.translate("重试", com.gentv.mobile.ui.AppLanguage.ENGLISH)).performScrollTo().performClick()
        assertEquals(8, retried)
    }

    @Test fun renderDiscoveryPage() = render("discovery") { DiscoveryScreen(scan, tv, {}, {}) }
    @Test fun renderRemotePage() = render("remote") { RemoteScreen(remote, tv.title, {}, {}, {}, {}, {}) }
    @Test fun renderAppsPage() = render("apps") { SendFilesScreen(FilesState(freeBytes = 8L * 1024 * 1024 * 1024), tv.title, {}, {}, {}, {}, remote = remote, initialApps = true) }
    @Test fun renderTransferPage() = render("transfer") {
        SendFilesScreen(FilesState(items = listOf(SendItem(1, "living-room-video.mp4", 1024L * 1024 * 1024, 320L * 1024 * 1024, SendStatus.SENDING), SendItem(2, "photo.jpg", 1024 * 500, status = SendStatus.SENT)), freeBytes = 8L * 1024 * 1024 * 1024), tv.title, {}, {}, {}, {}, remote = remote)
    }
    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("w412dp-h1000dp-xxhdpi")
        val out = File("build/screenshots/gentv-$name.png").apply { parentFile.mkdirs() }
        captureRoboImage(out.absolutePath) { CompositionLocalProvider(LocalInspectionMode provides true) { GenTVTheme { content() } } }
    }
}
