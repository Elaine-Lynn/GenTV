package com.gentv.core.ops

import com.gentv.core.lists.HomeApp
import com.gentv.core.lists.Launchers
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStagesTest {
    private val launcherx = Launchers.LAUNCHERX + "/.MainActivity"
    private val cvte = Launchers.CVTE_LAUNCHER

    /** GenTV as the phone app leaves it after a confirmed install: on the TV, answering HOME, not yet Home. */
    private fun FakeTv.withGenTV() = apply {
        installed += HomeApp.GENTV.pkg
        homeClaimants[HomeApp.GENTV.component] = 0
    }

    private fun stages(tv: FakeTv, log: ActionLog, sdk: Int = 30) = HomeStages(tv, log, sdk, clock = { 5L }, sleep = {}, settle = {})

    private fun FakeTv.neverUninstalled() = assertTrue(commands.none { it.startsWith("pm uninstall") })

    @Test fun firstStageKeepsTheOriginalLauncherOnAndRestorePutsItBack() = runTest {
        val tv = FakeTv.googleTv().withGenTV()
        val log = MemoryActionLog()
        assertEquals(HandoverOutcome.Done(null), stages(tv, log).keepStock())
        assertEquals(HomeApp.GENTV.component, tv.home())
        assertTrue("nothing turned off", tv.disabled.isEmpty())
        assertTrue("GenTV was already installed", tv.commands.none { it == Commands.INSTALL_TMP })
        assertEquals(launcherx, log.records().originalHome())

        val restored = Restorer(tv, log, clock = { 6L }).run()
        assertEquals(RestoreOutcome.Done(emptyList(), homeRestored = true), restored)
        assertEquals(launcherx, tv.home())
        assertTrue("restore keeps GenTV installed", HomeApp.GENTV.pkg in tv.installed)
        tv.neverUninstalled()
    }

    @Test fun firmwareThatKeepsHomeGetsTheCompatibilityStepThenAnExactRestore() = runTest {
        val tv = FakeTv.tclG06().withGenTV()
        val log = MemoryActionLog()
        val first = stages(tv, log).keepStock()
        assertTrue("$first", first is HandoverOutcome.RolledBack && first.homeNow == launcherx)
        assertTrue("the first stage turns nothing off", tv.disabled.isEmpty())

        assertEquals(HandoverOutcome.Done(Launchers.LAUNCHERX), stages(tv, log).compatibility(rebootConfirmed = false, cableClaimed = false))
        assertEquals(HomeApp.GENTV.component, tv.home())
        assertEquals(setOf(Launchers.LAUNCHERX, Launchers.SETUPWRAITH), tv.disabled)
        assertEquals(launcherx, log.records().originalHome())

        val restored = Restorer(tv, log, clock = { 6L }).run()
        assertTrue("$restored", restored is RestoreOutcome.Done && restored.homeRestored)
        assertTrue(tv.disabled.isEmpty())
        assertEquals(launcherx, tv.home())
        assertTrue(HomeApp.GENTV.pkg in tv.installed)
        tv.neverUninstalled()
    }

    @Test fun cvteWaitsForARestartBeforeAnythingIsTurnedOff() = runTest {
        val tv = FakeTv.tclG06().withGenTV().apply {
            homeClaimants.remove(launcherx)
            installed += cvte
            homeClaimants["$cvte/.LauncherActivity"] = 3
        }
        val log = MemoryActionLog()
        assertTrue(stages(tv, log).keepStock() is HandoverOutcome.RolledBack)
        assertEquals(HandoverOutcome.RebootCheckpoint, stages(tv, log).compatibility(rebootConfirmed = false, cableClaimed = false))
        assertTrue(tv.disabled.isEmpty())

        assertEquals(HandoverOutcome.Done(cvte), stages(tv, log).compatibility(rebootConfirmed = true, cableClaimed = false))
        assertTrue(cvte in tv.disabled && cvte in log.records().disabledByUs())
        assertEquals("$cvte/.LauncherActivity", log.records().originalHome())
        tv.neverUninstalled()
    }

    @Test fun setupAppOwningTheWifiBlocksUntilACableIsVerifiedOnTheTv() = runTest {
        val tv = FakeTv.tclG06().withGenTV().apply { wifiOwner = "com.google.android.tungsten.setupwraith:10130" }
        val log = MemoryActionLog()
        assertEquals(HandoverOutcome.SetupAppBlocked(WraithCheck.OwnsWifi), stages(tv, log).compatibility(false, cableClaimed = false))
        // Saying "it's on a cable" isn't proof: the TV's default network is still Wi-Fi.
        assertEquals(HandoverOutcome.SetupAppBlocked(WraithCheck.OwnsWifi), stages(tv, log).compatibility(false, cableClaimed = true))
        assertTrue(tv.disabled.isEmpty())

        tv.ethernet = true
        assertEquals(HandoverOutcome.Done(Launchers.LAUNCHERX), stages(tv, log).compatibility(false, cableClaimed = true))
    }

    @Test fun unreadableWifiOwnerFailsClosed() = runTest {
        val tv = FakeTv.tclG06().withGenTV().apply { wifiOwner = "" }
        val out = stages(tv, MemoryActionLog()).compatibility(false, cableClaimed = false)
        assertTrue("$out", out is HandoverOutcome.SetupAppBlocked && out.check is WraithCheck.Unknown)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun coreServicesAndFactoryAppsAreNeverTreatedAsTheHomeToTurnOff() = runTest {
        for (owner in listOf("com.android.tv.settings/.FallbackHome", "com.vendor.factorytest/.Home")) {
            val tv = FakeTv.googleTv().withGenTV().apply {
                installed += owner.substringBefore('/')
                homeClaimants[owner] = 5
                preferredHome = owner
            }
            val stages = stages(tv, MemoryActionLog())
            assertEquals(null, stages.identifyHome())
            assertEquals(HandoverOutcome.UnknownHome(owner.substringBefore('/')), stages.compatibility(rebootConfirmed = true, cableClaimed = false))
            assertTrue(owner, tv.disabled.isEmpty())
            assertFalse(tv.commands.any { it.startsWith("cmd package set-home-activity") })
        }
    }

    @Test fun vendorLauncherThatAnswersHomeIsIdentified() = runTest {
        val tv = FakeTv.googleTv().withGenTV().apply {
            installed += "com.vendor.tvhome"
            homeClaimants["com.vendor.tvhome/.Main"] = 3
            preferredHome = "com.vendor.tvhome/.Main"
        }
        val log = MemoryActionLog()
        assertEquals("com.vendor.tvhome", stages(tv, log).identifyHome())
        assertEquals(HandoverOutcome.Done(null), stages(tv, log).keepStock())
        assertTrue("com.vendor.tvhome" !in tv.disabled)
        assertEquals("com.vendor.tvhome/.Main", log.records().originalHome())
    }

    @Test fun droppedLinkIsReportedNotThrown() = runTest {
        val tv = FakeTv.tclG06().withGenTV().apply { dropOnPrefix = Commands.RESOLVE_HOME }
        assertEquals(HandoverOutcome.Disconnected, stages(tv, MemoryActionLog()).compatibility(false, false))
    }
}
