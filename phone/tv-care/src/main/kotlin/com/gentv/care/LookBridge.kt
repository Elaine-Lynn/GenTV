package com.gentv.care

import com.gentv.core.lists.HomeApp
import com.gentv.core.ops.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64

/** Config-only theme transport. Wallpaper files and schema2 layout are never removed. */
class LookBridge(private val tv: TvShell, private val session: Session) {
    private val pkg = HomeApp.GENTV.pkg
    suspend fun read(): JsonObject {
        val output = tv.run(Commands.lookReadConfig(pkg)).trim()
        val encoded = Regex("config_base64=([A-Za-z0-9+/=]*)").find(output)?.groupValues?.get(1)
        require(encoded != null || output.startsWith("{")) { "Theme configuration unavailable; do not overwrite it" }
        val text = if (encoded != null) String(Base64.getDecoder().decode(encoded)) else output
        return if (text.isBlank()) buildJsonObject {} else Json.parseToJsonElement(text).jsonObject
    }
    private suspend fun write(config: JsonObject) {
        require(Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok"))
        for ((name, bytes) in listOf("config.json" to config.toString().toByteArray(), "theme_revision" to System.currentTimeMillis().toString().toByteArray())) {
            tv.push(bytes, Commands.lookStage(name)); tv.run(Commands.lookPut(pkg, name))
        }
        check(read() == config) { "Theme config not confirmed; read before retrying" }
        check(Parse.lines(tv.run(Commands.restartLook(pkg))).contains("restarted")) { "Theme written but launcher restart not confirmed" }
    }
    suspend fun save(config: JsonObject, expectedHash: String, allowHelpers: Boolean) {
        require(config.toString().length <= 60000)
        fun enabled(key: String) = config[key]?.jsonPrimitive?.content in setOf("true", "1")
        require(!(enabled("power_on_home") && enabled("boot_to_live_tv"))) { "Choose Home or Live TV at power-on, never both" }
        val before = read()
        require(sha256(before.toString().toByteArray()) == expectedHash) { "Theme changed; export and review again" }
        val helperKeys = (config.keys + before.keys).filter { it.contains("key_service", true) || it == "power_on_home" || it == "boot_to_live_tv" }
        require(allowHelpers || helperKeys.all { config[it] == before[it] }) { "Helper/boot changes require explicit owner choice" }
        val baseline = File(session.directory, "look-original.json")
        if (!baseline.exists()) privateWrite(baseline, before.toString())
        privateWrite(File(session.directory, "look-last-intent.json"), config.toString())
        write(config)
    }
    suspend fun restoreIfChanged(): Boolean {
        val baseline = File(session.directory, "look-original.json")
        if (!baseline.exists()) return true
        val original = Json.parseToJsonElement(baseline.readText()).jsonObject
        val current = read()
        if (current == original) return true
        val intended = Json.parseToJsonElement(File(session.directory, "look-last-intent.json").readText()).jsonObject
        require(current == intended) { "Theme changed outside this session; preserve it and review the saved baseline manually" }
        write(original)
        return read() == original
    }
}
