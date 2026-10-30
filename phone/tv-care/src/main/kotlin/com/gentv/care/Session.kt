package com.gentv.care

import com.gentv.core.ops.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

fun privateWrite(file: File, content: String) {
    file.parentFile.mkdirs()
    val temporary = Files.createTempFile(file.parentFile.toPath(), ".gentv-state-", ".tmp").toFile()
    try {
        restrict(temporary, false)
        FileOutputStream(temporary).use { it.write(content.toByteArray()); it.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        restrict(file, false)
        runCatching { java.nio.channels.FileChannel.open(file.parentFile.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) } }
    } finally { temporary.delete() }
}
fun restrict(file: File, directory: Boolean) {
    if (file.toPath().fileSystem.supportedFileAttributeViews().contains("posix"))
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"))
}

/** Each session has an immutable baseline and the same durable journal as the phone. */
class Session(val directory: File) {
    private val baseline = File(directory, "backup.json")
    val log: ActionLog = object : ActionLog {
        private val delegate = FileActionLog(File(directory, "actions.jsonl"))
        override fun append(record: ActionRecord) { delegate.append(record); restrict(File(directory, "actions.jsonl"), false) }
        override fun records() = delegate.records()
    }
    fun backup() = Backup.fromJson(baseline.readText())
    suspend fun create(tv: TvShell, deviceConfirmed: Boolean = false): Backup {
        require(!directory.exists()) { "Session already exists; never overwrite a recovery baseline" }
        directory.mkdirs(); restrict(directory, true)
        val snapshot = TvReader.snapshot(tv)
        require(snapshot.installed.isNotEmpty() && snapshot.disabled.all { it in snapshot.installed }) { "Package snapshot incomplete" }
        val backup = TvReader.backup(tv, snapshot, System.currentTimeMillis())
        DeviceIdentity.firmware(backup.props)
        for ((namespace, settings) in backup.settings) {
            require(!Regex("(?i)permission denial|error:|not found").containsMatchIn(settings)) { "Settings backup refused" }
            if (settings.isBlank()) require(tv.run(Settings.get(namespace, "gentv_read_probe")).trim() == "null") { "Cannot distinguish an empty settings table from an unavailable command" }
        }
        privateWrite(baseline, backup.toJson())
        privateWrite(File(directory, "enabled-states.json"), "{}")
        DeviceIdentity.bind(tv, backup.props, directory, deviceConfirmed)
        return backup
    }
    suspend fun verify(tv: TvShell, requireSameFirmware: Boolean = true) {
        val before = backup()
        val now = Parse.props(tv.run(Commands.GETPROP))
        require(!requireSameFirmware || DeviceIdentity.firmware(now) == DeviceIdentity.firmware(before.props)) { "Firmware/device differs from session" }
        DeviceIdentity.verify(tv, now, directory, before)
    }
    fun states(): Map<String, Int> = Json.parseToJsonElement(File(directory, "enabled-states.json").readText()).jsonObject.mapValues { it.value.jsonPrimitive.int }
    suspend fun rememberStates(tv: TvShell, packages: Collection<String>) {
        val states = states().toMutableMap()
        for (pkg in packages) if (pkg !in states) {
            val output = tv.run("dumpsys package ${com.gentv.core.lists.PackageName.require(pkg)}")
            val line = output.lineSequence().firstOrNull { Regex("\\bUser 0:").containsMatchIn(it) }
            val value = line?.let { Regex("\\benabled=(\\d+)").find(it)?.groupValues?.get(1)?.toInt() }
            require(value in 0..4) { "Cannot read original User 0 package state for $pkg" }
            states[pkg] = value!!
        }
        privateWrite(File(directory, "enabled-states.json"), buildJsonObject { states.forEach { (pkg, state) -> put(pkg, state) } }.toString())
    }
}

/** Restore only this session, retain GenTV, preserve default/enabled package state and other undo records. */
class RestoreShell(private val tv: TvShell, private val session: Session) : TvShell by tv {
    private val targets = session.log.records().disabledByUs()
    override suspend fun run(command: String): String = when {
        command == Commands.READ_MIRROR -> targets.joinToString("\n", postfix = "\nEND")
        command == Commands.READ_REMOVED -> "END"
        command == Commands.CLEAR_MIRROR -> { targets.forEach { tv.run(Commands.unmirror(it)) }; "cleared" }
        command == Commands.CLEAR_REMOVED -> "cleared"
        command.startsWith("pm enable ") -> {
            val pkg = command.substringAfter("pm enable ")
            require(pkg in targets)
            when (session.states()[pkg]) {
                0 -> tv.run("pm default-state --user 0 ${com.gentv.core.lists.PackageName.require(pkg)}")
                1 -> tv.run(command)
                else -> error("No restorable enabled state for $pkg")
            }
        }
        else -> tv.run(command)
    }
}
fun retainedInstallLog(log: ActionLog) = object : ActionLog {
    override fun append(record: ActionRecord) = log.append(record)
    override fun records() = log.records().filterNot { it.op == Op.INSTALL || it.op == Op.UNINSTALL }
}
