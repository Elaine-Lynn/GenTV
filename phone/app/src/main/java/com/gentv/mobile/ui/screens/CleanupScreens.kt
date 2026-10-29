package com.gentv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import com.gentv.mobile.HomeAction
import com.gentv.mobile.HomeState
import com.gentv.core.lists.HomeApp
import com.gentv.mobile.TidyState
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.CardBox
import com.gentv.mobile.ui.ChoiceCard
import com.gentv.mobile.ui.InfoChip
import com.gentv.mobile.ui.MoreInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Bubble
import com.gentv.mobile.ui.Check
import com.gentv.mobile.ui.HeroMascot
import com.gentv.mobile.ui.Mood
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.ProgressBar
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.TextLink
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.TopBar
import com.gentv.mobile.ui.Title
import com.gentv.core.ops.PkgResult

@Composable
fun TidyingScreen(s: TidyState, onRestore: () -> Unit, onContinue: () -> Unit, onShareLogs: () -> Unit) {
    val stopped = s.stopReason != null
    ScreenFrame(top = if (stopped) ({ com.gentv.mobile.ui.TopBar("返回结果", onContinue, null) }) else null, bottom = if (!stopped) null else { {
        BigButton("恢复所有更改", onRestore, color = GenTVPalette.Green, leading = { Icon(Icons.Rounded.Restore, null, tint = Color(0xFF14213A)) })
        TextLink("Keep what was done and continue", onContinue)
        androidx.compose.foundation.layout.Row { TextLink("Share logs", onShareLogs); com.gentv.mobile.ui.ReportProblemLink("tidy") }
    } }) {
        if (stopped) {
            HeroMascot(worried = true)
            Title("GenTV stopped to be safe")
            Note(s.stopReason!!)
            Body("Your TV is fine. ${s.disabledCount} apps were turned off before it stopped; Restore everything turns them back on.")
        } else {
            Bubble(if (s.saving) "Saving, keep the TV on" else "Tidying up…") { Spinner(26.dp) }
            Body("操作进行中，请保持电视开机。完成或安全停止后可返回。")
            ProgressBar(if (s.order.isEmpty()) 0f else s.results.size / s.order.size.toFloat(), GenTVPalette.Green)
            if (s.reconnecting) Note("Wi-Fi dropped. Reconnecting and carrying on where it left off…", color = GenTVPalette.BlueSoft, textColor = GenTVPalette.Ink)
        }
        Outline {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in s.order) Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(c.pkg, c.label, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.label, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, modifier = Modifier.weight(1f))
                    Status(s.results[c.pkg], running = !stopped && !s.finished)
                }
            }
        }
    }
}

@Composable
private fun Status(r: PkgResult?, running: Boolean) {
    when (r) {
        is PkgResult.Disabled -> Check(GenTVPalette.Green, 26.dp)
        is PkgResult.Removed -> Small(if (r.yours) "uninstalled" else "uninstalled for this TV", GenTVPalette.GreenInk)
        is PkgResult.AlreadyOff -> Small("already off")
        is PkgResult.NotInstalled -> Small("not on this TV")
        is PkgResult.Skipped -> Small("kept on")
        is PkgResult.Failed -> Small("didn't turn off", GenTVPalette.Red)
        null -> if (running) Spinner(20.dp, GenTVPalette.Line) else Small("not touched")
    }
}

@Composable
private fun Small(t: String, color: androidx.compose.ui.graphics.Color = GenTVPalette.Muted) =
    Text(t, style = MaterialTheme.typography.labelMedium, color = color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreenStep(
    s: HomeState,
    disabledCount: Int,
    onToggle: (String) -> Unit,
    onChoose: (HomeApp) -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onAction: (HomeAction, Boolean) -> Unit,
    onContinue: () -> Unit,
    onShareLogs: () -> Unit,
    onCancelPlayStore: () -> Unit = {},
    onOpenLook: () -> Unit = {},
    onCloseLook: () -> Unit = {},
    onApplyLook: (com.gentv.mobile.LookId) -> Unit = {},
    onApplyPhoto: (com.gentv.mobile.PickedFile) -> Unit = {},
    onBack: () -> Unit = {},
) {
    when (s) {
        is HomeState.Offer -> ScreenFrame(top = { TopBar("返回", onBack, null) }, bottom = {
            BigButton("Install GenTV desktop", onInstall)
        }) {
            Title("GenTV desktop")
            Body("Your apps, a clean home screen, and no ads.")
            CardBox(color = GenTVPalette.BlueSoft, border = GenTVPalette.Blue) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.gentv.mobile.R.drawable.gentv_desktop_preview),
                    contentDescription = "GenTV desktop preview",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2066f / 1104f).clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text("GenTV", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge, color = GenTVPalette.Ink)
                Body("Install GenTV on your connected TV and open it immediately.")
            }
            Note("Requires Android 8 or newer. Your existing home screen stays available.")
        }
        is HomeState.GenTVResult -> ScreenFrame(top = { TopBar("返回", onBack, null) }, bottom = {
            if (s.installed) BigButton("Done", onContinue)
            else BigButton("Try again", onInstall)
        }) {
            Title(if (s.installed) "GenTV desktop installed" else "Could not install GenTV desktop")
            Body(if (s.installed) "GenTV is open on your TV. You can set it as the default home screen in your TV settings." else s.message ?: "Reconnect to your TV and try again.")
        }
        is HomeState.WaitingForPlayStore -> ScreenFrame(top = { com.gentv.mobile.ui.TopBar("返回", onBack, null) }, bottom = {
            TextLink("Choose another home screen", onCancelPlayStore)
        }) {
            HeroMascot(mood = Mood.SEARCH, height = 130.dp)
            Title("Press Install on your TV")
            Body("Use the remote to press Install. GenTV carries on by itself.")
            Bubble("Waiting for the install…") { Spinner(26.dp) }
        }
        HomeState.Running -> ScreenFrame {
            Body("正在设置电视，请等待操作完成后返回。")
            HeroMascot()
            Bubble("Setting up your new home screen…") { Spinner(26.dp) }
            Body("About 30 seconds. Please don't press the remote.")
        }
        is HomeState.Look -> LookScreen(s.look, onCloseLook, onApplyLook, onApplyPhoto)
        is HomeState.Installed -> ScreenFrame(top = { com.gentv.mobile.ui.TopBar("返回", onBack, null) }, bottom = {
            if (s.app == HomeApp.GENTV) BigButton("Pick a look", onOpenLook, color = GenTVPalette.BlueSoft, textColor = GenTVPalette.Ink)
            BigButton("Continue", onContinue)
        }) {
            HeroMascot(mood = Mood.HAPPY)
            Bubble("New home screen is on", color = GenTVPalette.GreenSoft, textColor = GenTVPalette.GreenInk) { Check(GenTVPalette.Green, 30.dp) }
            HomePreview(s.app)
            Body(s.app.tip)
        }
        is HomeState.Problem -> {
            var ethernet by rememberSaveable { mutableStateOf(false) }
            ScreenFrame(top = { com.gentv.mobile.ui.TopBar("返回", onBack, null) }, bottom = {
                when (s.action) {
                    HomeAction.REPLACE_COPY -> BigButton("Replace it", { onAction(HomeAction.REPLACE_COPY, false) })
                    HomeAction.TURN_OFF_SETUP_APP -> BigButton("Try again", { onAction(HomeAction.TURN_OFF_SETUP_APP, ethernet) })
                    HomeAction.RETRY -> BigButton("Try again", { onAction(HomeAction.RETRY, false) })
                    null -> Unit
                }
                TextLink(if (s.action == null) "Continue" else "Skip, keep my home screen", onContinue)
                androidx.compose.foundation.layout.Row { TextLink("Share logs", onShareLogs); if (s.worried) com.gentv.mobile.ui.ReportProblemLink("tidy") }
            }) {
                HeroMascot(worried = s.worried)
                Title(if (s.worried) "Something needs a look" else "Home screen not changed")
                Body(s.message)
                if (s.action == HomeAction.TURN_OFF_SETUP_APP) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ethernet, { ethernet = it })
                    Text("My TV is plugged in with a network cable", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Ink)
                }
                if (s.rescue.isNotEmpty()) Outline {
                    Text("If the TV looks wrong, these commands put it back (from a computer):", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
                    for (c in s.rescue) Text(c, style = MaterialTheme.typography.labelMedium, color = GenTVPalette.Ink)
                }
            }
        }
    }
}

/** Current desktop identity for installation results. */
val HomeApp.title: String get() = if (this == HomeApp.GENTV) "GenTV" else "Selected home screen"
val HomeApp.tip: String get() = "Your apps and desktop settings remain available."

@Composable
private fun HomePreview(app: HomeApp) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GenTVPalette.Grey).padding(20.dp)) {
        Icon(Icons.Rounded.Home, contentDescription = null, tint = GenTVPalette.Muted)
        Text(app.title, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
        Body("Your selected home screen is ready.")
    }
}

/** The TV's home app hasn't been tested with GenTV, so no home screen change is offered. */
@Composable
fun UntestedHomeCard() {
    CardBox(color = GenTVPalette.Grey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Home, null, tint = GenTVPalette.Muted)
            Spacer(Modifier.width(10.dp))
            Text("Your TV's home screen isn't supported yet", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
        }
    }
}
