package com.gentv.mobile

import com.gentv.core.flow.ConnectEvent
import com.gentv.core.flow.ConnectState
import com.gentv.core.flow.Step
import com.gentv.core.lists.HomeApp
import com.gentv.core.lists.Launchers
import com.gentv.core.lists.ListCatalog
import com.gentv.core.ops.Commands
import com.gentv.core.ops.originalHome
import com.gentv.core.testing.FakeTv
import com.gentv.mobile.data.DesktopPreviewApk
import com.gentv.mobile.data.Store
import com.gentv.mobile.data.Trace
import com.gentv.mobile.net.DemoBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Install GenTV desktop, confirm it on the TV, then the staged Home handover, against the simulated TV. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeHandoverFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dir: File
    private val launcherx = Launchers.LAUNCHERX + "/.MainActivity"
    private val apk = "PK desktop fixture".toByteArray().let { bytes ->
        DesktopPreviewApk(bytes, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dir = Files.createTempDirectory("gentv-home").toFile()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private object NoNetwork : com.gentv.mobile.net.Backend {
        override fun scan() = kotlinx.coroutines.flow.emptyFlow<com.gentv.core.flow.FoundTv>()
        override suspend fun connect(tv: com.gentv.core.flow.FoundTv, onAwaitingAllow: () -> Unit) = com.gentv.mobile.net.Link(com.gentv.core.flow.Attempt.UNREACHABLE)
        override suspend fun pair(tv: com.gentv.core.flow.FoundTv, code: String) = com.gentv.mobile.net.Link(com.gentv.core.flow.Attempt.FAILED)
        override suspend fun portOpen(host: String) = false
        override val saveWaitMs = 0L
    }

    private fun vm(demo: DemoBackend) = WizardViewModel(
        Store(dir), Trace(File(dir, "logs")), { NoNetwork }, demo, { ListCatalog(emptyMap()) }, { it.pkg.toByteArray() },
        imageWork = dispatcher, desktopPreviewApk = { apk },
    )

    /** Connects to the demo TV and opens the GenTV desktop page, then installs (a full install unless [preview]). */
    private fun TestScope.install(vm: WizardViewModel, preview: Boolean = false) {
        vm.choose(com.gentv.core.flow.TvKind.ANDROID_TV)
        vm.ready()
        advanceTimeBy(4_000)
        assertTrue((vm.ui.value.connect as ConnectState.Scanning).found.contains(DemoBackend.DEMO))
        vm.connect(ConnectEvent.Pick(DemoBackend.DEMO))
        advanceUntilIdle()
        vm.openHomeScreen(); advanceUntilIdle()
        assertEquals(Step.HOME_SCREEN, vm.ui.value.wizard.step)
        vm.installGenTVHome(previewOnly = preview); advanceUntilIdle()
        assertEquals(HomeState.GenTVResult(true, previewOnly = preview), vm.ui.value.home)
    }

    private fun FakeTv.neverUninstalled() = assertTrue(commands.none { it.startsWith("pm uninstall") })

    @Test fun homeOnlyMovesAfterTheOwnerConfirmsAndTheOriginalLauncherStaysOn() = runTest(dispatcher) {
        val demo = DemoBackend(FakeTv.googleTv())
        val tv = demo.tv
        val vm = vm(demo)
        install(vm)
        assertEquals("installing alone leaves Home", launcherx, tv.home())
        assertTrue(tv.commands.none { it.startsWith("cmd package set-home-activity") })

        vm.makeGenTVHome(); advanceUntilIdle()
        val done = vm.ui.value.home as HomeState.Installed
        assertTrue(done.note!!, done.note!!.contains("still installed and turned on"))
        assertEquals(HomeApp.GENTV.component, tv.home())
        assertTrue("first stage turns nothing off", tv.disabled.isEmpty())

        vm.homeContinue(); advanceUntilIdle()
        assertEquals(launcherx, vm.ui.value.done.records.originalHome())
        vm.restoreEverything(); advanceUntilIdle()
        assertEquals(launcherx, tv.home())
        assertTrue("GenTV stays installed", HomeApp.GENTV.pkg in tv.installed)
        tv.neverUninstalled()
    }

    @Test fun firmwareLimitIsItsOwnResultThenTheCompatibilityStepTurnsOffOnlyHomeBlockers() = runTest(dispatcher) {
        val demo = DemoBackend() // TCL G06: the highest-priority enabled launcher always owns Home
        val tv = demo.tv
        val vm = vm(demo)
        install(vm)
        vm.makeGenTVHome(); advanceUntilIdle()
        val limit = vm.ui.value.home as HomeState.Problem
        assertTrue("install success stays separate from Home", limit.installed)
        assertEquals(HomeAction.COMPAT_HANDOVER, limit.action)
        assertTrue(limit.message, limit.message.contains("GenTV works"))
        assertEquals(launcherx, tv.home())
        assertTrue(tv.disabled.isEmpty())

        vm.homeAction(HomeAction.COMPAT_HANDOVER); advanceUntilIdle()
        val done = vm.ui.value.home as HomeState.Installed
        assertTrue(done.note!!, done.note!!.startsWith("Turned off, never uninstalled"))
        assertEquals(setOf(Launchers.LAUNCHERX, Launchers.SETUPWRAITH), tv.disabled)
        assertEquals(HomeApp.GENTV.component, tv.home())

        vm.homeContinue(); advanceUntilIdle()
        vm.restoreEverything(); advanceUntilIdle()
        assertTrue(tv.disabled.isEmpty())
        assertEquals(launcherx, tv.home())
        tv.neverUninstalled()
    }

    @Test fun previewOnlyKeepsHome() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        install(vm, preview = true)
        vm.makeGenTVHome(); advanceUntilIdle()
        assertEquals(HomeState.GenTVResult(true, previewOnly = true), vm.ui.value.home)
        assertTrue(demo.tv.commands.none { it.startsWith("cmd package set-home-activity") || it.startsWith("pm disable") })
        assertEquals(launcherx, demo.tv.home())
    }

    @Test fun cvteRestartsOnceBeforeItsLauncherIsTurnedOff() = runTest(dispatcher) {
        val cvte = Launchers.CVTE_LAUNCHER
        val demo = DemoBackend(FakeTv.tclG06().apply {
            homeClaimants.remove(launcherx)
            installed += cvte
            homeClaimants["$cvte/.LauncherActivity"] = 3
        })
        val tv = demo.tv
        val vm = vm(demo)
        install(vm)
        vm.makeGenTVHome(); advanceUntilIdle()
        vm.homeAction(HomeAction.COMPAT_HANDOVER); advanceUntilIdle()
        val checkpoint = vm.ui.value.home as HomeState.Problem
        assertEquals(HomeAction.CONFIRM_REBOOT, checkpoint.action)
        assertTrue(checkpoint.message, checkpoint.message.contains("CVTE"))
        assertTrue(tv.disabled.isEmpty())

        // The owner restarts the TV; the phone's old link is gone and reconnects.
        tv.dropOnPrefix = Commands.RESOLVE_HOME
        vm.homeAction(HomeAction.CONFIRM_REBOOT); advanceUntilIdle()
        assertTrue("${vm.ui.value.home}", vm.ui.value.home is HomeState.Installed)
        assertTrue(cvte in tv.disabled)
        assertEquals(HomeApp.GENTV.component, tv.home())
        tv.neverUninstalled()
    }

    @Test fun setupAppOwningTheWifiNeedsARejoinOrAVerifiedCable() = runTest(dispatcher) {
        val demo = DemoBackend(FakeTv.tclG06().apply { wifiOwner = "com.google.android.tungsten.setupwraith:10130" })
        val tv = demo.tv
        val vm = vm(demo)
        install(vm)
        vm.makeGenTVHome(); advanceUntilIdle()
        vm.homeAction(HomeAction.COMPAT_HANDOVER); advanceUntilIdle()
        var problem = vm.ui.value.home as HomeState.Problem
        assertEquals(HomeAction.TURN_OFF_SETUP_APP, problem.action)
        assertTrue(problem.message, problem.message.contains("forget this Wi-Fi network"))

        vm.homeAction(HomeAction.TURN_OFF_SETUP_APP, onEthernet = true); advanceUntilIdle()
        problem = vm.ui.value.home as HomeState.Problem
        assertEquals("the TV isn't on a cable, so ticking the box isn't enough", HomeAction.TURN_OFF_SETUP_APP, problem.action)
        assertTrue(tv.disabled.isEmpty())

        tv.ethernet = true
        vm.homeAction(HomeAction.TURN_OFF_SETUP_APP, onEthernet = true); advanceUntilIdle()
        assertTrue("${vm.ui.value.home}", vm.ui.value.home is HomeState.Installed)
    }
}
