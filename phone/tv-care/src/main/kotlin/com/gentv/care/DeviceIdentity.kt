package com.gentv.care
import com.gentv.core.ops.*
import kotlinx.serialization.json.*
import java.io.File
import java.security.SecureRandom

object DeviceIdentity {
    const val MARKER = "/data/local/tmp/gentv/care-device-id"
    fun hardware(props: Map<String, String>) = props["ro.serialno"].orEmpty().ifBlank { props["ro.boot.serialno"].orEmpty() }.takeUnless { it.lowercase() in setOf("unknown", "null", "0123456789abcdef") || it.all { c -> c == '0' } }.orEmpty()
    fun firmware(props: Map<String, String>): String {
        props["ro.build.fingerprint"]?.takeIf { it.isNotBlank() }?.let { return it }
        val fields = listOf("ro.build.id", "ro.build.version.incremental", "ro.build.version.release", "ro.build.version.sdk").map { props[it].orEmpty() }
        require(fields.count { it.isNotBlank() } >= 2) { "Read SDK and build/version evidence before changing this older TV" }
        return "derived:" + sha256(fields.joinToString("|").toByteArray())
    }
    suspend fun bind(tv: TvShell, props: Map<String, String>, directory: File, deviceConfirmed: Boolean) {
        val hardware = hardware(props)
        if (hardware.isNotBlank()) {
            privateWrite(File(directory, "identity.json"), buildJsonObject { put("method", "hardware"); put("value", hardware) }.toString())
            return
        }
        require(deviceConfirmed) { "Hardware serial unavailable. Confirm this authorised endpoint is the intended TV, then resume backup with --device-confirmed and a new session path" }
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        // Record the recovery binding intent before creating a device-local marker.
        privateWrite(File(directory, "identity.json"), buildJsonObject { put("method", "marker"); put("value", nonce) }.toString())
        val existing = tv.run("if [ -f $MARKER ]; then cat $MARKER; fi").trim()
        val value = if (Regex("[a-f0-9]{64}").matches(existing)) existing else {
            require(existing.isBlank()) { "Unexpected identity marker; inspect it without overwriting" }
            tv.run("mkdir -p ${Commands.MIRROR_DIR} && printf '%s' '$nonce' > $MARKER")
            check(tv.run("cat $MARKER").trim() == nonce) { "Device identity marker not confirmed; no settings changed" }
            nonce
        }
        privateWrite(File(directory, "identity.json"), buildJsonObject { put("method", "marker"); put("value", value) }.toString())
    }
    suspend fun verify(tv: TvShell, props: Map<String, String>, directory: File, baseline: Backup) {
        val identity = File(directory, "identity.json")
        if (!identity.exists()) {
            require(hardware(baseline.props).isNotBlank() && hardware(props) == hardware(baseline.props)) { "Legacy session identity cannot be confirmed" }
            return
        }
        val stored = Json.parseToJsonElement(identity.readText()).jsonObject
        val value = stored["value"]!!.jsonPrimitive.content
        when (stored["method"]!!.jsonPrimitive.content) {
            "hardware" -> require(hardware(props) == value) { "Physical device differs from session" }
            "marker" -> {
                require(tv.run("cat $MARKER").trim() == value) { "Device marker differs or disappeared; preserve session, do not guess recovery target" }
                for (key in listOf("ro.product.model", "ro.product.device", "ro.product.manufacturer"))
                    require(props[key] == baseline.props[key]) { "Device profile differs from confirmed target" }
            }
            else -> error("Unknown session identity method")
        }
    }
}
