package com.gentv.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gentv.mobile.BuildConfig
import com.gentv.mobile.data.UpdateCheck
import com.gentv.mobile.data.UpdateStatus
import com.gentv.mobile.ui.*
import com.gentv.mobile.ui.AppText as Text

@Composable
fun SettingsScreen(update: UpdateStatus, onBack: () -> Unit, onCheck: () -> Unit, onOpenUrl: (String) -> Unit, onFeedback: () -> Unit = {}, analyticsEnabled: Boolean = false, onAnalyticsChanged: (Boolean) -> Unit = {}, analyticsAvailable: Boolean = com.gentv.mobile.data.Analytics.configured, onDownloadUpdate: (com.gentv.mobile.data.UpdateInfo) -> Unit = {}) {
    BackHandler(onBack = onBack)
    var privacy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("Privacy and data") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) { Text("Usage statistics are off by default. If enabled, only fixed feature events, app version, Android SDK and a random session ID are sent. Feedback is sent only when you submit it and can include your message, email, phone model, Android version, random install ID and attachments you choose. TV connection keys, backups and operation history stay on this phone unless you choose to share a report. Shared reports are redacted before export. Turning statistics off clears unsent events; already sent data cannot be recalled by this app. For information about server retention and remote-data deletion requests, contact GenTV through the official website. Keep your TV restore records before clearing app data.") } },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Close") } })
    val language = LocalAppLanguage.current
    val changeLanguage = LocalChangeLanguage.current
    ScreenFrame(top = { TopBar("Back", onBack, null, showSettings = false) }) {
        Title("Settings")
        CardBox {
            Text("Language", style = MaterialTheme.typography.titleLarge, color = GenTVPalette.Ink)
            Spacer(Modifier.height(12.dp))
            listOf(AppLanguage.ENGLISH to "English", AppLanguage.CHINESE to "简体中文").forEach { (choice, label) ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { changeLanguage(choice) }
                    .heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = language == choice, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = GenTVPalette.Ink, localize = false)
                }
            }
            Text("Changes take effect immediately and are saved on this phone.", color = GenTVPalette.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        CardBox {
            Text("Updates", style = MaterialTheme.typography.titleLarge, color = GenTVPalette.Ink)
            Spacer(Modifier.height(12.dp))
            Text("Current version", color = GenTVPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            Text(BuildConfig.VERSION_NAME, color = GenTVPalette.Ink, style = MaterialTheme.typography.titleLarge, localize = false)
            Spacer(Modifier.height(16.dp))
            BigButton(if (update is UpdateStatus.Checking) "Checking for updates…" else "Check for updates", onCheck,
                enabled = update !is UpdateStatus.Checking)
            when (update) {
                UpdateStatus.Current -> SafetyBanner("You're up to date")
                UpdateStatus.Failed -> Note("Could not check for updates. Try again later.")
                UpdateStatus.Unconfigured -> Note("Automatic update service is not configured yet. Visit the official website for downloads.")
                is UpdateStatus.Available -> {
                    SafetyBanner("A newer version is available: ${update.info.version}")
                    if (update.info.minSdk > android.os.Build.VERSION.SDK_INT) Note("This update requires a newer Android version.")
                    else TextLink("Download update", { if (update.info.installable) onDownloadUpdate(update.info) else onOpenUrl(UpdateCheck.SITE_URL) })
                }
                else -> Unit
            }
            TextLink("Official website", { onOpenUrl(UpdateCheck.SITE_URL) })
        }
        if (analyticsAvailable) CardBox {
            var enabled by androidx.compose.runtime.remember(analyticsEnabled) { androidx.compose.runtime.mutableStateOf(analyticsEnabled) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Anonymous usage statistics", modifier = Modifier.weight(1f), color = GenTVPalette.Ink)
                Switch(checked = enabled, onCheckedChange = { enabled = it; onAnalyticsChanged(it) })
            }
            Text("Optional. Sends feature usage and success or failure counts. No device identifiers, TV addresses, app lists, files or logs. Turning off clears unsent events.", color = GenTVPalette.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        CardBox {
            TextLink("Privacy and data", { privacy = true })
        }
        CardBox {
            TextLink("Feedback", onFeedback)
            Text("Share a problem or suggestion.", color = GenTVPalette.Muted, style = MaterialTheme.typography.bodyMedium)
        }

    }
}
