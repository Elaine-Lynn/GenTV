package com.gentv.mobile.data

import com.gentv.core.lists.ListCatalog
import com.gentv.core.ops.ActionLog
import com.gentv.core.ops.Backup
import com.gentv.core.ops.FileActionLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A TV this phone has worked on, for the "My TVs" screen and reconnecting after an IP change. */
@Serializable
data class KnownTv(val serial: String, val title: String, val model: String, val host: String, val lastSeen: Long)

/**
 * Everything GenTV keeps on the phone:
 *   keys/adbkey.pem                 ADB identity ("Always allow" sticks)
 *   tvs/<serial>/backup-<ts>.json   taken before any change
 *   tvs/<serial>/actions.jsonl      append-only change log, drives Restore
 *   tvs/<serial>/tv.json            name and last address
 *   logs/                           connection and command trace for Share logs
 */
class Store(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val languageFile = File(root, "language.txt")
    fun language(): String = runCatching { languageFile.readText().trim() }.getOrNull()
        ?.takeIf { it == "en" || it == "zh" } ?: "en"
    fun saveLanguage(code: String) {
        require(code == "en" || code == "zh")
        languageFile.parentFile?.mkdirs()
        val temporary = File(languageFile.path + ".tmp")
        temporary.writeText(code)
        check(temporary.renameTo(languageFile)) { "Could not save language preference" }
    }

    val keyFile = File(root, "keys/adbkey.pem")
    val logsDir = File(root, "logs")
    /** The update check's cache: when it last asked GitHub, what it found, what was dismissed. */
    val updateFile = File(root, "update.json")
    /** Set after the first successful connect: later "Connect a TV" taps skip the get-ready steps. */
    val onboardedFile = File(root, "onboarded")
    fun onboarded() = onboardedFile.exists()
    fun markOnboarded() { runCatching { onboardedFile.parentFile?.mkdirs(); onboardedFile.writeText("1") } }

    /** The rating prompt: asks used, when, and whether the person already rated. */
    val ratingFile = File(root, "rating.json")

    /** Serial numbers become folder names, so only safe characters survive. */
    private fun tvDir(serial: String) = File(root, "tvs/" + serial.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "unknown" })

    /** Real app icons read from TV APKs, one PNG per package. */
    val iconsDir: File get() = File(root, "icons").apply { mkdirs() }

    fun iconFile(pkg: String) = File(iconsDir, pkg.replace(Regex("[^A-Za-z0-9._]"), "_") + ".png")

    fun cachedIcon(pkg: String): String? = iconFile(pkg).takeIf { it.length() > 0 }?.path

    fun actionLog(serial: String): ActionLog = FileActionLog(File(tvDir(serial), "actions.jsonl"))

    fun saveBackup(backup: Backup): File {
        val f = File(tvDir(backup.serial), "backup-${backup.takenAt}.json")
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(backup.toJson())
        tmp.renameTo(f)
        return f
    }

    fun latestBackup(serial: String): Backup? =
        tvDir(serial).listFiles { f -> f.name.startsWith("backup-") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name }?.let { runCatching { Backup.fromJson(it.readText()) }.getOrNull() }

    /** Apps the owner pinned to this TV's remote bar, in order; null until they change it. */
    fun pinnedApps(serial: String): List<String>? =
        File(tvDir(serial), "pinned.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }

    fun savePinned(serial: String, pkgs: List<String>) {
        val f = File(tvDir(serial), "pinned.txt")
        f.parentFile?.mkdirs()
        f.writeText(pkgs.joinToString("\n"))
    }

    fun remember(tv: KnownTv) {
        val f = File(tvDir(tv.serial), "tv.json")
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(KnownTv.serializer(), tv))
    }

    fun knownTvs(): List<KnownTv> =
        File(root, "tvs").listFiles()?.mapNotNull { d ->
            File(d, "tv.json").takeIf { it.exists() }?.let { runCatching { json.decodeFromString(KnownTv.serializer(), it.readText()) }.getOrNull() }
        }?.sortedByDescending { it.lastSeen }.orEmpty()

    companion object {
        /** The bundled device lists (assets/lists/<id>.txt, copied from framework/data at build time). */
        fun loadLists(assets: android.content.res.AssetManager): ListCatalog {
            val names = assets.list("lists").orEmpty().filter { it.endsWith(".txt") }
            return ListCatalog(names.associate { it.removeSuffix(".txt") to assets.open("lists/$it").bufferedReader().readText() })
        }
    }
}
