package com.gentv.care

import com.gentv.core.lists.*
import com.gentv.core.ops.*
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CareTest {
    private val repo = File(System.getProperty("gentv.repo"))
    private fun fixture(): Pair<FakeTv, TvShell> {
        val fake = FakeTv.googleTv().apply {
            props["ro.build.fingerprint"] = "fixture/device/build"
            props["ro.serialno"] = "fixture-serial"
        }
        val shell = object : TvShell by fake {
            override suspend fun run(command: String): String = when {
                command.startsWith("settings list ") -> "fixture=1\n" + fake.run(command)
                command.startsWith("dumpsys package ") -> "android.intent.category.HOME\nUser 0: installed=true hidden=false enabled=0\n"
                command.startsWith("pm default-state --user 0 ") -> fake.run(Commands.enable(command.substringAfterLast(' ')))
                else -> fake.run(command)
            }
        }
        return fake to shell
    }
    private suspend fun session(tv: TvShell): Session {
        val parent = Files.createTempDirectory("gentv-care-test-").toFile()
        parent.deleteOnExit()
        return Session(File(parent, "session")).also { it.create(tv) }
    }
    @Test fun invalidPackageAndMiddlewareNeverReachCleanup() {
        assertNotNull(desktopProtection("com.example.ads;reboot", emptySet(), emptySet(), emptySet()))
        for (pkg in listOf("com.cltv.mal", "com.mediatek.tis", "android.ext.services", "com.android.phone", "com.gentv.tv"))
            assertNotNull(pkg, desktopProtection(pkg, emptySet(), emptySet(), emptySet()))
        assertNull(desktopProtection("com.example.ads", emptySet(), emptySet(), emptySet()))
    }
    @Test fun sessionCannotMoveToAnotherPhysicalDevice() = runTest {
        val (fake, tv) = fixture()
        val session = session(tv)
        fake.props["ro.serialno"] = "another-device"
        try { session.verify(tv); fail("wrong device accepted") } catch (_: IllegalArgumentException) { }
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun backupCannotOverwriteAnExistingSession() = runTest {
        val (_, tv) = fixture(); val session = session(tv)
        try { session.create(tv); fail("baseline overwritten") } catch (_: IllegalArgumentException) { }
    }
    @Test fun cleanupPlanPreservesPreDisabledAndDynamicHome() = runTest {
        val (fake, tv) = fixture()
        fake.installed += listOf("com.example.ads", "com.example.old", "com.example.home")
        fake.disabled += "com.example.old"
        fake.homeClaimants["com.example.home/.Home"] = 0
        val session = session(tv)
        val plan = Care(tv, session, repo, settle = {}).plan(listOf("com.example.ads", "com.example.old", "com.example.home"), emptySet(), null)
        assertEquals(listOf("com.example.ads"), plan.allowed)
        assertTrue(plan.skipped.keys.containsAll(listOf("com.example.old", "com.example.home")))
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun restorePendingIntentOnlyAndPreserveOtherSessionMirrorAndGenTV() = runTest {
        val (fake, tv) = fixture()
        fake.installed += listOf("com.example.ads", "com.example.other", HomeApp.GENTV.pkg)
        val session = session(tv)
        session.rememberStates(tv, listOf("com.example.ads"))
        session.log.append(ActionRecord(1, Op.DISABLE, "com.example.ads", Phase.INTENT))
        session.log.append(ActionRecord(2, Op.INSTALL, HomeApp.GENTV.pkg, Phase.DONE))
        fake.disabled += listOf("com.example.ads", "com.example.other")
        fake.files[Commands.MIRROR_FILE] = "com.example.ads\ncom.example.other\n"
        val outcome = Care(tv, session, repo, settle = {}).restore()
        assertTrue(outcome is RestoreOutcome.Done)
        assertFalse("com.example.ads" in fake.disabled)
        assertTrue("com.example.other" in fake.disabled)
        assertTrue(HomeApp.GENTV.pkg in fake.installed)
        assertFalse(fake.commands.any { it.startsWith("pm uninstall") })
        assertTrue(fake.files[Commands.MIRROR_FILE].orEmpty().contains("com.example.other"))
    }
    @Test fun restoreDoesNotUninstallGenTVAfterHomeRecovery() = runTest {
        val (fake, tv) = fixture()
        val original = fake.home()!!
        val session = session(tv)
        fake.installed += HomeApp.GENTV.pkg
        fake.homeClaimants[HomeApp.GENTV.component] = 0
        fake.preferredHome = HomeApp.GENTV.component
        session.log.append(ActionRecord(1, Op.INSTALL, HomeApp.GENTV.pkg, Phase.DONE))
        session.log.append(ActionRecord(2, Op.SET_HOME, HomeApp.GENTV.component, Phase.DONE, before = original))
        val outcome = Care(tv, session, repo, settle = {}).restore()
        assertTrue(outcome is RestoreOutcome.Done && outcome.homeRestored)
        assertTrue(HomeApp.GENTV.pkg in fake.installed)
        assertEquals(original, fake.home())
    }
    @Test fun normalHomeFailureRollsBackWithoutDisablingAnything() = runTest {
        val (fake, tv) = fixture()
        val original = fake.home()
        val session = session(tv)
        fake.installed += HomeApp.GENTV.pkg
        fake.homeClaimants[HomeApp.GENTV.component] = 0
        fake.strictPriority = true
        assertFalse(Care(tv, session, repo, settle = {}).selectHome())
        assertEquals(original, fake.home())
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun wifiOwnershipTriggersRepairAfterInstallingGenTVInsteadOfRejectingTheModel() = runTest {
        val (fake, tv) = fixture(); fake.strictPriority = true; val session = session(tv)
        fake.wifiOwner = Launchers.SETUPWRAITH
        val outcome = Care(tv, session, repo, settle = {}).takeover(ByteArray(2), emptyList())
        assertTrue(outcome is HandoverOutcome.SetupAppBlocked)
        assertTrue(HomeApp.GENTV.pkg in fake.installed)
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun verifiedActiveEthernetCanContinueDespiteSetupOwnedSavedWifi() = runTest {
        val (fake, base) = fixture(); fake.strictPriority = true; fake.wifiOwner = Launchers.SETUPWRAITH
        val tv = object : TvShell by base {
            override suspend fun run(command: String): String = when (command) {
                "dumpsys connectivity" -> "Active default network: 101\nNetworkAgentInfo{network{101} Transports: ETHERNET}"
                "ip route show default" -> "default via 192.0.2.1 dev eth0 proto dhcp"
                else -> base.run(command)
            }
        }
        val session = session(tv)
        assertTrue(Care(tv, session, repo, settle = {}).takeover(ByteArray(2), emptyList()) is HandoverOutcome.Done)
        assertEquals(HomeApp.GENTV.component, fake.home())
    }
    @Test fun cvteCanUseGenTVWithoutDisablingItsOriginalLauncher() = runTest {
        val (fake, tv) = fixture(); fake.installed += "com.cvte.tv.launcher.main"; val session = session(tv)
        val outcome = Care(tv, session, repo, settle = {}).takeover(ByteArray(2), emptyList())
        assertTrue(outcome is HandoverOutcome.Done)
        assertEquals(HomeApp.GENTV.component, fake.home())
        assertFalse(fake.commands.any { it.startsWith("pm disable") })
    }
    @Test fun cvteCompatibilityResumesAfterRebootCheckpoint() = runTest {
        val (fake, tv) = fixture()
        val vendor = "com.cvte.tv.launcher.main"
        fake.installed += vendor
        fake.homeClaimants.clear(); fake.homeClaimants["$vendor/.Home"] = 2
        fake.preferredHome = "$vendor/.Home"; fake.strictPriority = true
        val session = session(tv); val care = Care(tv, session, repo, settle = {})
        assertTrue(care.takeover(ByteArray(2), emptyList()) is HandoverOutcome.SetupAppBlocked)
        assertTrue(HomeApp.GENTV.pkg in fake.installed)
        assertFalse(vendor in fake.disabled)
        assertTrue(care.takeover(ByteArray(2), emptyList(), bootConfirmed = true) is HandoverOutcome.Done)
        assertEquals(HomeApp.GENTV.component, fake.home())
        assertTrue(care.restore() is RestoreOutcome.Done)
        assertFalse(vendor in fake.disabled)
    }
    @Test fun unknownVendorHomeIsDiscoveredFromLiveIntentAndCanBeTakenOver() = runTest {
        val (fake, tv) = fixture(); val vendor = "com.example.vendorhome"
        fake.installed += vendor
        fake.homeClaimants.clear(); fake.homeClaimants["$vendor/.Home"] = 2
        fake.preferredHome = "$vendor/.Home"; fake.strictPriority = true
        val session = session(tv)
        assertTrue(Care(tv, session, repo, settle = {}).takeover(ByteArray(2), emptyList()) is HandoverOutcome.Done)
        assertTrue(vendor in fake.disabled)
        assertEquals(HomeApp.GENTV.component, fake.home())
    }
    @Test fun maliciousSelectionRefusesWholeFile() {
        val file = File.createTempFile("gentv-selection", ".txt")
        try {
            file.writeText("com.example.ads\ncom.example.other; reboot\n")
            try { readPackages(file); fail("shell injection accepted") } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
    @Test fun realLocalCatalogIsParsedAndUsedByMatcher() {
        val catalog = localCatalog(repo)
        assertTrue(catalog.lists.size >= 250)
        assertTrue(catalog.rejected.isEmpty())
        val supplementalTv = TvInfo("AR55AR2851UDPRO", "R4", "rtd2851m", "Acer", "Acer", 34, "14", "fixture")
        assertTrue(ModelMatcher.match(supplementalTv, catalog.lists).any { it.list.id == "acer-ar55ar2851udpro" && it.exact })
        val tv = TvInfo("43C645", "G05", "G05_4K", "TCL", "TCL", 33, "13", "fixture")
        assertTrue(ModelMatcher.match(tv, catalog.lists, installed = emptySet()).any { it.exact })
    }

    @Test fun cleanupAndRestoreUseTheSharedCoreWithoutUninstalling() = runTest {
        val (fake, tv) = fixture()
        fake.installed += "com.example.ads"
        val session = session(tv)
        val care = Care(tv, session, repo, settle = {})
        val outcome = care.clean(listOf("com.example.ads"), emptySet(), null)
        assertTrue(outcome is DebloatOutcome.Finished)
        assertTrue("com.example.ads" in fake.disabled)
        assertTrue(session.log.records().disabledByUs().contains("com.example.ads"))
        assertTrue(care.restore() is RestoreOutcome.Done)
        assertFalse("com.example.ads" in fake.disabled)
        assertFalse(fake.commands.any { it.startsWith("pm uninstall") })
    }
    @Test fun guardedTakeoverReusesLocalCoreAndCanBeRestored() = runTest {
        val (fake, tv) = fixture(); fake.strictPriority = true; val original = fake.home(); val session = session(tv)
        val care = Care(tv, session, repo, settle = {})
        val outcome = care.takeover(ByteArray(2), emptyList())
        assertTrue(outcome is HandoverOutcome.Done)
        assertEquals(HomeApp.GENTV.component, fake.home())
        assertTrue(Launchers.LAUNCHERX in fake.disabled)
        assertFalse(fake.commands.any { it == Commands.grantLocation(HomeApp.GENTV.pkg) })
        assertTrue(care.restore() is RestoreOutcome.Done)
        assertEquals(original, fake.home())
        assertTrue(HomeApp.GENTV.pkg in fake.installed)
    }
    @Test fun refusedRollbackEnableRemainsRecoverableInTheJournal() = runTest {
        val (fake, base) = fixture(); fake.strictPriority = true
        var focusReads = 0
        val tv = object : TvShell by base {
            override suspend fun run(command: String): String {
                if (command == Commands.FOCUSED_APP && ++focusReads >= 2) {
                    fake.enableDoesNotStick += Launchers.LAUNCHERX
                    return "mFocusedApp=ActivityRecord{123 u0 com.example.other/.MainActivity t1}"
                }
                return base.run(command)
            }
        }
        val session = session(tv)
        val care = Care(tv, session, repo, settle = {})
        assertTrue(care.takeover(ByteArray(2), emptyList()) is HandoverOutcome.RollbackFailed)
        assertTrue(Launchers.LAUNCHERX in session.log.records().disabledByUs())
        fake.enableDoesNotStick.clear()
        assertTrue(care.restore() is RestoreOutcome.Done)
        assertFalse(Launchers.LAUNCHERX in fake.disabled)
    }
    @Test fun optionsRejectUnknownOrDuplicateFlags() {
        for (args in listOf(arrayOf("clean", "--uninstall"), arrayOf("clean", "--serial", "a", "--serial", "b"))) {
            try { Options(args); fail("invalid flags accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
