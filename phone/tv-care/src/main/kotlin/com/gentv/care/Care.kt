package com.gentv.care

import com.gentv.core.lists.*
import com.gentv.core.ops.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.io.File

fun localCatalog(repo: File): ListCatalog {
    val sources = File(repo, "phone/framework/data/devices").listFiles().orEmpty()
        .filter { File(it, "debloat.txt").isFile }.associate { it.name to File(it, "debloat.txt").readText() }.toMutableMap()
    val supplements = File(repo, "skills/gentv-tv-care/references/device-supplements.json")
    if (supplements.isFile) {
        val lists = kotlinx.serialization.json.Json.parseToJsonElement(supplements.readText()).jsonObject.getValue("lists").jsonObject
        for ((id, raw) in lists) sources.putIfAbsent(id, raw.jsonPrimitive.content)
    }
    return ListCatalog(sources)
}

/** Extra desktop guard closes known core gaps without changing the shared phone framework. */
fun desktopProtection(pkg: String, live: Set<String>, keep: Set<String>, homes: Set<String>): String? = when {
    !PackageName.isValid(pkg) -> "Invalid package name"
    pkg in homes || Launchers.isHomeApp(pkg) -> "Home owner or Home claimant"
    pkg in setOf("android.ext.services", "android.ext.shared", "com.cltv.mal", "com.mediatek.tis", "com.mediatek.tv.service", "com.mediatek.tv.agent") -> "Android or TV middleware"
    Regex("com\\.(google\\.)?android\\.(permissioncontroller|packageinstaller|ext\\.services|ext\\.shared|modulemetadata)").matches(pkg) -> "Android core module"
    else -> Protected.reason(pkg, live, keep)
}

data class CleanupPlan(val allowed: List<String>, val skipped: Map<String, String>)
class Care(private val tv: TvShell, private val session: Session, private val repo: File,
           private val settle: suspend () -> Unit = { delay(2000) }) {
    suspend fun plan(packages: List<String>, keep: Set<String>, modelList: String?): CleanupPlan {
        require(packages.isNotEmpty() && packages.all { PackageName.isValid(it) }) { "Entire list refused: invalid or empty package selection" }
        session.verify(tv)
        val snapshot = TvReader.snapshot(tv)
        val homeOutput = tv.run(Commands.QUERY_HOME)
        val homes = Parse.componentsIn(homeOutput)
        require(homes.isNotEmpty()) { "Cannot determine Home claimants; no cleanup allowed" }
        val catalog = localCatalog(repo)
        val selected = modelList?.let { catalog.byId(it) ?: error("Unknown local model list") }
        if (selected != null) {
            require(!selected.needsHide && selected.id !in ModelMatcher.UNSUPPORTED_DEVICE_LISTS) { "This list cannot be applied by disable-user" }
            require(ModelMatcher.match(snapshot.info, listOf(selected), installed = snapshot.installed).any { it.exact }) { "Selected list is not an exact model/codename match" }
        }
        val protected = keep + selected?.keep.orEmpty() + session.backup().disabled
        val skipped = linkedMapOf<String, String>()
        val allowed = mutableListOf<String>()
        val flags = Parse.flags(tv.run(Commands.flags(packages)))
        for (pkg in packages.distinct()) {
            val reason = desktopProtection(pkg, snapshot.liveProtected, protected, homes)
                ?: when {
                    pkg !in snapshot.installed -> "Not installed"
                    pkg in snapshot.disabled -> "Already disabled; not changed by this run"
                    flags[pkg].isNullOrBlank() -> "Could not read package flags"
                    flags[pkg]!!.contains("PERSISTENT") -> "Persistent system app"
                    else -> null
                }
            if (reason != null) skipped[pkg] = reason else allowed += pkg
        }
        return CleanupPlan(allowed, skipped)
    }
    suspend fun clean(packages: List<String>, keep: Set<String>, modelList: String?): DebloatOutcome {
        val plan = plan(packages, keep, modelList)
        session.rememberStates(tv, plan.allowed)
        val snap = TvReader.snapshot(tv)
        return Debloater(tv, session.log).run(plan.allowed, liveProtected = snap.liveProtected, listKeep = keep)
    }
    suspend fun restore(): RestoreOutcome {
        session.verify(tv)
        return Restorer(RestoreShell(tv, session), retainedInstallLog(session.log)).run()
    }
    suspend fun verifyHome(): Boolean {
        val resolved = Parse.component(tv.run(Commands.RESOLVE_HOME))
        if (resolved?.substringBefore('/') != HomeApp.GENTV.pkg) return false
        tv.run(Commands.OPEN_SETTINGS); settle()
        tv.run(Commands.KEY_HOME); settle()
        return Parse.focusedPackage(tv.run(Commands.FOCUSED_APP)) == HomeApp.GENTV.pkg
    }
    /** First try normal Android Home selection, without disabling any package. */
    suspend fun selectHome(): Boolean {
        session.verify(tv)
        val before = Parse.component(tv.run(Commands.RESOLVE_HOME))
        require(before != null && before.substringBefore('/') != "android") { "Resolve the original Home before handover" }
        val start = tv.run(Commands.startLauncher(HomeApp.GENTV.component, emptyList(), System.currentTimeMillis()/1000))
        require(start.contains("Status: ok") && !start.lineSequence().any { it.startsWith("Error") }) { "GenTV did not start; Home unchanged" }
        session.log.append(ActionRecord(System.currentTimeMillis(), Op.SET_HOME, HomeApp.GENTV.component, Phase.INTENT, before))
        tv.run(Commands.setHome(HomeApp.GENTV.component))
        if (verifyHome()) {
            session.log.append(ActionRecord(System.currentTimeMillis(), Op.SET_HOME, HomeApp.GENTV.component, Phase.DONE, before))
            return true
        }
        tv.run(Commands.setHome(before)); tv.run(Commands.KEY_HOME)
        val restored = Parse.component(tv.run(Commands.RESOLVE_HOME)) == before
        session.log.append(ActionRecord(System.currentTimeMillis(), Op.SET_HOME, HomeApp.GENTV.component, Phase.FAILED, before, "rollback=$restored"))
        check(restored) { "Home rollback unconfirmed; stop and recover original Home" }
        return false
    }
    /** Installation intent includes staged Home takeover; unfamiliar brands trigger discovery, not a veto. */
    suspend fun takeover(apk: ByteArray, dock: List<String>, bootConfirmed: Boolean = false): HandoverOutcome {
        session.verify(tv)
        val snap = TvReader.snapshot(tv)
        require(snap.info.sdk >= HomeApp.GENTV.minSdk)
        val home = snap.home?.substringBefore('/')
        val claimants = Parse.componentsIn(tv.run(Commands.QUERY_HOME))
        if (home == null || home == "android") return HandoverOutcome.NeedsChoice(claimants)
        val inspected = tv.run("dumpsys package ${PackageName.require(home)}")
        require(home in claimants && !Regex("factory|facmenu|factorytest", RegexOption.IGNORE_CASE).containsMatchIn(home)) { "Identify an actual usable Home, never a factory activity" }
        require(home == HomeApp.GENTV.pkg || Protected.reason(home) == null) { "Home points to a core system service; inspect the real launcher activity first" }
        require(home in Launchers.KNOWN_STOCK || home == HomeApp.GENTV.pkg || inspected.contains("android.intent.category.HOME")) { "Read this vendor's HOME activity declaration before disabling it" }
        val guarded = object : TvShell by tv {
            override suspend fun run(command: String): String = if (command == Commands.grantLocation(HomeApp.GENTV.pkg))
                "Optional location permission was not granted by desktop maintenance" else tv.run(command)
        }
        // Install once, try Android's Home role/default selection, then verify the actual key destination.
        val normalAttempt = LauncherHandover(guarded, session.log, identifiedStockHome = home, keepStockEnabled = true)
            .run(apk, dock, snap.info.sdk)
        val normal = if (normalAttempt is HandoverOutcome.Done) {
            if (verifyHome()) return normalAttempt
            tv.run(Commands.setHome(snap.home!!)); tv.run(Commands.KEY_HOME)
            val restored = Parse.component(tv.run(Commands.RESOLVE_HOME))
            if (restored != snap.home) return HandoverOutcome.RollbackFailed(restored, listOf(Commands.setHome(snap.home!!)))
            HandoverOutcome.RolledBack(restored)
        } else normalAttempt
        if (normal !is HandoverOutcome.RolledBack && normal !is HandoverOutcome.NeedsChoice && normal !is HandoverOutcome.SetupWraithTookHome)
            return normal
        // CVTE needs a separate installed-GenTV reboot checkpoint, not a blanket brand exclusion.
        if (snap.installed.any { it.startsWith("com.cvte.") } && !bootConfirmed)
            return HandoverOutcome.SetupAppBlocked(WraithCheck.Unknown("GenTV installed. Reboot once, confirm ADB and Home return, then resume with --boot-confirmed; do not enable key helpers in this pass"))
        val wifi = tv.run(Commands.WIFI_OWNERS)
        val ethernet = ethernetDefault(tv.run(Commands.CONNECTIVITY), tv.run(Commands.DEFAULT_ROUTE))
        if (Launchers.SETUPWRAITH in claimants) {
            if (!ethernet && (!Regex("(?m)^cname=[A-Za-z0-9_.]+").containsMatchIn(wifi) || wifi.contains("setupwraith")))
                return HandoverOutcome.SetupAppBlocked(WraithCheck.Unknown("GenTV installed. Forget and rejoin Wi-Fi in TV Settings, or establish a verified Ethernet default route; resume this session after reconnecting"))
            val check = SetupWraithGuard.check(tv, ethernet)
            if (check != WraithCheck.Ok) return HandoverOutcome.SetupAppBlocked(check)
        }
        val targets = listOfNotNull(home.takeUnless { it == HomeApp.GENTV.pkg }, Launchers.SETUPWRAITH.takeIf { it in claimants })
        session.rememberStates(tv, targets)
        val outcome = LauncherHandover(guarded, session.log, identifiedStockHome = home, alreadyInstalled = true)
            .run(apk, emptyList(), snap.info.sdk, onEthernet = ethernet)
        if (outcome is HandoverOutcome.Done && !verifyHome()) {
            val newlyOff = Parse.packages(tv.run(Commands.LIST_DISABLED)) - snap.disabled
            for (pkg in targets.filter { it in newlyOff }) {
                val command = if (session.states()[pkg] == 0) "pm default-state --user 0 ${PackageName.require(pkg)}" else Commands.enable(pkg)
                tv.run(command)
                val enabled = pkg !in Parse.packages(tv.run(Commands.LIST_DISABLED))
                session.log.append(ActionRecord(System.currentTimeMillis(), Op.ENABLE, pkg, if (enabled) Phase.DONE else Phase.FAILED, detail = "handover rollback"))
            }
            val stillOff = targets.filter { it in newlyOff && it in Parse.packages(tv.run(Commands.LIST_DISABLED)) }
            if (stillOff.isNotEmpty()) return HandoverOutcome.RollbackFailed(Parse.component(tv.run(Commands.RESOLVE_HOME)), stillOff.map { Commands.enable(it) } + Commands.setHome(snap.home!!))
            tv.run(Commands.setHome(snap.home!!)); tv.run(Commands.KEY_HOME)
            val restored = Parse.component(tv.run(Commands.RESOLVE_HOME))
            check(restored == snap.home) { "Handover rollback unconfirmed; recover original Home" }
            return HandoverOutcome.RolledBack(restored)
        }
        return outcome
    }
}

fun readPackages(file: File): List<String> {
    val lines = file.readText().removePrefix("\uFEFF").lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotBlank() }.toList()
    require(lines.isNotEmpty() && lines.all { PackageName.isValid(it) }) { "Entire selection refused: use one package per line" }
    return lines.distinct()
}
