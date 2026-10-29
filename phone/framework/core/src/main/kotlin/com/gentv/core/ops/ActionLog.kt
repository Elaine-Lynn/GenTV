package com.gentv.core.ops

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * REMOVE is "uninstall for the TV's user" (`pm uninstall -k --user 0`): a preinstalled app's APK stays
 * on the system partition and REINSTALL (`cmd package install-existing`) brings it back. For an app
 * the owner installed, REMOVE is a normal uninstall and can't be undone (detail [OWNER_APP]).
 */
enum class Op { DISABLE, ENABLE, INSTALL, UNINSTALL, SET_HOME, SETTING, REMOVE, REINSTALL }

/** [ActionRecord.detail] of a REMOVE on an app the owner installed: gone for good, not restorable. */
const val OWNER_APP = "owner app"

/** INTENT is written before a change, DONE or FAILED after. An INTENT with no result means "maybe done". */
enum class Phase { INTENT, DONE, FAILED }

@Serializable
data class ActionRecord(
    val ts: Long,
    val op: Op,
    val target: String,
    val phase: Phase,
    /** Value before the change: previous Home component, previous setting value. */
    val before: String? = null,
    val detail: String? = null,
)

/** Append-only record of every change GenTV makes to one TV. Restore is driven from this. */
interface ActionLog {
    fun append(record: ActionRecord)
    fun records(): List<ActionRecord>
}

class MemoryActionLog : ActionLog {
    private val items = mutableListOf<ActionRecord>()
    override fun append(record: ActionRecord) { synchronized(items) { items += record } }
    override fun records(): List<ActionRecord> = synchronized(items) { items.toList() }
}

/**
 * One JSON object per line, fsync'd after every write so a crash or a dead battery cannot lose a
 * change that was already sent to the TV. A torn last line (crash mid-write) is skipped on read.
 */
class FileActionLog(private val file: File) : ActionLog {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun append(record: ActionRecord) {
        file.parentFile?.mkdirs()
        // After a torn write the file ends mid-line; start a fresh line so this record survives.
        val needsNewline = file.length() > 0 && java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - 1); raf.read() != '\n'.code
        }
        FileOutputStream(file, true).use { out ->
            if (needsNewline) out.write('\n'.code)
            out.write((json.encodeToString(ActionRecord.serializer(), record) + "\n").toByteArray())
            out.fd.sync()
        }
    }

    @Synchronized
    override fun records(): List<ActionRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching { json.decodeFromString(ActionRecord.serializer(), line) }.getOrNull()
        }
    }
}

/**
 * Packages GenTV turned off and has not turned back on. A DISABLE intent counts even without a
 * result: the command may have reached the TV before the connection dropped, and enabling a
 * package that was never disabled is harmless.
 */
fun List<ActionRecord>.disabledByUs(): Set<String> {
    val state = linkedMapOf<String, Boolean>()
    val confirmed = mutableMapOf<String, Boolean>()
    for (r in this) {
        when {
            r.op == Op.DISABLE && r.phase == Phase.INTENT -> state[r.target] = true
            r.op == Op.DISABLE && r.phase == Phase.DONE -> { state[r.target] = true; confirmed[r.target] = true }
            r.op == Op.DISABLE && r.phase == Phase.FAILED -> state[r.target] = confirmed[r.target] ?: false
            r.op == Op.ENABLE && r.phase == Phase.DONE -> { state[r.target] = false; confirmed[r.target] = false }
        }
    }
    return state.filterValues { it }.keys
}

/**
 * Preinstalled apps GenTV removed for the TV's user and hasn't brought back. Like [disabledByUs], an
 * INTENT counts: the uninstall may have reached the TV, and install-existing on an app that is still
 * there is harmless. Apps the owner installed are left out: they can't come back ([ownerAppsRemoved]).
 */
fun List<ActionRecord>.removedByUs(): Set<String> {
    val state = linkedMapOf<String, Boolean>()
    val confirmed = mutableMapOf<String, Boolean>()
    for (r in this) when {
        r.op == Op.REMOVE && r.detail == OWNER_APP -> state.remove(r.target)
        r.op == Op.REMOVE && r.phase == Phase.INTENT -> state[r.target] = true
        r.op == Op.REMOVE && r.phase == Phase.DONE -> { state[r.target] = true; confirmed[r.target] = true }
        r.op == Op.REMOVE && r.phase == Phase.FAILED -> state[r.target] = confirmed[r.target] ?: false
        r.op == Op.REINSTALL && r.phase == Phase.DONE -> { state[r.target] = false; confirmed[r.target] = false }
    }
    return state.filterValues { it }.keys
}

/** Everything GenTV took off the TV and can still put back: turned off or removed for the user. */
fun List<ActionRecord>.offByUs(): Set<String> = disabledByUs() + removedByUs()

/** Apps the owner installed that GenTV uninstalled at their request: gone for good. */
fun List<ActionRecord>.ownerAppsRemoved(): Set<String> =
    filter { it.op == Op.REMOVE && it.detail == OWNER_APP && it.phase == Phase.DONE }.map { it.target }.toSet()

/** REMOVE intents with no result yet. */
fun List<ActionRecord>.pendingRemoves(): Set<String> {
    val pending = linkedSetOf<String>()
    for (r in this) if (r.op == Op.REMOVE) {
        if (r.phase == Phase.INTENT) pending += r.target else pending -= r.target
    }
    return pending
}

/** DISABLE intents with no result yet: the command may or may not have reached the TV. */
fun List<ActionRecord>.pendingDisables(): Set<String> {
    val pending = linkedSetOf<String>()
    for (r in this) if (r.op == Op.DISABLE) {
        if (r.phase == Phase.INTENT) pending += r.target else pending -= r.target
    }
    return pending
}

/** The Home component before GenTV first changed it, or null if Home was never touched. */
fun List<ActionRecord>.originalHome(): String? =
    firstOrNull { it.op == Op.SET_HOME && it.phase != Phase.FAILED && it.before != null }?.before

/** True when GenTV installed its launcher and has not uninstalled it since. */
fun List<ActionRecord>.launcherInstalledByUs(pkg: String): Boolean {
    var installed = false
    for (r in this) if (r.target == pkg) when {
        r.op == Op.INSTALL && r.phase == Phase.DONE -> installed = true
        r.op == Op.UNINSTALL && r.phase == Phase.DONE -> installed = false
    }
    return installed
}

/** Settings changed by GenTV: key "namespace/name" -> value before the first change. */
fun List<ActionRecord>.changedSettings(): Map<String, String?> {
    val result = linkedMapOf<String, String?>()
    for (r in this) if (r.op == Op.SETTING && r.phase != Phase.FAILED && r.target !in result) result[r.target] = r.before
    return result
}
