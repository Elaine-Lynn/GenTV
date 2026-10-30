package com.gentv.care

import com.gentv.core.ops.TvShell
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64

/** JVM transport for the local docs/desktop-protocol.md; TV retains authoritative schema validation. */
class DesktopBridge(private val tv: TvShell, private val session: Session) {
    suspend fun read(): JsonObject = exchange("read", -1, null)
    private suspend fun exchange(operation: String, revision: Int, config: JsonObject?): JsonObject {
        require(operation in setOf("read", "save"))
        val payload = config?.let { Base64.getEncoder().encodeToString(it.toString().toByteArray()).also { p -> require(p.length <= 60000) } }
        val output = tv.run("am broadcast --user 0 -n com.gentv.tv/com.gentv.tv.DesktopReceiver --es operation $operation --ei revision $revision" + (payload?.let { " --es payload '$it'" } ?: ""))
        check(!output.contains("GENTV_ERROR:")) { "TV refused configuration; read again before changing anything" }
        val encoded = Regex("GENTV1:([A-Za-z0-9+/=]+)").find(output)?.groupValues?.get(1) ?: error("No supported desktop bridge response")
        val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded))).jsonObject
        check(value["protocol"]?.jsonPrimitive?.int == 1 && value["ok"]?.jsonPrimitive?.boolean == true && value["backend"]?.jsonPrimitive?.content == "gentv") { "Unsupported desktop bridge" }
        return value
    }
    suspend fun save(config: JsonObject, revision: Int): JsonObject {
        require(config["schema"]?.jsonPrimitive?.int == 2) { "Only schema 2 profiles are accepted" }
        require(config.keys.all { it in setOf("schema", "order", "hidden", "names", "effects", "pinnedSources", "sourceNames", "sourceDevices", "sourceTargets", "folders", "images") }) { "Unknown profile field" }
        val before = read()
        require(before["revision"]!!.jsonPrimitive.int == revision) { "Desktop changed; read and review a fresh profile" }
        val baseline = File(session.directory, "desktop-original.json")
        if (!baseline.exists()) privateWrite(baseline, before.toString())
        // Save intent before the broadcast. An interrupted write must be reconciled by reading, never replayed.
        privateWrite(File(session.directory, "desktop-last-intent.json"), buildJsonObject { put("revision", revision); put("config", config) }.toString())
        val saved = exchange("save", revision, config)
        check(saved["revision"]!!.jsonPrimitive.int > revision && config.all { (key, value) -> saved["config"]!!.jsonObject[key] == value }) { "Configuration write not confirmed; read before retrying" }
        privateWrite(File(session.directory, "desktop-last-confirmed.json"), saved.toString())
        return saved
    }
    suspend fun restoreIfChanged(): Boolean {
        val baseline = File(session.directory, "desktop-original.json")
        if (!baseline.exists()) return true
        val original = Json.parseToJsonElement(baseline.readText()).jsonObject["config"]!!.jsonObject
        val current = read()
        if (current["config"] == original) return true
        val intent = Json.parseToJsonElement(File(session.directory, "desktop-last-intent.json").readText()).jsonObject
        val intendedRevision = intent["revision"]!!.jsonPrimitive.int + 1
        require(current["config"] == intent["config"] && current["revision"]!!.jsonPrimitive.int == intendedRevision) {
            "Desktop changed outside this session; preserve it and review the saved baseline manually"
        }
        val restored = exchange("save", current["revision"]!!.jsonPrimitive.int, original)
        return restored["config"] == original
    }
}
