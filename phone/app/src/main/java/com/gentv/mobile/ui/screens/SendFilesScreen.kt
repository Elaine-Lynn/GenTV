package com.gentv.mobile.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gentv.mobile.FilesState
import com.gentv.mobile.PickedFile
import com.gentv.mobile.SendItem
import com.gentv.mobile.SendStatus
import com.gentv.mobile.ui.*
import java.util.Locale

/** Send files from the phone to the TV. */
@Composable
fun SendFilesScreen(
    s: FilesState,
    tvName: String?,
    onPicked: (List<PickedFile>) -> Unit,
    onCancel: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onBack: () -> Unit,
    remote: com.gentv.mobile.RemoteState = com.gentv.mobile.RemoteState(),
    initialApps: Boolean = false,
    onLaunch: (com.gentv.mobile.RemoteApp) -> Unit = {},
    onManage: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onType: (String) -> Unit = {},
    onNavigate: (Int) -> Unit = { onBack() },
    onDismissMessage: () -> Unit = {},
) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                var name = "file"
                var size = 0L
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val z = c.getColumnIndex(OpenableColumns.SIZE)
                            if (n >= 0 && !c.isNull(n)) name = c.getString(n) ?: "file"
                            if (z >= 0 && !c.isNull(z)) size = c.getLong(z)
                        }
                    }
                }
                PickedFile(name, size) { resolver.openInputStream(uri) ?: error("can't open") }
            }
            onPicked(files)
        }
    }

    var appsTab by rememberSaveable(initialApps) { mutableStateOf(initialApps) }
    var hiddenHistory by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var textDialog by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    CompanionScreen("应用投送", tvName, 3, onNavigate, showTitle = false) {
        if (remote.reconnecting) CompanionCard {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在重新连接电视…", color = CompanionColors.Secondary)
        }
        remote.message?.let { message -> CompanionCard {
            Text(message, color = CompanionColors.Error)
            TextButton(onDismissMessage) { Text("知道了") }
        } }
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(CompanionColors.Raised).padding(4.dp)) {
            listOf("电视应用", "无线快传").forEachIndexed { index, label ->
                Box(Modifier.weight(1f).clip(CircleShape).background(if (appsTab == (index == 0)) Color(0xFF39393B) else Color.Transparent)
                    .clickable(role = Role.Tab) { appsTab = index == 0 }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (appsTab == (index == 0)) CompanionColors.Text else CompanionColors.Secondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (appsTab) {
            CompanionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Tv, null, Modifier.size(30.dp), tint = CompanionColors.Blue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tvName ?: "电视应用", color = CompanionColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.freeBytes?.let { "可用空间 ${formatBytes(it)}" } ?: "读取电视上的可启动应用", color = CompanionColors.Secondary, fontSize = 12.sp)
                    }
                    TextButton(onRefresh) { Text("同步", fontSize = 12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("可启动应用  ${remote.apps.size} 款", color = CompanionColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                TextButton(onManage) { Text("应用管理", fontSize = 12.sp) }
            }
            when {
                remote.loadingApps -> CompanionCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在读取电视应用…", color = CompanionColors.Secondary, fontSize = 13.sp) }
                remote.apps.isEmpty() -> CompanionCard { Text("暂未读取到可启动应用", color = CompanionColors.Secondary); TextButton(onRefresh) { Text("重新读取") } }
                else -> remote.apps.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { app ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B1B1D))
                                .clickable(role = Role.Button) { onLaunch(app) }.padding(horizontal = 6.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppIcon(app.pkg, app.label, 48.dp)
                                Text(app.label, localize = false, color = CompanionColors.Text, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            CompanionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(GenIcons.Send, null, Modifier.size(28.dp), tint = CompanionColors.Blue)
                    Column(Modifier.weight(1f)) {
                        Text("隔空发送 APK", color = CompanionColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("仅传输文件，不会自动安装", color = CompanionColors.Secondary, fontSize = 11.sp)
                    }
                    CompanionButton("选择 APK", { picker.launch(arrayOf("application/vnd.android.package-archive")) }, primary = true)
                }
            }
        } else {
            CompanionCard(Modifier.clickable(role = Role.Button) { picker.launch(arrayOf("*/*")) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(68.dp).background(CompanionColors.Blue.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Sensors, null, Modifier.size(36.dp), tint = CompanionColors.Blue)
                    }
                    Text("点选文件，投送至电视", color = CompanionColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("通过当前局域网连接传输照片、视频和安装包", color = CompanionColors.Secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text("保存到 Downloads › GenTV", color = CompanionColors.Blue, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransferKind("发送相册", Icons.Outlined.Image, Modifier.weight(1f)) { picker.launch(arrayOf("image/*")) }
                TransferKind("发送视频", Icons.Outlined.Movie, Modifier.weight(1f)) { picker.launch(arrayOf("video/*")) }
                TransferKind("APK 安装包", Icons.Outlined.Android, Modifier.weight(1f)) { picker.launch(arrayOf("application/vnd.android.package-archive")) }
                TransferKind("文本输入", Icons.Outlined.ContentPaste, Modifier.weight(1f)) { text = clipboard.getText()?.text.orEmpty(); textDialog = true }
            }
            s.freeBytes?.let { Text("电视可用空间 ${formatBytes(it)}", color = CompanionColors.Muted, fontSize = 12.sp) }
        }
        if (!appsTab || s.items.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("传输队列与历史", color = CompanionColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                TextButton({ hiddenHistory = s.items.filter { it.status == SendStatus.SENT || it.status == SendStatus.CANCELLED }.map { it.id } },
                    enabled = s.items.any { it.status == SendStatus.SENT || it.status == SendStatus.CANCELLED }) { Text("清除记录", fontSize = 12.sp) }
            }
            val visible = s.items.filter { it.id !in hiddenHistory }
            if (visible.isEmpty()) CompanionCard { Text("暂无传输记录", color = CompanionColors.Muted, fontSize = 13.sp) }
            visible.forEach { item -> TransferRow(item, onCancel, onRetry) }
        }
        if (textDialog) AlertDialog(onDismissRequest = { textDialog = false }, title = { Text("输入到电视") }, text = {
            OutlinedTextField(text, { text = it }, label = { Text("电视当前输入框中的文字") }, supportingText = { Text("请先在电视上选中输入框。") })
        }, confirmButton = { TextButton({ onType(text); textDialog = false }, enabled = text.isNotBlank()) { Text("发送") } }, dismissButton = { TextButton({ textDialog = false }) { Text("取消") } })
    }
}

@Composable
private fun TransferKind(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1B1D)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = CompanionColors.Blue)
        Text(title, color = CompanionColors.Text, fontSize = 10.sp)
    }
}

@Composable
private fun TransferRow(item: SendItem, onCancel: (Int) -> Unit, onRetry: (Int) -> Unit) {
    CompanionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(30.dp), tint = CompanionColors.Blue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.name, color = CompanionColors.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(item.size), color = CompanionColors.Secondary, fontSize = 11.sp)
            }
            when (item.status) {
                SendStatus.WAITING -> TextButton({ onCancel(item.id) }) { Text("取消") }
                SendStatus.FAILED, SendStatus.NO_SPACE -> TextButton({ onRetry(item.id) }) { Text("重试") }
                SendStatus.SENT -> Icon(Icons.Outlined.CheckCircle, "已发送", tint = CompanionColors.Green)
                else -> Unit
            }
        }
        when (item.status) {
            SendStatus.SENDING -> {
                LinearProgressIndicator(progress = { if (item.size > 0) (item.sent.toFloat() / item.size).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(item.sent)} / ${formatBytes(item.size)}", fontSize = 11.sp, color = CompanionColors.Blue)
            }
            SendStatus.SENT -> Text("已保存：${item.savedAs ?: item.name}", color = CompanionColors.Green, fontSize = 11.sp)
            SendStatus.WAITING -> Text("等待发送", color = CompanionColors.Muted, fontSize = 11.sp)
            SendStatus.CANCELLED -> Text("已取消", color = CompanionColors.Muted, fontSize = 11.sp)
            SendStatus.NO_SPACE -> Text("电视空间不足，请清理后重试", color = CompanionColors.Error, fontSize = 12.sp)
            SendStatus.FAILED -> Text("发送失败，请确认电视连接后重试", color = CompanionColors.Error, fontSize = 12.sp)
        }
    }
}

private fun formatBytes(b: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        b >= gb -> String.format(Locale.US, "%.1f GB", b / gb)
        b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
        else -> String.format(Locale.US, "%d KB", maxOf(1L, Math.round(b / kb)))
    }
}
