package com.gentv.care
import com.gentv.core.lists.PackageName
import kotlinx.serialization.json.*

/** Structured arguments compiled into the existing local protocols; callers never need to edit JSON. */
data class ConfigurationDraft(val layout: JsonObject, val look: JsonObject)
object Configuration {
    fun draft(snapshot: JsonObject, look: JsonObject, changes: Map<String, String>): ConfigurationDraft {
        require(changes.isNotEmpty()) { "Specify a configuration change" }
        val layout = snapshot["config"]!!.jsonObject.toMutableMap()
        val theme = look.toMutableMap()
        val apps = snapshot["apps"]!!.jsonArray.map { it.jsonObject }
        fun resolve(value: String): String {
            val matches = apps.filter { it["id"]?.jsonPrimitive?.content == value || it["package"]?.jsonPrimitive?.content == value }
            require(matches.size == 1) { "Application is missing or ambiguous: $value; select an exact id from desktop-export" }
            return matches.single()["id"]!!.jsonPrimitive.content
        }
        fun text(value: String): String {
            require(value.isNotBlank() && value.length <= 60 && value.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() })
            return value
        }
        for ((key, value) in changes) when (key) {
            "columns" -> { val count = value.toInt(); require(count in 1..8); theme["columns"] = JsonPrimitive(count) }
            "greeting" -> theme["greeting_name"] = JsonPrimitive(text(value))
            "effects" -> { require(value in setOf("auto", "smooth", "full")); layout["effects"] = JsonPrimitive(value) }
            "dock" -> {
                val packages = value.split(',').map { it.trim() }
                require(packages.isNotEmpty() && packages.size <= 8 && packages.distinct().size == packages.size && packages.all(PackageName::isValid))
                val ids = packages.map(::resolve)
                theme["dock_apps"] = JsonArray(packages.map(::JsonPrimitive))
                val order = layout["order"]!!.jsonArray.map { it.jsonPrimitive.content }
                layout["order"] = JsonArray((ids + order.filterNot { it in ids }).map(::JsonPrimitive))
            }
            "hide-app", "show-app" -> {
                val id = resolve(value); val hidden = layout["hidden"]!!.jsonArray.map { it.jsonPrimitive.content }.toMutableSet()
                if (key == "hide-app") hidden += id else hidden -= id
                layout["hidden"] = JsonArray(hidden.map(::JsonPrimitive))
            }
            "rename-app" -> {
                val pair = value.split('=', limit = 2); require(pair.size == 2)
                val names = layout["names"]!!.jsonObject.toMutableMap()
                names[resolve(pair[0])] = JsonPrimitive(text(pair[1])); layout["names"] = JsonObject(names)
            }
            "boot" -> {
                require(value in setOf("home", "live-tv", "system"))
                theme["power_on_home"] = JsonPrimitive(value == "home")
                theme["boot_to_live_tv"] = JsonPrimitive(value == "live-tv")
                theme["power_on_home_always"] = JsonPrimitive(false)
            }
            "settings-key", "input-key" -> {
                val keys = value.split(',').map { it.toInt() }; require(keys.isNotEmpty() && keys.size <= 10 && keys.all { it in 1..1000 })
                theme[if (key == "settings-key") "settings_key_keycodes" else "input_key_keycodes"] = JsonArray(keys.distinct().map(::JsonPrimitive))
            }
            "app-key" -> {
                val parts = value.split(':', limit = 2); require(parts.size == 2 && Regex("KEYCODE_[A-Z0-9_]+|[0-9]{1,4}").matches(parts[0]) && PackageName.isValid(parts[1]))
                resolve(parts[1])
                val mappings = theme["app_keys"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.filterNot { it.substringBefore(':') == parts[0] }
                theme["app_keys"] = JsonArray((mappings + value).map(::JsonPrimitive))
            }
            else -> error("Unknown configuration option")
        }
        return ConfigurationDraft(JsonObject(layout), JsonObject(theme))
    }
}
