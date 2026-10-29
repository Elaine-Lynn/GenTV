package com.gentv.mobile.data

import android.util.Base64
import com.gentv.core.ops.TvShell
import org.json.JSONObject

/** Versioned bridge to GenTV Home. Uses the existing authenticated ADB session. */
class DesktopClient(private val shell: TvShell) {
    suspend fun read(): JSONObject = exchange("read")
    suspend fun save(config: JSONObject, revision: Int = -1): JSONObject {
        parseDesktopProfile(config.toString())
        val response = exchange("save", config, revision)
        val saved = response.getJSONObject("config")
        check(response.getInt("revision") > revision && config.keys().asSequence().all { key ->
            saved.has(key) && saved.get(key).toString() == config.get(key).toString()
        }) { "The TV did not confirm the requested settings. Read it again before making another change." }
        return response
    }
    suspend fun undo(revision: Int) = exchange("undo", revision = revision)

    private suspend fun exchange(operation: String, config: JSONObject? = null, revision: Int = -1): JSONObject {
        val payload = config?.let {
            Base64.encodeToString(it.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP).also { encoded ->
                require(encoded.length <= 60000) { "Desktop settings are too large." }
            }
        }
        // Operation is internal, payload is base64. No user-controlled shell syntax is interpolated.
        val output = shell.run("am broadcast --user 0 -n com.gentv.tv/com.gentv.tv.DesktopReceiver --es operation $operation --ei revision $revision" +
            (payload?.let { " --es payload '$it'" } ?: ""))
        return decode(output)
    }
    companion object {
        fun decode(output: String): JSONObject {
            check(!output.contains("GENTV_ERROR:")) { "The TV refused this profile, could not save it, or its settings changed on another device. Read the TV again before retrying; no change is confirmed." }
            val encoded = Regex("GENTV1:([A-Za-z0-9+/=]+)").find(output)?.groupValues?.get(1)
                ?: error("GenTV Home with desktop management is not available. Install the matching GenTV Home build, then reconnect. No settings were confirmed.")
            val result = JSONObject(String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8))
            check(result.optInt("protocol") == 1 && result.optBoolean("ok")) { "Unsupported desktop response." }
            return result
        }
    }
}

data class DesktopState(
    val busy: Boolean = false,
    val snapshot: String? = null,
    val message: String? = null,
    val report: String? = null,
)

/** Deliberately collects only allowlisted values, never raw getprop/dumpsys/settings exports. */
class DesktopReport(private val shell: TvShell) {
    suspend fun collect(): String {
        val model = clean(shell.run("getprop ro.product.model"))
        val android = clean(shell.run("getprop ro.build.version.release"))
        val firmware = clean(shell.run("getprop ro.build.display.id"))
        val home = shell.run("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        val installed = shell.run("pm path com.gentv.tv").lineSequence().any { it.startsWith("package:") }
        val bridge = try { DesktopClient(shell).read() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { null }
        val component = Regex("(?:[A-Za-z0-9_]+\\.)+[A-Za-z0-9_]+/[A-Za-z0-9_.$]+").findAll(home).lastOrNull()?.value
        return buildString {
            appendLine("# GenTV desktop verification")
            appendLine("Model: $model\nAndroid: $android\nFirmware: $firmware")
            appendLine("\n| Check | Result |\n| --- | --- |")
            appendLine("| GenTV Home installed | ${if (installed) "PASS" else "FAIL"} |")
            appendLine("| Default Home is GenTV | ${if (component == "com.gentv.tv/.MainActivity" || component == "com.gentv.tv/com.gentv.tv.MainActivity") "PASS" else if (component == null) "UNKNOWN" else "FAIL"} |")
            appendLine("| Desktop configuration bridge | ${if (bridge != null) "PASS" else "UNAVAILABLE"} |")
            for (check in listOf("Physical remote Home", "Wake from standby", "Return after reboot", "HDMI picture and sound", "D-pad from every edge", "Debugging closed after use"))
                appendLine("| $check | NOT TESTED |")
            appendLine("\nA successful install or injected key does not certify the physical remote, reboot or HDMI.")
            bridge?.let {
                appendLine("\nEffects: ${if (it.optBoolean("smooth")) "smooth" else "full"}")
                appendLine("Input mappings: ${it.optJSONObject("config")?.optJSONObject("sourceTargets")?.length() ?: 0}")
            }
        }
    }
    private fun clean(value: String) = com.gentv.core.ops.LogRedactor.redact(value.trim())
        .replace(Regex("[\\r\\n|]"), " ").take(160).ifBlank { "UNKNOWN" }
}

/** Validates imported drafts before the UI reads nested fields. TV validates independently on save. */
fun parseDesktopProfile(text: String): JSONObject {
    require(text.length <= 60000) { "Profile is too large" }
    val value = JSONObject(text)
    require(value.get("schema") == 2) { "Unsupported profile" }
    val allowed = setOf("schema", "order", "hidden", "names", "effects", "pinnedSources", "sourceNames", "sourceDevices", "sourceTargets", "folders", "images")
    require(value.keys().asSequence().all { it in allowed }) { "Unknown profile field" }
    require(listOf("order", "hidden", "names", "effects").all { value.has(it) }) { "Incomplete profile" }
    for (key in listOf("order", "hidden")) if (value.has(key)) {
        val array = value.getJSONArray(key)
        require(array.length() <= 500)
        val seen = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val id = array.get(i)
            require(id is String && id.length <= 256 && id.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")) && seen.add(id)) { "Invalid app key" }
        }
    }
    for (key in listOf("names", "sourceNames")) if (value.has(key)) {
        val names = value.getJSONObject(key)
        require(names.length() <= 500)
        for (id in names.keys()) {
            if (key == "names") require(id.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"))) { "Invalid component" }
            val name = names.get(id)
            require(id.length <= 256 && name is String && name.isNotBlank() && name.length <= 60 && name.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) { "Invalid name" }
        }
    }
    if (value.has("pinnedSources")) {
        val pins = value.getJSONArray("pinnedSources")
        require(pins.length() <= 100)
        val seen = mutableSetOf<String>()
        for (i in 0 until pins.length()) { val id = pins.get(i); require(id is String && id.isNotBlank() && id.length <= 256 && seen.add(id)) }
    }
    if (value.has("sourceDevices")) {
        val devices = value.getJSONObject("sourceDevices")
        require(devices.length() <= 100)
        for (id in devices.keys()) require(id.length <= 256 && devices.get(id) in setOf("playstation", "switch", "xbox", "appletv", "computer")) { "Unknown input device" }
    }
    if (value.has("sourceTargets")) {
        val targets = value.getJSONObject("sourceTargets")
        require(targets.length() <= 100)
        for (id in targets.keys()) {
            val target = targets.get(id)
            require(id.length <= 256 && target is String && target.length <= 256) { "Invalid input mapping" }
        }
    }
    DesktopImages.validate(value)
    require(value.optString("effects", "auto") in setOf("auto", "smooth", "full")) { "Invalid effects mode" }
    return value
}
