package com.gentv.care
import com.gentv.core.ops.*

/** Read-only command negotiation for older package managers. No fake properties or guessed Home. */
class LegacyShell(private val tv: TvShell) : TvShell by tv {
    private val homeArgs = "--brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    override suspend fun run(command: String): String {
        val result = tv.run(command)
        if (command == Commands.QUERY_HOME && Parse.componentsIn(result).isEmpty()) {
            val pm = tv.run("pm query-activities $homeArgs")
            if (Parse.componentsIn(pm).isNotEmpty()) return pm
            val components = homeComponents(tv.run("dumpsys package"))
            return components.joinToString("\n")
        }
        if (command == Commands.RESOLVE_HOME && Parse.component(result) == null) {
            val pm = tv.run("pm resolve-activity $homeArgs")
            if (Parse.component(pm) != null) return pm
            val activity = tv.run("dumpsys activity activities")
            val explicit = Regex("(?m)mHomeActivity[^\\n]*?((?:[A-Za-z0-9_]+\\.)+[A-Za-z0-9_]+/[A-Za-z0-9_.$]+)").find(activity)?.groupValues?.get(1)
            if (explicit != null) return explicit
            val processes = tv.run("dumpsys activity")
            val owner = Regex("(?m)mHomeProcess[^\\n]*?\\d+:((?:[A-Za-z0-9_]+\\.)+[A-Za-z0-9_]+)/").find(processes)?.groupValues?.get(1)
            if (owner != null) {
                val candidates = homeComponents(tv.run("dumpsys package $owner")).filter { it.substringBefore('/') == owner }
                if (candidates.size == 1) return candidates.single()
            }
        }
        return result
    }
    companion object {
        fun homeComponents(dump: String): Set<String> {
            val found = linkedSetOf<String>(); var component: String? = null; var inActivities = false
            for (line in dump.lineSequence()) {
                if (line.contains("Activity Resolver Table:", true)) { inActivities = true; component = null; continue }
                if (line.contains("Resolver Table:", true) && !line.contains("Activity Resolver Table:", true)) { inActivities = false; component = null }
                if (!inActivities) continue
                val candidate = Regex("(?:^|\\s)((?:[A-Za-z0-9_]+\\.)+[A-Za-z0-9_]+/[A-Za-z0-9_.$]+)(?:\\s|$)").find(line)?.groupValues?.get(1)
                if (candidate != null) component = candidate
                if (line.contains("Category:") && line.contains("android.intent.category.HOME") && component != null) found += component
            }
            return found
        }
    }
}
