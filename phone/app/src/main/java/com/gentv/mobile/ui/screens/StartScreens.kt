package com.gentv.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Bubble
import com.gentv.mobile.ui.ChoiceButton
import com.gentv.mobile.ui.ChipRow
import com.gentv.mobile.ui.InfoChip
import com.gentv.mobile.ui.MoreInfo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tv
import com.gentv.mobile.ui.HeroMascot
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.NumberedStep
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.TextLink
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.core.flow.Mode
import com.gentv.core.flow.TvKind

@Composable
fun WelcomeScreen(
    onChoose: (TvKind) -> Unit,
    onRestore: () -> Unit,
    update: com.gentv.mobile.data.UpdateInfo? = null,
    onDismissUpdate: () -> Unit = {},
    savedTvs: List<com.gentv.mobile.data.KnownTv> = emptyList(),
    onSavedTv: (com.gentv.mobile.data.KnownTv) -> Unit = {},
    onRescue: (com.gentv.mobile.data.KnownTv) -> Unit = {},
    online: Set<String> = emptySet(),
    onConnect: () -> Unit = { onChoose(TvKind.ANDROID_TV) },
) {
    var otherKinds by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    @Composable fun ConnectBlock() {
    BigButton(if (savedTvs.isNotEmpty()) "连接其他电视" else "连接电视", onConnect, color = GenTVPalette.Blue,
        leading = { Icon(Icons.Rounded.Tv, null, tint = Color(0xFF14213A)) })
    TextLink("Fire TV, Samsung, LG or not sure?", { otherKinds = true }, color = GenTVPalette.Muted, small = true)
    if (otherKinds) com.gentv.mobile.ui.InfoSheet("Which kind of TV?", { otherKinds = false }) {
        ChoiceButton("Android TV / Google TV", Icons.Rounded.Tv, GenTVPalette.Blue) { otherKinds = false; onChoose(TvKind.ANDROID_TV) }
        ChoiceButton("Amazon Fire TV", Icons.Rounded.SettingsInputHdmi, GenTVPalette.Orange, badge = "Clean-up only") { otherKinds = false; onChoose(TvKind.FIRE_TV) }
        ChoiceButton("Samsung or LG", Icons.Rounded.DesktopWindows, GenTVPalette.Purple) { otherKinds = false; onChoose(TvKind.SAMSUNG_LG) }
        ChoiceButton("Not sure", Icons.AutoMirrored.Rounded.HelpOutline, GenTVPalette.Grey, textColor = GenTVPalette.Ink) { otherKinds = false; onChoose(TvKind.NOT_SURE) }
    }
    }
    ScreenFrame(top = {
        val actions = com.gentv.mobile.ui.LocalAppActions.current
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.weight(1f))
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                TextLink("恢复电视", onRestore, icon = Icons.Rounded.Restore)
                androidx.compose.material3.IconButton(actions.settings) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Settings, "Settings", tint = GenTVPalette.Muted)
                }
            }
        }
    }) {
        if (update != null) UpdateBanner(update, onDismissUpdate)
        if (savedTvs.isNotEmpty()) {
            com.gentv.mobile.ui.AppText("我的设备", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = GenTVPalette.Ink)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                savedTvs.take(5).forEach { tv ->
                    com.gentv.mobile.ui.TvCard(tv.title, savedSubtitle(tv), onClick = { onSavedTv(tv) }, trailing = {
                        val up = tv.host in online
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(10.dp).background(if (up) GenTVPalette.Green else GenTVPalette.Muted.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                    .semantics { contentDescription = if (up) "On the network" else "Not found on the network" },
                            )
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Icon(Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = GenTVPalette.Muted)
                        }
                    })
                }
            }
            ConnectBlock()
            var pick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            TextLink("TV stuck after a change?", { if (savedTvs.size == 1) onRescue(savedTvs[0]) else pick = true }, color = GenTVPalette.Muted)
            if (pick) androidx.compose.material3.AlertDialog(
                onDismissRequest = { pick = false },
                title = { com.gentv.mobile.ui.AppText("Which TV is stuck?") },
                text = {
                    Column {
                        savedTvs.forEach { tv ->
                            androidx.compose.material3.TextButton({ pick = false; onRescue(tv) }) { com.gentv.mobile.ui.AppText(tv.title) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton({ pick = false }) { com.gentv.mobile.ui.AppText("取消") } },
            )

        } else {
            com.gentv.mobile.ui.AppText("GenTV", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = GenTVPalette.Ink)
            HeroMascot(height = 210.dp)
            Title("让电视，尽在掌握。")
            Body("更清爽的电视桌面，更顺手的手机遥控，文件轻松传到大屏。")
        }
        if (savedTvs.isEmpty()) ConnectBlock()
        ChipRow(center = true) {
            InfoChip(Icons.Rounded.Favorite, "免费使用", color = Color.Transparent, textColor = GenTVPalette.Muted)
            InfoChip(Icons.Rounded.Shield, "安全可恢复", color = Color.Transparent, textColor = GenTVPalette.Muted)
            InfoChip(Icons.Rounded.Backup, "操作前备份", color = Color.Transparent, textColor = GenTVPalette.Muted)
        }
    }
}

/** A newer release is out. GenTV is sideloaded, so this is the only way people hear about it. */
@Composable
private fun UpdateBanner(update: com.gentv.mobile.data.UpdateInfo, onDismiss: () -> Unit) {
    val actions = com.gentv.mobile.ui.LocalAppActions.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth()
            .background(GenTVPalette.Yellow, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.gentv.mobile.ui.AppText(
            "GenTV ${update.version} is out", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink,
            modifier = Modifier.weight(1f),
        )
        TextLink("Get it", { if (update.installable) actions.downloadUpdate(update) else actions.openUrl(com.gentv.mobile.data.UpdateCheck.SITE_URL) })
        TextLink("Not now", onDismiss, color = GenTVPalette.Muted)
    }
}

@Composable
fun UnsupportedScreen(onBack: () -> Unit) {
    ScreenFrame(bottom = { BigButton("返回", onBack, color = GenTVPalette.Blue) }) {
        HeroMascot(worried = true)
        Title("Samsung and LG aren't supported")
        Body("Samsung TVs run Tizen and LG TVs run webOS. Neither lets a phone switch apps off the way Android TV does, so GenTV can't help with them.")
        Note("If your Samsung or LG has a Chromecast, Fire TV stick or Google TV streamer plugged in, pick that one instead.", color = GenTVPalette.BlueSoft, textColor = GenTVPalette.Ink)
    }
}

@Composable
fun GetReadyScreen(kind: TvKind?, mode: Mode, onBack: () -> Unit = {}, onReady: () -> Unit) {
    val fire = kind == TvKind.FIRE_TV
    ScreenFrame(top = { com.gentv.mobile.ui.TopBar("返回", onBack, null) }, bottom = { BigButton("准备好了，开始连接", onReady) }) {
        Title(if (mode == Mode.RESTORE) "Let's reconnect to your TV" else "准备连接电视")
        Body(if (mode == Mode.RESTORE) "TV on, same Wi-Fi, debugging still on." else "About a minute, and only once.")
        Outline {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (fire) {
                    NumberedStep(1, "Select your Fire TV's name 7 times", "Settings › My Fire TV › About")
                    NumberedStep(2, "Turn on ADB debugging", "My Fire TV › Developer options")
                    NumberedStep(3, "Allow on the TV", "Tick Always allow, then Allow")
                } else {
                    NumberedStep(1, "Select Build 7 times", "Settings › System › About › Android TV OS build")
                    NumberedStep(2, "Turn on USB debugging", "Developer options. Then Wireless debugging too.")
                    NumberedStep(3, "Allow on the TV", "Tick Always allow, then Allow")
                }
            }
        }
        MoreInfo("Need help?", link = "Need help?") {
            Body("Keep your phone on the same home network as the TV.", color = GenTVPalette.Ink)
            Note("Using a stick powered from the TV's USB port? Plug it into its wall adapter for now, so it doesn't switch off halfway.")
            if (!fire) {
                Body("Some TVs call Wireless debugging Network debugging or ADB over network instead. With Wireless debugging, GenTV asks once for the code your TV shows.", color = GenTVPalette.Ink)
                Note(
                    "TV on Ethernet? On most TVs Wireless debugging stays greyed out until the TV is on Wi-Fi. Turn on the TV's Wi-Fi and join the same network as your phone, then turn on Wireless debugging. You can turn Wi-Fi off again when you're done.",
                    color = GenTVPalette.BlueSoft, textColor = GenTVPalette.Ink,
                )
            }
        }
    }
}

/** "TCL 55C735 · 192.168.1.2 · used 2 days ago": enough to tell two TVs of one model apart. */
internal fun savedSubtitle(tv: com.gentv.mobile.data.KnownTv, now: Long = System.currentTimeMillis()): String {
    val mins = ((now - tv.lastSeen) / 60_000).coerceAtLeast(0)
    val ago = when {
        tv.lastSeen <= 1_000_000L -> null
        mins < 2 -> "used just now"
        mins < 60 -> "used $mins min ago"
        mins < 60 * 24 -> "used ${mins / 60} h ago"
        mins < 60 * 24 * 60 -> "used ${mins / (60 * 24)} days ago"
        else -> "used a while ago"
    }
    return listOfNotNull(tv.model.takeIf { it.isNotBlank() && it != tv.title }, tv.host.takeIf { it.isNotBlank() }, ago).joinToString("  ·  ")
}
