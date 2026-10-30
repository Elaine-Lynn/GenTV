package com.gentv.core.ops

import com.gentv.core.lists.HomeApp
import com.gentv.core.lists.PackageName
import com.gentv.core.lists.SourceProfiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A look for the compatible desktop home screen: its themes/ folders, or the
 * owner's own photo. [config] is the theme's config.json; [wallpapers] is a rotating collection.
 */
class LookFiles(
    val id: String,
    val config: String,
    val wallpaper: ByteArray? = null,
    val wallpapers: List<Pair<String, ByteArray>> = emptyList(),
    val captions: ByteArray? = null,
) {
    init {
        require(NAME.matches(id))
        wallpapers.forEach { require(NAME.matches(it.first)) { "unsupported wallpaper name" } }
    }

    companion object {
        val NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")
    }
}

/**
 * Puts a look on the compatible desktop home screen: every file is
 * staged in /data/local/tmp, then written through the launcher's ThemeProvider so the launcher owns
 * it (on Android 14 a file the shell creates in Android/data can't be read by the app), with cp as
 * the fallback for launchers or Android versions without `content write`. The owner's dock is kept.
 * Nothing here changes which apps are on or off, so it isn't logged for Restore; uninstalling the
 * home screen removes its files.
 */
class LauncherLook(private val tv: TvShell, private val clock: () -> Long = System::currentTimeMillis) {
    private val pkg = HomeApp.GENTV.pkg

    /** True when the look is on the TV and Home was restarted with it. */
    suspend fun apply(look: LookFiles): Boolean {
        if (!Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok")) return false
        val current = currentConfig()
        val dock = dock(current)
        put("config.json", merged(look.config, dock, current).toByteArray())
        if (look.wallpaper != null) put("wallpaper.jpg", look.wallpaper) else tv.run(Commands.lookDelete(pkg, "wallpaper.jpg"))
        tv.run(Commands.lookClearWallpapers(pkg))
        for ((name, bytes) in look.wallpapers) put("wallpapers/$name", bytes)
        if (look.captions != null) put("wallpapers/captions.txt", look.captions) else tv.run(Commands.lookDelete(pkg, "wallpapers/captions.txt"))
        put("theme_revision", clock().toString().toByteArray())
        return Parse.lines(tv.run(Commands.restartLook(pkg))).contains("restarted")
    }

    /** Writes the dock alone, as the handover does after installing the home screen. */
    suspend fun writeDock(dockApps: List<String>) {
        if (!Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok")) return
        val current = currentConfig().toMutableMap()
        current.putAll(Json.parseToJsonElement(Commands.dockConfig(dockApps)).jsonObject)
        put("config.json", JsonObject(current).toString().toByteArray())
        put("theme_revision", clock().toString().toByteArray())
    }

    private suspend fun put(name: String, bytes: ByteArray) {
        tv.push(bytes, Commands.lookStage(name))
        tv.run(Commands.lookPut(pkg, name))
        if (name == "config.json") {
            check(currentConfig() == Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject) {
                "The TV did not save the launcher configuration"
            }
        }
    }

    /** Fill missing device routing fields; an owner's explicit values always win. No Home change. */
    suspend fun configureSources(profile: String?) {
        if (profile == null) return
        val selected = SourceProfiles.routing(Json.parseToJsonElement(profile).jsonObject)
        if (selected.isEmpty()) return
        check(Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok")) { "Cannot prepare input configuration" }
        val current = currentConfig()
        val out = selected.toMutableMap().apply { putAll(current) }
        if (out == current) return
        put("config.json", JsonObject(out).toString().toByteArray())
        put("theme_revision", clock().toString().toByteArray())
    }

    // A failed read must stop a write, rather than erase the owner's routing configuration.
    private suspend fun currentConfig(): JsonObject {
        val output = tv.run(Commands.lookReadConfig(pkg)).trim()
        val encoded = Regex("config_base64=([A-Za-z0-9+/=]*)").find(output)?.groupValues?.get(1)
        val text = if (encoded != null) String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8) else output
        return if (text.isEmpty()) JsonObject(emptyMap()) else Json.parseToJsonElement(text).jsonObject
    }

    private fun dock(config: JsonObject): List<String> =
        (config["dock_apps"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }.filter(PackageName::isValid)

    companion object {
        /** The theme's settings with the owner's dock in place of the theme's sample dock. */
        fun merged(config: String, dock: List<String>, current: JsonObject = JsonObject(emptyMap())): String {
            val theme = runCatching { Json.parseToJsonElement(config).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            val out = theme.toMutableMap()
            // Hardware routing belongs to the TV, not to the selected wallpaper/theme.
            out.putAll(SourceProfiles.routing(current))
            if (dock.isNotEmpty()) out["dock_apps"] = JsonArray(dock.map { JsonPrimitive(it) })
            else out.remove("dock_apps")
            // Editing stays on unless the theme itself says otherwise.
            for (k in Commands.EDITABLE_HOME) out.putIfAbsent(k, JsonPrimitive(true))
            return JsonObject(out).toString() + "\n"
        }
    }
}
