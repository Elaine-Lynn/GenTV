package com.gentv.mobile.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gentv.mobile.BackupStatus
import com.gentv.mobile.ReviewState
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.CardBox
import com.gentv.mobile.ui.ChipRow
import com.gentv.mobile.ui.IconBadge
import com.gentv.mobile.ui.InfoChip
import com.gentv.mobile.ui.MoreInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.vector.ImageVector
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Bubble
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.TextLink
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.Toggle
import com.gentv.core.lists.Candidate
import com.gentv.core.lists.Group

private fun Group.badge(): Pair<Color, ImageVector> = when (this) {
    Group.ADS_TRACKING -> GenTVPalette.Blue to Icons.Rounded.Campaign
    Group.PRELOADED -> GenTVPalette.Orange to Icons.Rounded.Apps
    Group.VOICE -> GenTVPalette.Purple to Icons.Rounded.Mic
    Group.CASTING -> GenTVPalette.Blue to Icons.Rounded.Cast
    Group.UPDATES -> GenTVPalette.Muted to Icons.Rounded.SystemUpdate
    Group.STREAMING -> GenTVPalette.Green to Icons.Rounded.PlayArrow
}

/** What turning the group off changes, in two or three words; the full sentence is behind (i). */
private fun Group.warning(): String? = when (this) {
    Group.VOICE -> "Voice button stops"
    Group.CASTING -> "No casting to TV"
    Group.UPDATES -> "No TV updates"
    Group.STREAMING -> "Only unused ones"
    else -> null
}

private fun Group.help(): String? = when (this) {
    Group.VOICE -> "Off means the remote's voice button stops working."
    Group.CASTING -> "Off means you can't cast from your phone to this TV."
    Group.UPDATES -> "Off stops firmware updates. Turn back on before updating."
    Group.STREAMING -> "Only ones you don't use. Your sign-ins come back if you turn them on again."
    else -> null
}

@Composable
fun ReviewScreen(
    s: ReviewState,
    onToggleApp: (String) -> Unit,
    onToggleGroup: (Group, Boolean) -> Unit,
    onStart: () -> Unit,
    onRetryBackup: () -> Unit,
    onDone: () -> Unit,
    onShowMore: () -> Unit = {},
    onAllApps: () -> Unit = {},
    onUninstall: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    tvName: String? = null,
) {
    val n = s.selected.size
    ScreenFrame(top = { com.gentv.mobile.ui.TopBar("返回首页", onBack, tvName) }, bottom = {
        when {
            s.noList -> BigButton("Finish", onDone, color = GenTVPalette.Blue)
            else -> BigButton(
                if (n == 0) "Pick apps to turn off" else "Turn off $n app${if (n == 1) "" else "s"}", onStart,
                color = GenTVPalette.Green, enabled = s.backup == BackupStatus.Saved && n > 0,
            )
        }
    }) {
        Title("应用精简")
        if (s.backup != BackupStatus.Saved) { BackupHeader(s.backup, onRetryBackup); return@ScreenFrame }
        if (s.noList) {
            BackupHeader(s.backup, onRetryBackup)
            Title("No tested list for this TV yet")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nothing will be changed.", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Muted, modifier = Modifier.weight(1f))
                MoreInfo("No tested list yet") {
                    Body("Your backup is saved, but GenTV only turns apps off from a list someone has checked on the same model. Nothing will be changed.", color = GenTVPalette.Ink)
                }
            }
            return@ScreenFrame
        }
        ChipRow {
            BackupHeader(s.backup, onRetryBackup)
            if (!s.exact) Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(Icons.Rounded.Info, "Similar model", color = GenTVPalette.Yellow, textColor = GenTVPalette.Ink)
                MoreInfo("Similar model", color = GenTVPalette.Ink) {
                    Body(
                        "This list is for ${s.listTitle ?: "a similar model"}, not tested on ${s.yourTv?.let { "your $it" } ?: "yours"}. The safe parts are switched on; look through the rest.",
                        color = GenTVPalette.Ink,
                    )
                    Body("Touch and hold any app to see its package ID.", color = GenTVPalette.Muted)
                }
            }
        }
        if (s.fewFound) FewFoundCard(s.candidates.size, onShowMore)
        for (group in Group.entries) {
            val apps = s.candidates.filter { it.group == group }
            if (apps.isNotEmpty()) GroupCard(group, apps, s.selected, onToggleApp, onToggleGroup)
        }
        if (s.extra.isNotEmpty()) MoreAppsCard(s.extra, s.selected, onToggleApp)
        KeptCard(s.kept)
        AllAppsCard(onAllApps)
    }
}

@Composable
private fun BackupHeader(b: BackupStatus, onRetry: () -> Unit) {
    when (b) {
        BackupStatus.Running -> Bubble("Saving a backup…") { Spinner(26.dp) }
        BackupStatus.Saved -> InfoChip(Icons.Rounded.CheckCircle, "Backup saved")
        is BackupStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Note("${b.message} GenTV won't change anything without a backup.", color = GenTVPalette.RedSoft, textColor = GenTVPalette.Red)
            TextLink("Try the backup again", onRetry)
        }
    }
}

/** This TV's list found only a few apps: say so rather than look finished, and offer the rest. */
@Composable
private fun FewFoundCard(count: Int, onShowMore: () -> Unit) {
    CardBox(color = GenTVPalette.BlueSoft) {
        Text(
            if (count == 1) "We found 1 app we're sure about" else "We found $count apps we're sure about",
            style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink,
        )
        Spacer(Modifier.heightIn(min = 10.dp))
        BigButton("查看更多应用", onShowMore, color = GenTVPalette.Grey, textColor = GenTVPalette.Ink)
    }
}

/** Apps other TVs' lists turn off, all unselected; the risks are behind (i). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreAppsCard(apps: List<Candidate>, selected: Set<String>, onToggleApp: (String) -> Unit) {
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AddCircle, GenTVPalette.Blue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("More apps (${apps.size})", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
                Text("From other TVs' lists", style = MaterialTheme.typography.labelMedium, color = GenTVPalette.Muted)
            }
            MoreInfo("More apps") {
                Body("These come from lists for other TVs, not yours. GenTV found them installed on your TV, but nobody has tested turning them off on this model.", color = GenTVPalette.Ink)
                Body("None are picked to start with. Only pick apps you know and don't use. Apps GenTV protects, like the Play Store, keyboard and TV inputs, are never shown.", color = GenTVPalette.Ink)
                Body("Your backup is saved, so Restore everything turns them back on.", color = GenTVPalette.Ink)
            }
        }
        for (group in Group.entries) {
            val inGroup = apps.filter { it.group == group }
            if (inGroup.isEmpty()) continue
            ChipRow(Modifier.padding(top = 12.dp)) {
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = GenTVPalette.Muted)
                group.warning()?.let { WarningChip(it) }
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in inGroup) AppChip(a, a.pkg in selected) { onToggleApp(a.pkg) }
            }
        }
    }
}

@Composable
private fun WarningChip(text: String) =
    InfoChip(Icons.Rounded.WarningAmber, text, color = GenTVPalette.Amber, textColor = GenTVPalette.AmberInk)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(group: Group, apps: List<Candidate>, selected: Set<String>, onToggleApp: (String) -> Unit, onToggleGroup: (Group, Boolean) -> Unit) {
    val on = apps.count { it.pkg in selected }
    val (color, icon) = group.badge()
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
            Text("${group.title} (${apps.size})", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, modifier = Modifier.weight(1f))
            Toggle(on > 0, { onToggleGroup(group, it) }, group.title)
        }
        group.warning()?.let { w ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WarningChip(w)
                MoreInfo(group.title, color = GenTVPalette.AmberInk) { Body(group.help()!!, color = GenTVPalette.Ink) }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in apps) AppChip(a, a.pkg in selected) { onToggleApp(a.pkg) }
        }
    }
}

/** An app to pick: its real icon and its name on two lines. Long-press shows its package ID and what we know about it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppChipLayout(pkg: String, label: String, picked: Boolean, note: String = "", onClick: () -> Unit) {
    var details by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (details) com.gentv.mobile.ui.AppDetailsSheet(label, pkg, note) { details = false }
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Checkbox, onLongClickLabel = "Show package ID", onLongClick = { details = true }, onClick = onClick)
            .semantics { stateDescription = if (picked) "will be turned off" else "stays on" }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.alpha(if (picked) 1f else 0.4f)) { AppIcon(pkg, label, size = 46.dp) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (picked) GenTVPalette.Ink else GenTVPalette.Muted, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppChip(c: Candidate, picked: Boolean, onClick: () -> Unit) = AppChipLayout(c.pkg, c.label, picked, c.note, onClick)

@Composable
private fun AllAppsCard(onClick: () -> Unit) {
    Outline(Modifier.clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Apps, GenTVPalette.Blue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("All apps on this TV", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
                Text("See every app with what it costs your TV, and turn off or uninstall any of them.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = GenTVPalette.Muted)
        }
    }
}

@Composable
private fun KeptCard(kept: List<String>) {
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Lock, Color(0xFF9AA3B8))
            Spacer(Modifier.width(12.dp))
            Text("Always kept", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
        }
        Text(
            (listOf("Play Store", "Google Play Services", "keyboard", "remote and Bluetooth", "TV inputs and Live TV") + kept.take(12)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
