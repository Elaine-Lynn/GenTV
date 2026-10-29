package com.gentv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gentv.mobile.CheckState
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.CardBox
import com.gentv.mobile.ui.MoreInfo
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Bubble
import com.gentv.mobile.ui.HeroMascot
import com.gentv.mobile.ui.Mood
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.SafetyBanner
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.TextLink
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.TopBar
import com.gentv.core.lists.AppLabels

/** Check my TV: compares the TV with what GenTV turned off, and offers to turn things off again. */
@Composable
fun CheckScreen(
    s: CheckState,
    tvName: String?,
    labels: Map<String, String>,
    onRetidy: () -> Unit,
    onRebootTest: () -> Unit,
    onRecheck: () -> Unit,
    onBack: () -> Unit,
    onTurnOnRemoteHelper: (String) -> Unit = {},
) {
    val report = s.report
    val busy = s.rebooting != null || s.running
    ScreenFrame(
        top = { TopBar(back = "返回首页", onBack = onBack, tvName = tvName) },
        bottom = {
            if (s.rebooting == null) {
                if (report != null && report.toTidy.isNotEmpty()) {
                    BigButton(
                        if (s.retidying) "Turning off…" else "Turn these off again",
                        onRetidy, color = GenTVPalette.Green, enabled = !s.retidying,
                    )
                }
                if (!busy) {
                    BigButton("Restart the TV and check", onRebootTest, color = GenTVPalette.Grey, textColor = GenTVPalette.Ink,
                        leading = { Icon(Icons.Rounded.RestartAlt, null, tint = GenTVPalette.Ink) })
                    Text("Checks it all stays off. About 2 minutes.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
                }
                if (report?.toTidy.isNullOrEmpty() && !busy) TextLink("Check again", onRecheck)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("电视诊断", Modifier.weight(1f))
            MoreInfo("电视诊断") {
                Body("TV updates sometimes switch apps back on. This checks your TV against what GenTV turned off.", color = GenTVPalette.Ink)
                Body("Restarting checks that everything stays off after a reboot. It takes about 2 minutes.", color = GenTVPalette.Ink)
            }
        }
        if (s.rebooting != null) {
            HeroMascot(mood = Mood.SEARCH, height = 120.dp)
            Bubble(s.rebooting, leading = { Spinner() })
            return@ScreenFrame
        }
        if (s.running) Bubble("正在检查电视…", leading = { Spinner() })
        if (report != null) {
            if (report.allGood) {
                ResultCard(Mood.HAPPY, "All tidy", "${report.stillOff} still off", GenTVPalette.GreenSoft, GenTVPalette.GreenInk)
            } else {
                val back = report.backOn.size + report.newOnList.size
                ResultCard(
                    Mood.WORRIED,
                    if (back == 0) "Home screen changed" else if (back == 1) "1 app to turn off" else "$back apps to turn off",
                    "${report.stillOff} still off", GenTVPalette.Amber, GenTVPalette.AmberInk,
                )
                if (report.backOn.isNotEmpty()) PackageCard("Switched back on", "Usually after a TV update.", report.backOn, labels)
                if (report.newOnList.isNotEmpty()) PackageCard("New since your last tidy", null, report.newOnList, labels)
                if (report.homeChanged) {
                    Note("Your TV's old home screen took over again. Open Home screen on My TV to switch back.")
                }
            }
        }
        if (report != null && report.remoteOff.isNotEmpty()) Outline {
            Text("Remote features are off", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
            Text("Turned off, so the remote's mic or shortcut buttons may not work. Turn them on to get them back.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pkg in report.remoteOff) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(labels[pkg] ?: com.gentv.core.lists.AppLabels.label(pkg), style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Ink)
                        com.gentv.core.lists.AllApps.TRADE_OFFS[pkg]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted) }
                    }
                    if (busy) Spinner() else TextLink("Turn on", { onTurnOnRemoteHelper(pkg) })
                }
            }
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message) }
    }
}

/** The answer first: a mascot, a big line and a small one. */
@Composable
private fun ResultCard(mood: Mood, title: String, sub: String, color: Color, ink: Color) {
    CardBox(color = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.gentv.mobile.ui.IconBadge(com.gentv.mobile.ui.GenIcons.Check, ink, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = ink)
                Text(sub, style = MaterialTheme.typography.titleMedium, color = ink)
            }
        }
    }
}

@Composable
private fun PackageCard(title: String, note: String?, pkgs: List<String>, labels: Map<String, String>) {
    Outline {
        Text(title, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
        if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
        Spacer(Modifier.heightIn(min = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (pkg in pkgs) {
                val label = labels[pkg] ?: AppLabels.label(pkg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, label, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
