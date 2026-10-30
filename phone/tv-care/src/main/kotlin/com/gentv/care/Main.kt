package com.gentv.care

import com.gentv.core.lists.*
import com.gentv.core.ops.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.system.exitProcess

private val BOOLS = setOf("apply", "allow-stock-disable", "ui-confirmed", "router-dns-reviewed", "allow-helpers", "boot-confirmed", "device-confirmed")
class Options(args: Array<String>) {
    val command = args.firstOrNull() ?: "help"
    private val values = linkedMapOf<String, String>()
    init {
        var i = 1
        while (i < args.size) {
            val key = args[i].removePrefix("--")
            require(args[i].startsWith("--") && key !in values) { "Use unique --option arguments" }
            values[key] = if (key in BOOLS) "true" else requireNotNull(args.getOrNull(++i)?.takeUnless { it.startsWith("--") }) { "Missing value for --$key" }
            i++
        }
        val common = setOf("repo", "adb", "adb-python", "serial", "session")
        val allowed = when (command) {
            "help", "inspect", "restore", "verify-home" -> emptySet()
            "backup" -> setOf("device-confirmed")
            "check" -> setOf("model-list")
            "desktop-export" -> setOf("output")
            "desktop-save" -> setOf("profile", "revision")
            "look-export" -> setOf("output")
            "look-save" -> setOf("config", "before-hash", "allow-helpers")
            "match" -> setOf("limit")
            "privacy" -> setOf("output")
            "configure" -> setOf("columns", "dock", "greeting", "effects", "hide-app", "show-app", "rename-app", "boot", "settings-key", "input-key", "app-key")
            "benchmark" -> setOf("samples", "idle-seconds", "output", "compare")
            "clean" -> setOf("packages", "keep", "model-list")
            "install" -> setOf("apk", "sha256", "aapt")
            "home" -> setOf("allow-stock-disable", "ui-confirmed", "apk", "sha256", "aapt", "boot-confirmed")
            "dns" -> setOf("provider", "router-dns-reviewed")
            "caches" -> emptySet()
            "screenshot" -> setOf("output")
            "key" -> setOf("key")
            else -> error("Unknown command: $command")
        }
        require(values.keys.all { it in common || it in allowed || it == "apply" }) { "Unknown option" }
    }
    fun get(name: String) = values[name]
    fun require(name: String) = get(name) ?: error("Missing --$name")
    fun flag(name: String) = get(name) == "true"
}
fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun verifiedApk(options: Options): ByteArray {
    val apk = File(options.require("apk")).canonicalFile
    val expected = options.require("sha256").lowercase()
    require(expected.matches(Regex("[a-f0-9]{64}")))
    val bytes = apk.readBytes()
    require(sha256(bytes) == expected) { "APK checksum mismatch" }
    val output = ProcessBuilder(options.require("aapt"), "dump", "badging", apk.absolutePath).redirectErrorStream(true).start()
    val badging = output.inputStream.bufferedReader().readText()
    require(output.waitFor() == 0 && Regex("package: name='com\\.gentv\\.tv'").containsMatchIn(badging)) { "APK is not a GenTV TV package" }
    val sdk = Regex("sdkVersion:'(\\d+)'").find(badging)?.groupValues?.get(1)?.toInt() ?: error("Cannot read APK minimum SDK")
    require(sdk >= 26) { "Unexpected APK minimum SDK" }
    return bytes
}
private fun result(status: String, detail: String, extras: Map<String, JsonElement> = emptyMap()) = buildJsonObject {
    put("status", status); put("detail", detail); extras.forEach { (key, value) -> put(key, value) }
}

suspend fun execute(options: Options, tv: TvShell, repo: File): JsonObject {
    val command = options.command
    if (command == "inspect") {
        val snap = TvReader.snapshot(tv)
        return result("read-only", "Device inspected", mapOf("model" to JsonPrimitive(snap.info.displayName), "sdk" to JsonPrimitive(snap.info.sdk), "home" to JsonPrimitive(snap.home), "installed" to JsonPrimitive(snap.installed.size)))
    }
    if (command == "match") {
        val snap = TvReader.snapshot(tv)
        val matches = ModelMatcher.match(snap.info, localCatalog(repo).lists.filterNot { it.needsHide }, options.get("limit")?.toInt() ?: 5, snap.installed)
        return result("read-only", "Candidates require firmware and owner review", mapOf("matches" to buildJsonArray {
            matches.forEach { match -> add(buildJsonObject {
                put("id", match.list.id); put("title", match.list.title); put("score", match.score)
                put("coverage", match.coverage); put("exact", match.exact)
                if (match.exact) put("label", "已兼容")
                put("needsHide", match.list.needsHide); put("fireOs", match.list.isFireOs)
                put("keep", buildJsonArray { match.list.keep.sorted().forEach { add(it) } })
            }) }
        }))
    }
    if (command == "privacy") {
        val report = PrivacyAudit(tv, repo).collect()
        saveReport(File(options.require("output")), report)
        return result("saved", "Read-only privacy audit saved; no automatic cleanup", mapOf("findings" to JsonPrimitive(report["findings"]!!.jsonArray.size)))
    }
    if (command == "benchmark") {
        val samples = options.get("samples")?.toInt() ?: 5
        val idle = options.get("idle-seconds")?.toInt() ?: 3
        require(samples in 3..20 && idle in 1..30)
        if (!options.flag("apply")) return result("plan", "Measure installed GenTV: $samples cold launches, injected-key frames, PSS and $idle second idle window; no install or data clear")
        val measured = Performance(tv).measure(samples, idle)
        val report = options.get("compare")?.let { JsonObject(measured + ("comparisonDelta" to comparePerformance(measured, Json.parseToJsonElement(File(it).readText()).jsonObject))) } ?: measured
        saveReport(File(options.require("output")), report)
        return result("saved", "Performance report saved; unavailable metrics remain null", mapOf("summary" to report["summary"]!!))
    }
    val session = Session(File(options.require("session")).canonicalFile)
    if (command == "backup") {
        val backup = session.create(tv, deviceConfirmed = options.flag("device-confirmed"))
        return result("saved", "Private baseline saved; no TV settings changed", mapOf("packages" to JsonPrimitive(backup.packages.size)))
    }
    session.verify(tv, requireSameFirmware = command != "check")
    val care = Care(tv, session, repo)
    val apply = options.flag("apply")
    return when (command) {
        "clean" -> {
            val packages = readPackages(File(options.require("packages")))
            val keep = options.get("keep")?.let { readPackages(File(it)).toSet() }.orEmpty()
            val plan = care.plan(packages, keep, options.get("model-list"))
            if (!apply) result("plan", "No packages changed", mapOf("allowed" to buildJsonArray { plan.allowed.forEach { add(it) } }, "skipped" to buildJsonObject { plan.skipped.forEach { (pkg, reason) -> put(pkg, reason) } }))
            else {
                val outcome = care.clean(packages, keep, options.get("model-list"))
                result(if (outcome is DebloatOutcome.Finished && outcome.results.none { it is PkgResult.Failed }) "applied" else "partial", outcome.toString())
            }
        }
        "install" -> {
            val bytes = verifiedApk(options)
            val sdk = tv.run("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0
            require(sdk >= 26) { "GenTV requires Android 8+" }
            if (!apply) result("plan", "Checksum and package verified; install GenTV without changing Home")
            else {
                session.log.append(ActionRecord(System.currentTimeMillis(), Op.INSTALL, HomeApp.GENTV.pkg, Phase.INTENT))
                tv.push(bytes, Commands.APK_TMP)
                val output = try { tv.run(Commands.INSTALL_TMP) } finally { tv.run(Commands.REMOVE_TMP) }
                val installed = output.contains("Success")
                session.log.append(ActionRecord(System.currentTimeMillis(), Op.INSTALL, HomeApp.GENTV.pkg, if (installed) Phase.DONE else Phase.FAILED, detail = output))
                require(installed) { "Install refused; no uninstall or downgrade attempted: $output" }
                val start = tv.run(Commands.startLauncher(HomeApp.GENTV.component, emptyList(), System.currentTimeMillis()/1000))
                result(if (start.contains("Status: ok") && !start.contains("Error:")) "installed" else "partial", "$output\n$start; physical UI confirmation required")
            }
        }
        "home" -> {
            if (!apply) result("plan", "Complete staged GenTV Home takeover: install/select first, investigate vendor Home and network, then reversibly disable blockers only if necessary; physical UI confirmation required")
            else {
                require(options.flag("ui-confirmed")) { "Confirm GenTV UI on the real TV before Home handover" }
                if (options.get("apk") != null) {
                    val bytes = verifiedApk(options)
                    val outcome = care.takeover(bytes, emptyList(), bootConfirmed = options.flag("boot-confirmed"))
                    val verified = outcome is HandoverOutcome.Done && care.verifyHome()
                    result(if (verified) "handed-over" else if (outcome is HandoverOutcome.SetupAppBlocked || outcome is HandoverOutcome.NeedsChoice) "needs-device-action" else "partial", "$outcome; resume this session after the indicated device step; physical remote and reboot checks remain required")
                } else {
                    val selected = care.selectHome()
                    result(if (selected) "handed-over" else "needs-device-action", if (selected) "Home checked through Android; verify physical remote and reboot" else "Original Home restored. Resume the same home command with a verified local --apk and --sha256 to continue staged compatibility takeover; do not treat this model as unsupported")
                }
            }
        }
        "restore" -> if (!apply) result("plan", "Restore this session's packages, settings and original Home; retain GenTV", mapOf("packages" to buildJsonArray { session.log.records().disabledByUs().forEach { add(it) } }))
            else {
                val outcome = care.restore()
                val look = runCatching { LookBridge(tv, session).restoreIfChanged() }
                val desktop = runCatching { DesktopBridge(tv, session).restoreIfChanged() }
                val settingsRestored = session.log.records().changedSettings().all { (key, before) ->
                    val (ns, name) = key.split('/', limit = 2)
                    tv.run(Settings.get(ns, name)).trim() == (before ?: "null")
                }
                val ok = settingsRestored && look.getOrDefault(false) && desktop.getOrDefault(false) && outcome is RestoreOutcome.Done && (session.log.records().originalHome() == null || outcome.homeRestored)
                result(if (ok) "restored" else "partial", "$outcome; settings=$settingsRestored; theme=${look.exceptionOrNull()?.message ?: look.getOrDefault(false)}; desktop=${desktop.exceptionOrNull()?.message ?: desktop.getOrDefault(false)}")
            }
        "dns" -> {
            val dns = PrivateDns(tv, session.log)
            val provider = options.require("provider")
            require(provider in setOf("ads", "family", "restore"))
            require(tv.run("getprop ro.build.version.sdk").trim().toInt() >= 28) { "Private DNS requires Android 9+" }
            if (!apply) result("plan", "Current DNS=${dns.read()}; requested=$provider; YouTube ads remain")
            else {
                require(provider == "restore" || options.flag("router-dns-reviewed")) { "Review existing router/VPN/domain filtering before changing DNS" }
                val after = if (provider == "restore") {
                    for ((key, before) in session.log.records().changedSettings().filterKeys { it in setOf("global/private_dns_mode", "global/private_dns_specifier") }) {
                        val (ns, name) = key.split('/', limit = 2)
                        tv.run(if (before == null || before == "null") Settings.delete(ns, name) else Settings.put(ns, name, before))
                    }
                    dns.read()
                } else dns.turnOn(if (provider == "ads") AdBlockProvider.ADS else AdBlockProvider.FAMILY)
                val matches = if (provider == "restore") session.log.records().changedSettings().filterKeys { it in setOf("global/private_dns_mode", "global/private_dns_specifier") }.all { (key, before) ->
                    val (ns, name) = key.split('/', limit = 2)
                    tv.run(Settings.get(ns, name)).trim() == (before ?: "null")
                } else after.provider == (if (provider == "ads") AdBlockProvider.ADS else AdBlockProvider.FAMILY)
                result(if (matches) "applied" else "partial", "$after; verify resolution and streaming playback")
            }
        }
        "caches" -> if (!apply) result("plan", "Clear rebuildable app caches using GenTV TvFacts; no app data clear, caches cannot be restored")
            else { val outcome = TvFacts.freeCaches(tv); result(if (outcome.finished) "applied" else "partial", outcome.toString()) }
        "check" -> {
            val snap = TvReader.snapshot(tv)
            val selected = options.get("model-list")?.let { localCatalog(repo).byId(it) ?: error("Unknown local model list") }
            val report = TvCheck(tv, session.log).run(selected?.packages.orEmpty(), session.backup().packages.toSet(), snap.liveProtected, selected?.keep.orEmpty())
            result("read-only", report.toString())
        }
        "configure" -> {
            val desktop = DesktopBridge(tv, session); val look = LookBridge(tv, session)
            val current = desktop.read(); val theme = look.read()
            val keys = listOf("columns", "dock", "greeting", "effects", "hide-app", "show-app", "rename-app", "boot", "settings-key", "input-key", "app-key")
            val changes = keys.mapNotNull { key -> options.get(key)?.let { key to it } }.toMap()
            val draft = Configuration.draft(current, theme, changes)
            if (!apply) result("plan", "Validated current applications and local configuration; no JSON editing or device writes required", mapOf("changes" to buildJsonObject { changes.forEach { (key, value) -> put(key, value) } }))
            else {
                val layoutResult = runCatching { if (draft.layout != current["config"]) desktop.save(draft.layout, current["revision"]!!.jsonPrimitive.int) }
                val themeResult = if (layoutResult.isSuccess) runCatching { if (draft.look != theme) look.save(draft.look, sha256(theme.toString().toByteArray()), allowHelpers = changes.keys.any { it in setOf("boot", "settings-key", "input-key", "app-key") }) } else Result.failure(IllegalStateException("Layout not confirmed; theme not attempted"))
                result(if (layoutResult.isSuccess && themeResult.isSuccess) "applied" else "partial", "layout=${layoutResult.exceptionOrNull()?.message ?: "confirmed"}; theme=${themeResult.exceptionOrNull()?.message ?: "confirmed"}; boot/key helpers still require owner permission and physical testing")
            }
        }
        "look-export" -> {
            val config = LookBridge(tv, session).read()
            val output = File(options.require("output")); require(!output.exists())
            privateWrite(output, config.toString())
            result("saved", "Theme config exported; wallpaper files untouched", mapOf("sha256" to JsonPrimitive(sha256(config.toString().toByteArray()))))
        }
        "look-save" -> {
            val config = Json.parseToJsonElement(File(options.require("config")).readText()).jsonObject
            val hash = options.require("before-hash")
            require(sha256(LookBridge(tv, session).read().toString().toByteArray()) == hash) { "Theme changed since export" }
            if (!apply) result("plan", "Merge reviewed local GenTV theme/boot/key settings into exported config; wallpapers and layout remain intact")
            else { LookBridge(tv, session).save(config, hash, options.flag("allow-helpers")); result("applied", "Theme saved with recovery baseline; helper permissions and physical buttons need separate verification") }
        }
        "desktop-export" -> {
            val snapshot = DesktopBridge(tv, session).read()
            val output = File(options.require("output")); require(!output.exists()) { "Export destination already exists" }
            privateWrite(output, snapshot.toString())
            result("saved", "Private desktop snapshot saved, including revision and schema 2 config")
        }
        "desktop-save" -> {
            val config = Json.parseToJsonElement(File(options.require("profile")).readText()).jsonObject
            val revision = options.require("revision").toInt()
            if (!apply) {
                val current = DesktopBridge(tv, session).read()
                require(current["revision"]!!.jsonPrimitive.int == revision) { "Desktop revision changed" }
                result("plan", "Save supplied schema 2 profile at revision $revision; TV will validate application/input targets")
            } else {
                val saved = DesktopBridge(tv, session).save(config, revision)
                result("applied", "Desktop revision ${saved["revision"]}; original profile retained for session restore")
            }
        }
        "verify-home" -> result(if (care.verifyHome()) "android-home-confirmed" else "failed", "Physical Home, HDMI, standby and reboot require owner confirmation")
        else -> error("Unsupported session command")
    }
}

fun main(args: Array<String>) = runBlocking {
    try {
        val options = Options(args)
        if (options.command == "help") {
            println("GenTV maintenance adapter: inspect, match, backup, clean, install, home, restore, dns, caches, check, verify-home, desktop-export, desktop-save, look-export, look-save, privacy, benchmark, configure, screenshot, key. Specify --repo, --serial, --adb; mutating operations default to plan and require --apply. Session commands require --session. No auto-pairing or device discovery.")
            return@runBlocking
        }
        val repo = File(options.require("repo")).canonicalFile
        require(File(repo, "phone/framework/core").isDirectory) { "Not a GenTV repository" }
        val adb = AdbShell(options.require("adb"), options.require("serial"), options.get("adb-python"))
        adb.requireAuthorised()
        val legacy = LegacyShell(adb)
        val lockDirectory = File(System.getProperty("user.home"), ".cache/gentv-tv-care/locks").apply { mkdirs(); restrict(this, true) }
        RandomAccessFile(File(lockDirectory, "maintenance.lock"), "rw").use { lockFile ->
            val lock = lockFile.channel.tryLock() ?: error("Another GenTV maintenance operation is running on this computer")
            lock.use {
                val output = when (options.command) {
                    "screenshot" -> { val file = File(options.require("output")); require(!file.exists()); file.parentFile?.mkdirs(); file.writeBytes(adb.screenshot()); restrict(file, false); result("saved", "PNG screenshot saved privately") }
                    "key" -> {
                        val key = RemoteKey.valueOf(options.require("key").uppercase())
                        if (options.flag("apply")) { Remote(adb).use { it.press(key) }; result("sent", key.name) }
                        else result("plan", "Send ${key.name}")
                    }
                    else -> execute(options, legacy, repo)
                }
                println(LogRedactor.redact(output.toString(), listOf(options.require("serial"))))
                if (output["status"]?.jsonPrimitive?.content in setOf("failed", "partial", "rolled-back", "needs-device-action")) exitProcess(2)
            }
        }
    } catch (error: Exception) {
        System.err.println(result("stopped", LogRedactor.redact(error.message ?: error.javaClass.simpleName)).toString())
        exitProcess(1)
    }
}
