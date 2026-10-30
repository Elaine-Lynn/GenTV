package com.gentv.care

import com.gentv.core.lists.*
import com.gentv.core.ops.*
import kotlinx.serialization.json.*
import java.io.File

/** Evidence collector only: no permission, package or settings mutations. */
class PrivacyAudit(private val tv: TvShell, private val repo: File) {
    suspend fun collect(): JsonObject {
        val installed = Parse.packages(tv.run(Commands.LIST_PACKAGES))
        require(installed.isNotEmpty()) { "Cannot audit without an installed package inventory" }
        val disabled = Parse.packages(tv.run(Commands.LIST_DISABLED))
        val nominated = linkedMapOf<String, MutableList<String>>()
        for (setting in listOf("voice_interaction_service", "assistant", "voice_recognition_service")) {
            val owner = Parse.component(tv.run(Settings.get("secure", setting)))?.substringBefore('/')
            if (owner in installed) nominated.getOrPut(owner!!) { mutableListOf() }.add(setting)
        }
        val notes = localCatalog(repo).lists.flatMap { list -> (list.entries + list.optional).map { it.pkg to (list.id to it) } }.groupBy({ it.first }, { it.second })
        val findings = buildJsonArray {
            for (pkg in installed.sorted()) {
                if (!PackageName.isValid(pkg)) continue
                val entries = notes[pkg].orEmpty()
                val group = entries.map { Grouping.groupOf(it.second) }
                val nameHint = Regex("voice|assistant|speech|katniss|alexa|acr|samba|inscape|alphonso|analytics|telemetry|tracking|feedback", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
                if (pkg !in nominated && !nameHint && group.none { it == Group.VOICE || it == Group.ADS_TRACKING }) continue
                val details = tv.run("dumpsys package $pkg")
                var operations = tv.run("cmd appops get $pkg RECORD_AUDIO")
                if (operations.contains("not found", true) || operations.contains("unknown command", true)) operations = tv.run("appops get $pkg RECORD_AUDIO")
                val audioGrant = Regex("android\\.permission\\.RECORD_AUDIO:\\s*granted=true").containsMatchIn(details)
                val access = Regex("(?i)(?<![A-Za-z])time=|lastAccessTime=|\\bAccess:").containsMatchIn(operations)
                val raw = operations.lineSequence().filter { it.contains("RECORD_AUDIO") || it.contains("Access:") || it.contains("Reject:") }.take(12).joinToString("\n").take(3000)
                val text = entries.joinToString(" ") { it.second.section + " " + it.second.note }
                val category = when {
                    pkg in nominated || Group.VOICE in group -> "voice"
                    Regex("(?i)\\bacr\\b|inscape|samba|alphonso").containsMatchIn(pkg + " " + text) -> "acr-candidate"
                    else -> "ads-telemetry-candidate"
                }
                add(buildJsonObject {
                    put("package", pkg); put("category", category); put("disabled", pkg in disabled)
                    put("systemNominations", buildJsonArray { nominated[pkg].orEmpty().forEach { add(it) } })
                    put("evidenceLevel", if (pkg in nominated) "system-setting" else if (entries.isNotEmpty()) "local-reference" else "name-only")
                    put("recordAudioGranted", audioGrant); put("recordedAudioAccess", access)
                    put("sharedUid", details.contains("sharedUserId=") || details.contains("sharedUser="))
                    put("appOpsEvidence", LogRedactor.redact(raw))
                    put("appOpsAvailable", raw.isNotBlank())
                    put("references", buildJsonArray { entries.take(5).forEach { (id, entry) -> add(buildJsonObject { put("modelRecord", id); put("note", LogRedactor.redact(entry.note.take(300))) }) } })
                    put("impact", if (category == "voice") "Disabling can remove voice search or the remote microphone button" else "Review the actual services and local model notes before selecting for cleanup")
                })
            }
        }
        return buildJsonObject {
            put("kind", "privacy-audit"); put("schema", 1); put("readOnly", true)
            put("findings", findings)
            put("limits", "Names, permissions and shared-UID AppOps cannot prove spying, recording intent or network upload. Missing evidence means unknown, not absent. No packages were disabled.")
        }
    }
}

fun saveReport(file: File, report: JsonObject) {
    require(!file.exists() && !File(file.path + ".md").exists()) { "Report destination already exists" }
    file.parentFile?.let { parent -> if (!parent.exists()) { parent.mkdirs(); restrict(parent, true) } }
    privateWrite(file, report.toString())
    val markdown = buildString {
        appendLine("# GenTV ${report["kind"]?.jsonPrimitive?.content ?: "report"}")
        appendLine()
        if (report["findings"] != null) {
            appendLine("| Package | Category | Evidence | Audio access recorded | Disabled |")
            appendLine("| --- | --- | --- | --- | --- |")
            for (item in report["findings"]!!.jsonArray) {
                val value = item.jsonObject
                appendLine("| ${value["package"]!!.jsonPrimitive.content} | ${value["category"]!!.jsonPrimitive.content} | ${value["evidenceLevel"]!!.jsonPrimitive.content} | ${value["recordedAudioAccess"]} | ${value["disabled"]} |")
            }
        } else appendLine("```json\n${report["summary"] ?: report}\n```")
        appendLine(); appendLine(report["limits"]?.jsonPrimitive?.content.orEmpty())
        appendLine(); appendLine("The adjacent JSON retains measurements, sample counts and available evidence.")
    }
    privateWrite(File(file.path + ".md"), markdown)
}
