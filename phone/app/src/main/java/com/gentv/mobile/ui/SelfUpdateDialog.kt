package com.gentv.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.gentv.mobile.data.InstallStatus
import com.gentv.mobile.data.SelfUpdate
import com.gentv.mobile.ui.AppText as Text

@Composable
fun SelfUpdateDialog(updater: SelfUpdate, install: () -> Unit) {
    val status by updater.status.collectAsStateWithLifecycle()
    if (status == InstallStatus.Idle) return
    AlertDialog(
        onDismissRequest = updater::cancel,
        title = { Text("App update") },
        text = { Text(when (val s = status) {
            is InstallStatus.Downloading -> "Downloading update: ${s.percent}%"
            is InstallStatus.Ready -> "Update verified. Android will ask you to confirm installation."
            InstallStatus.Verifying -> "Verifying update…"
            InstallStatus.PermissionRequired -> "Allow installs from this app in Android settings, then return and tap Install update."
            InstallStatus.InstallerOpened -> "Complete installation in Android. If you cancelled, you can try installing again."
            else -> "Update download or verification failed. Check your connection and try again."
        }) },
        confirmButton = {
            when (status) {
                is InstallStatus.Ready, InstallStatus.PermissionRequired, InstallStatus.InstallerOpened -> TextButton(onClick = install) { Text("Install update") }
                InstallStatus.Failed -> TextButton(onClick = updater::retry) { Text("Retry download") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = updater::cancel) { Text("Cancel") } },
    )
}
