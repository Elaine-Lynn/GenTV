package com.gentv.mobile.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gentv.mobile.data.DesktopState
import com.gentv.mobile.ui.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Edits a local draft. Only Save writes to the TV, then the bridge reads it back. */
@Composable
fun DesktopScreen(state: DesktopState, onClose: () -> Unit, onRead: () -> Unit,
                  onSave: (String) -> Unit, onUndo: () -> Unit, onReport: () -> Unit,
                  onEvidence: (String, Boolean) -> Unit, onInstallPreview: (() -> Unit)? = null) {

    val snapshot = remember(state.snapshot) { state.snapshot?.let(::JSONObject) }
    var draft by remember(state.snapshot) { mutableStateOf(snapshot?.optJSONObject("config")?.toString() ?: "{}") }
    var confirmClose by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    val dirty = draft != (snapshot?.optJSONObject("config")?.toString() ?: "{}")
    fun close() { if (!state.busy) { if (dirty) confirmClose = true else onClose() } }
    BackHandler { close() }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Discard unsaved changes?") },
        confirmButton = { TextButton(onClose) { Text("Discard") } },
        dismissButton = { TextButton({ confirmClose = false }) { Text("Keep editing") } })
    var selected by remember(state.snapshot) { mutableStateOf<String?>(null) }
    var rename by remember(state.snapshot) { mutableStateOf("") }
    val config = JSONObject(draft)
    val catalog = snapshot?.optJSONArray("apps") ?: JSONArray()
    val folders = config.optJSONObject("folders") ?: JSONObject()
    val folderIds = folders.keys().asSequence().toList()
    val labels = buildMap<String, String> {
        for (i in 0 until catalog.length()) catalog.getJSONObject(i).let { put(it.getString("id"), it.getString("label")) }
    }
    val allLabels = labels.toMutableMap().apply {
        keys.removeAll { com.gentv.mobile.data.DesktopFolders.isFolder(it) && it !in folderIds }
        for (id in folderIds) put(id, folders.getJSONObject(id).getString("name"))
    }
    val savedOrder = config.optJSONArray("order").strings()
    val order = savedOrder.filter { it in allLabels } + allLabels.keys.filter { it !in savedOrder }.sorted()
    val hidden = config.optJSONArray("hidden").strings().toSet()
    fun edit(block: (JSONObject) -> Unit) { val next = JSONObject(draft); block(next); draft = next.toString() }
    fun move(id: String, target: Int) {
        val next = order.toMutableList(); val from = next.indexOf(id)
        if (from < 0) return
        next.removeAt(from); next.add(target.coerceIn(0, next.size), id)
        // Preserve missing apps, e.g. during an update, so their position is not discarded.
        val missing = savedOrder.filter { it !in allLabels && !com.gentv.mobile.data.DesktopFolders.isFolder(it) }
        missing.forEach { idMissing -> next.add(savedOrder.indexOf(idMissing).coerceAtMost(next.size), idMissing) }
        edit { it.put("order", JSONArray(next)) }
    }
    val context = LocalContext.current
    var imageTarget by remember { mutableStateOf<String?>(null) }
    val importImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = imageTarget
        if (uri != null && id != null) runCatching {
            val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
            val encoded = com.gentv.mobile.data.DesktopImages.encode(stream)
            edit { next ->
                val pictures = next.optJSONObject("images") ?: JSONObject()
                pictures.put(id, encoded); next.put("images", pictures)
                com.gentv.mobile.data.parseDesktopProfile(next.toString())
                require(android.util.Base64.encodeToString(next.toString().toByteArray(), android.util.Base64.NO_WRAP).length <= 60000) { "Profile is full. Restore another app image first." }
            }
            importError = null
        }.onFailure { importError = "Could not use image: ${it.message}" }
    }
    var createFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    if (createFolder) AlertDialog(onDismissRequest = { createFolder = false }, title = { Text("New folder") },
        text = { OutlinedTextField(folderName, { folderName = it.take(60) }, singleLine = true, label = { Text("Folder name") }) },
        confirmButton = { TextButton({
            runCatching { edit { selected = com.gentv.mobile.data.DesktopFolders.create(it, folderName) }; rename = folderName.trim(); createFolder = false }
                .onFailure { importError = it.message }
        }, enabled = folderName.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton({ createFolder = false }) { Text("Cancel") } })
    val importProfile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { buildString { val buffer = CharArray(2048); while (true) { val count = it.read(buffer); if (count < 0) break; require(length + count <= 60000) { "Profile is too large" }; append(buffer, 0, count) } } }
                ?: error("Cannot open profile")
            require(text.length <= 60000) { "Profile is too large" }
            val profile = com.gentv.mobile.data.parseDesktopProfile(text)
            importNotice = "Imported as a local draft. Review and save to apply."
            draft = profile.toString(); selected = null; importError = null
        }.onFailure { importError = "Could not import this profile: ${it.message}" }
    }
    ScreenFrame(top = { TextLink("My TV", { close() }, icon = GenIcons.Back) }, bottom = {
        if (snapshot != null) BigButton("Save and verify", {
            val next = JSONObject(draft).put("schema", 2)
            if (!next.has("order")) next.put("order", JSONArray(order))
            onSave(next.toString())
        }, enabled = !state.busy)
    }) {
        Title("Organize GenTV Home")
        Body("Arrange apps and name your HDMI devices. Changes stay in this draft until you save.")
        state.message?.let { Note(it) }
        importError?.let { Note(it) }
        importNotice?.let { Note(it) }
        if (state.busy) Spinner(26.dp)
        if (snapshot == null && !state.busy) BigButton("Read TV settings", onRead)
        if (onInstallPreview != null && !state.busy) {
            TextLink("Install and open GenTV Home preview", onInstallPreview)
            Note("Opens the source-built preview while keeping the current default Home.")
        }
        if (snapshot != null) {
            if ("folders" in (snapshot.optJSONArray("capabilities").strings())) TextButton({ folderName = ""; createFolder = true }, enabled = !state.busy && folders.length() < 30) { Text("New folder") }
            Text("Hold and drag a tile to reorder, or select it and use the move buttons.", color = GenTVPalette.Muted)
            order.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { id ->
                        val index = order.indexOf(id)
                        var width by remember { mutableFloatStateOf(1f) }
                        var dragX by remember { mutableFloatStateOf(0f) }
                        var dragY by remember { mutableFloatStateOf(0f) }
                        var height by remember { mutableFloatStateOf(1f) }
                        val currentMove by rememberUpdatedState<(Int) -> Unit> { delta -> move(id, index + delta) }
                        val name = config.optJSONObject("names")?.optString(id, allLabels[id].orEmpty()) ?: allLabels[id].orEmpty()
                        Column(Modifier.weight(1f).heightIn(min = 105.dp)
                            .onSizeChanged { width = it.width.toFloat(); height = it.height.toFloat() }
                            .background(if (selected == id) GenTVPalette.BlueSoft else GenTVPalette.Card, RoundedCornerShape(16.dp))
                            .border(1.dp, if (selected == id) GenTVPalette.Blue else GenTVPalette.Card, RoundedCornerShape(16.dp))
                            .pointerInput(id, state.busy) {
                                if (!state.busy) detectDragGesturesAfterLongPress(
                                    onDragStart = { selected = id; rename = name; dragX = 0f; dragY = 0f },
                                    onDragCancel = { dragX = 0f; dragY = 0f },
                                    onDragEnd = { currentMove((dragX / width).roundToInt() + 2 * (dragY / height).roundToInt()) },
                                    onDrag = { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y },
                                )
                            }.clickable(enabled = !state.busy) { selected = id; rename = name }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon(id.substringBefore('/'), name, size = 32.dp)
                            Text(name, style = MaterialTheme.typography.titleSmall, color = GenTVPalette.Ink)
                            val parent = folderIds.firstOrNull { id in folders.getJSONObject(it).getJSONArray("apps").strings() }
                            Text(when { id in hidden -> "Hidden from Home"; parent != null -> "In " + folders.getJSONObject(parent).getString("name"); id in folderIds -> "Folder · " + folders.getJSONObject(id).getJSONArray("apps").length() + " apps"; else -> "${index + 1} · On Home" }, color = GenTVPalette.Muted)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            selected?.let { id ->
                val isFolder = id in folderIds
                if (!isFolder && !id.startsWith("com.gentv.tv/") && "folders" in snapshot.optJSONArray("capabilities").strings()) {
                    var folderMenu by remember(id) { mutableStateOf(false) }
                    Box {
                        TextButton({ folderMenu = true }, enabled = !state.busy) { Text("Move to folder / Home") }
                        DropdownMenu(folderMenu, { folderMenu = false }) {
                            DropdownMenuItem(text = { Text("Home (outside folders)") }, onClick = { edit { com.gentv.mobile.data.DesktopFolders.assign(it, id, null) }; folderMenu = false })
                            folderIds.forEach { folder -> DropdownMenuItem(text = { Text(folders.getJSONObject(folder).getString("name")) }, onClick = {
                                runCatching { edit { com.gentv.mobile.data.DesktopFolders.assign(it, id, folder) } }.onFailure { importError = it.message }; folderMenu = false
                            }) }
                        }
                    }
                }
                if (isFolder) {
                    Body("Select an app below and use Move to folder to add it. Moving it to Home removes it from the folder. Empty folders stay available.")
                    TextButton({ edit { com.gentv.mobile.data.DesktopFolders.dissolve(it, id) }; selected = null }, enabled = !state.busy) { Text("Dissolve folder; keep apps") }
                }
                if (!isFolder && !id.startsWith("com.gentv.tv/") && "images" in snapshot.optJSONArray("capabilities").strings()) {
                    Row {
                        TextButton({ imageTarget = id; importImage.launch(arrayOf("image/*")) }, enabled = !state.busy) { Text("Choose app image") }
                        TextButton({ edit { it.optJSONObject("images")?.remove(id) } }, enabled = !state.busy && config.optJSONObject("images")?.has(id) == true) { Text("Original image") }
                    }
                    Text("Images are compressed to 320 × 180; up to 12 KiB each and 24 KiB total. Original app banners return when reset.", color = GenTVPalette.Muted)
                }
                OutlinedTextField(rename, { rename = it.take(60) }, label = { Text("App name") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ move(id, order.indexOf(id) - 1) }, enabled = !state.busy && order.indexOf(id) > 0) { Text("Move earlier") }
                    TextButton({ move(id, order.indexOf(id) + 1) }, enabled = !state.busy && order.indexOf(id) < order.lastIndex) { Text("Move later") }
                }
                Row {
                    TextButton({ edit { val names = it.optJSONObject("names") ?: JSONObject()
                        if (isFolder) { it.getJSONObject("folders").getJSONObject(id).put("name", rename.trim()); names.remove(id) } else names.put(id, rename.trim())
                        it.put("names", names) } }, enabled = !state.busy && rename.isNotBlank()) { Text("Use name") }
                    TextButton({ edit { it.put("hidden", JSONArray(if (id in hidden) hidden - id else hidden + id)) } }, enabled = !state.busy) { Text(if (id in hidden) "Show on Home" else "Hide from Home") }
                    TextButton({ edit { it.optJSONObject("names")?.remove(id) }; rename = allLabels[id].orEmpty() }, enabled = !state.busy && !isFolder) { Text("Original name") }
                }
            }
            val sources = snapshot.optJSONArray("sources") ?: JSONArray()
            if (!snapshot.optBoolean("sourcesAvailable", true)) Note("This TV does not expose an input list. App arrangement remains available.")
            if (sources.length() > 0) {
                Title("Input names")
                Body("Use names such as PlayStation, Switch or Apple TV. If a tile opens the wrong port, choose the correct input under Opens, then test picture and sound.")
                for (i in 0 until sources.length()) {
                    val source = sources.getJSONObject(i); val id = source.getString("id")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in config.optJSONArray("pinnedSources").strings(), { checked -> edit {
                            val pins = it.optJSONArray("pinnedSources").strings().toMutableList()
                            if (checked && id !in pins) pins.add(id) else if (!checked) pins.remove(id)
                            it.put("pinnedSources", JSONArray(pins))
                        } }, enabled = !state.busy)
                        Text("Pin to Home")
                    }
                    val name = config.optJSONObject("sourceNames")?.optString(id, source.getString("label")) ?: source.getString("label")
                    OutlinedTextField(name, { value -> edit { val names = it.optJSONObject("sourceNames") ?: JSONObject(); if (value.isBlank()) names.remove(id) else names.put(id, value.take(60)); it.put("sourceNames", names) } },
                        label = { Text(source.getString("label")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    var chooseTarget by remember(id) { mutableStateOf(false) }
                    val targetId = config.optJSONObject("sourceTargets")?.optString(id, id) ?: id
                    val targetLabel = (0 until sources.length()).map { sources.getJSONObject(it) }.firstOrNull { it.getString("id") == targetId }?.getString("label") ?: "Original input"
                    Box {
                        TextButton({ chooseTarget = true }, enabled = !state.busy) { Text("Opens: $targetLabel") }
                        DropdownMenu(chooseTarget, { chooseTarget = false }) {
                            for (j in 0 until sources.length()) {
                                val target = sources.getJSONObject(j)
                                DropdownMenuItem(text = { Text(target.getString("label")) }, onClick = {
                                    edit {
                                        val targets = it.optJSONObject("sourceTargets") ?: JSONObject()
                                        if (target.getString("id") == id) targets.remove(id) else targets.put(id, target.getString("id"))
                                        it.put("sourceTargets", targets)
                                    }
                                    chooseTarget = false
                                })
                            }
                        }
                    }
                    var chooseDevice by remember(id) { mutableStateOf(false) }
                    Box {
                        TextButton({ chooseDevice = true }, enabled = !state.busy) { Text("Device: " + (config.optJSONObject("sourceDevices")?.optString(id)?.takeIf { it.isNotBlank() } ?: "HDMI")) }
                        DropdownMenu(chooseDevice, { chooseDevice = false }) {
                            for ((device, title) in listOf("" to "HDMI", "playstation" to "PlayStation", "switch" to "Switch", "xbox" to "Xbox", "appletv" to "Apple TV", "computer" to "Computer")) {
                                DropdownMenuItem(text = { Text(title) }, onClick = {
                                    edit {
                                        val devices = it.optJSONObject("sourceDevices") ?: JSONObject()
                                        val names = it.optJSONObject("sourceNames") ?: JSONObject()
                                        if (device.isEmpty()) { devices.remove(id); names.remove(id) }
                                        else { devices.put(id, device); names.put(id, title) }
                                        it.put("sourceDevices", devices); it.put("sourceNames", names)
                                    }
                                    chooseDevice = false
                                })
                            }
                        }
                    }
                }
            }
            Row {
                TextButton({ importProfile.launch(arrayOf("application/json", "text/plain")) }, enabled = !state.busy) { Text("Import profile") }
                TextButton({
                    val exported = JSONObject(draft).put("schema", 2)
                    if (!exported.has("order")) exported.put("order", JSONArray(order))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, exported.toString(2)).putExtra(Intent.EXTRA_SUBJECT, "GenTV desktop profile"), "Share profile"))
                }, enabled = !state.busy) { Text("Export profile") }
            }
            Text("Profiles include your app and input names. Import changes the draft; Save validates and applies it to this TV.", color = GenTVPalette.Muted)
            Title("Effects")
            Row {
                for (mode in listOf("auto", "smooth", "full")) TextButton({ edit { it.put("effects", mode) } }, enabled = !state.busy) {
                    Text((if (config.optString("effects", "auto") == mode) "• " else "") + mode.replaceFirstChar { it.uppercase() })
                }
            }
            Text("TV currently uses ${if (snapshot.optBoolean("smooth")) "smooth" else "full"} focus scaling.", color = GenTVPalette.Muted)
            if (!state.busy && snapshot.optBoolean("canUndo")) TextLink("Undo last saved change", onUndo)
        }
        BigButton("Create verification report", onReport, enabled = !state.busy)
        state.report?.let { report ->
            Body(report)
            for (check in listOf("Physical remote Home", "Wake from standby", "Return after reboot", "HDMI picture and sound", "D-pad from every edge", "Debugging closed after use")) {
                Text(check, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton({ onEvidence(check, true) }, enabled = !state.busy) { Text("I tested: passed") }
                    TextButton({ onEvidence(check, false) }, enabled = !state.busy) { Text("I tested: failed") }
                }
            }
            TextLink("Share verification report", {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report).putExtra(Intent.EXTRA_SUBJECT, "GenTV desktop verification"), "Share report"))
            })
        }
    }
}
private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
