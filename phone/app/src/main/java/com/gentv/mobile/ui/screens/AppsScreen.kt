package com.gentv.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gentv.mobile.AppsState
import com.gentv.mobile.ui.AppDetailsSheet
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.SafetyBanner
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.TopBar
import com.gentv.mobile.ui.InfoSheet
import com.gentv.core.lists.AllAppsView
import com.gentv.core.lists.AppRow
import com.gentv.core.lists.Tier

/** Apps: every app on the TV. Tap one to turn it off or uninstall it right away, with the warning that goes with it. */
@Composable
fun AppsScreen(
    s: AppsState,
    labels: Map<String, String>,
    tvName: String?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onTurnOff: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pendingUninstall by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenFrame(top = { TopBar("返回首页", onBack, tvName) }) {
        Title("应用管理")
        Body("管理电视应用，操作前会保存备份。停用和预装应用移除可以恢复；自行安装的应用永久卸载后，需要重新安装。", color = GenTVPalette.Muted)
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = GenTVPalette.RedSoft, textColor = GenTVPalette.Red) }
        if (s.loading) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(26.dp); Spacer(Modifier.width(12.dp))
                Text("Reading every app on your TV…", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Muted)
            }
            return@ScreenFrame
        }
        if (s.rows.isEmpty()) {
            Body("暂未读取到电视应用。请确认电视连接正常后重试。")
            com.gentv.mobile.ui.TextLink("重新读取应用", onReload)
            return@ScreenFrame
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search apps", color = GenTVPalette.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = GenTVPalette.Muted) },
            trailingIcon = if (query.isEmpty()) null else ({
                IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = GenTVPalette.Muted) }
            }),
            shape = RoundedCornerShape(16.dp),
        )
        val sections = AllAppsView.sections(AllAppsView.search(s.rows, query), s.facts)
        if (sections.isEmpty()) Body("No apps match \"${query.trim()}\".")
        for (sec in sections) {
            Text("${sec.title} (${sec.size})", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, modifier = Modifier.padding(top = 4.dp))
            for (row in sec.all) AppLine(row, labels[row.pkg] ?: row.label, s.busy == row.pkg, enabled = s.busy == null) { onOpen(row.pkg) }
        }
    }
    val open = s.open?.let { pkg -> s.rows.firstOrNull { it.pkg == pkg } }
    if (open != null) AppActionSheet(open, labels[open.pkg] ?: open.label, onClose, onTurnOff, { pendingUninstall = it; onClose() })
    pendingUninstall?.let { pkg ->
        val row = s.rows.firstOrNull { it.pkg == pkg }
        if (row != null) androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(if (row.tier == Tier.YOURS) "永久卸载应用？" else "从此电视移除应用？") },
            text = { Text(if (row.tier == Tier.YOURS) "${labels[pkg] ?: row.label} 将被永久卸载，恢复功能无法找回，需要重新安装。" else "${labels[pkg] ?: row.label} 将从此电视移除，可以通过恢复功能找回。") },
            confirmButton = { androidx.compose.material3.TextButton({ pendingUninstall = null; onUninstall(pkg) }, enabled = s.busy == null) { Text("确认卸载", color = GenTVPalette.Red) } },
            dismissButton = { androidx.compose.material3.TextButton({ pendingUninstall = null }) { Text("取消") } },
        )
    }

}

@Composable
private fun AppLine(row: AppRow, name: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.pkg, name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, localize = false, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.pkg, style = MaterialTheme.typography.labelSmall, color = GenTVPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            busy -> Spinner(22.dp)
            row.locked -> Icon(Icons.Rounded.Lock, "Kept safe", Modifier.size(22.dp), tint = GenTVPalette.Muted)
            else -> Icon(Icons.Rounded.ChevronRight, null, Modifier.size(26.dp), tint = GenTVPalette.Muted)
        }
    }
}

/** The warning for this app, then the two choices. Locked apps only say why they stay. */
@Composable
private fun AppActionSheet(row: AppRow, name: String, onClose: () -> Unit, onTurnOff: (String) -> Unit, onUninstall: (String) -> Unit) {
    if (row.locked) { AppDetailsSheet(name, row.pkg, row.note, onClose); return }
    val yours = row.tier == Tier.YOURS
    InfoSheet(name, onClose) {
        Text(row.pkg, style = MaterialTheme.typography.labelMedium, color = GenTVPalette.Muted)
        if (row.note.isNotBlank()) Body(row.note.removePrefix("This TV's list keeps it on. "), color = GenTVPalette.Ink)
        when {
            yours -> Note("You installed this app. Uninstalling is permanent: Restore can't bring it back, but you can install it again from Google Play.")
            row.keptByList || row.tier == Tier.YOUR_CALL -> Note("Think first: this app may be used by your TV or remote. Turning it off is easy to undo.", color = GenTVPalette.Amber, textColor = GenTVPalette.AmberInk)
            else -> Body("Turning it off hides it and stops it from running. One tap brings it back.", color = GenTVPalette.Muted)
        }
        if (!yours) BigButton("Turn off", { onTurnOff(row.pkg) }, color = GenTVPalette.Green)
        BigButton(if (yours) "Uninstall for good" else "Uninstall for this TV", { onUninstall(row.pkg) }, color = if (yours) GenTVPalette.Red else GenTVPalette.Grey, textColor = if (yours) Color(0xFF381316) else GenTVPalette.Ink)
    }
}
