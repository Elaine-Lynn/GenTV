package com.gentv.mobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gentv.mobile.TvSummary
import com.gentv.mobile.ui.*
import com.gentv.core.flow.*

@Composable
fun DiscoveryScreen(state: ConnectState, currentTv: TvSummary?, onEvent: (ConnectEvent) -> Unit, onNavigate: (Int) -> Unit, onBack: () -> Unit = {}) {
    val scan = state as? ConnectState.Scanning
    val devices = scan?.found.orEmpty()
    val searching = scan != null && !scan.done
    var manual by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var ip by rememberSaveable { mutableStateOf("") }
    val valid = ConnectMachine.isIp(ip)
    val submit = { if (valid) { manual = false; onEvent(ConnectEvent.TypedIp(ip)) } }
    CompanionScreen("发现设备", currentTv?.title, 2, onNavigate, showTitle = false) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiscoveryRadar(searching)
            Row(Modifier.background(CompanionColors.Raised, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp), tint = CompanionColors.Blue)
                Text("同一局域网", color = CompanionColors.Text, fontSize = 13.sp)
                Box(Modifier.size(5.dp).background(CompanionColors.Green, CircleShape))
            }
            Text(if (searching) "正在发现电视与投影仪…" else "本轮搜索已完成", color = CompanionColors.Muted, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("可连接设备 (${devices.count { it.host != currentTv?.host } + if (currentTv != null) 1 else 0})", color = CompanionColors.Secondary, fontSize = 14.sp)
            TextButton(onClick = { onEvent(ConnectEvent.StartScan) }) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("重新检测", fontSize = 12.sp)
            }
        }
        currentTv?.let { current -> DeviceDiscoveryCard(FoundTv(current.host, name = current.title, model = current.model), true) { onNavigate(0) } }
        devices.filter { it.host != currentTv?.host }.forEach { tv -> DeviceDiscoveryCard(tv, false) { onEvent(ConnectEvent.Pick(tv)) } }
        if (devices.isEmpty() && currentTv == null) CompanionCard {
            Icon(Icons.Outlined.Tv, null, Modifier.size(32.dp), tint = CompanionColors.Muted)
            Text(if (searching) "正在搜索附近设备" else "暂未找到可连接设备", color = CompanionColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("请确认电视已开机、开启网络调试，并与手机连接同一家庭网络。", color = CompanionColors.Secondary, fontSize = 13.sp)
        }
        CompanionButton("手动通过 IP 地址直连", { manual = true }, Modifier.fillMaxWidth().padding(top = 12.dp))
        TextButton(onClick = { help = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.HelpOutline, null, Modifier.size(16.dp), tint = CompanionColors.Secondary)
            Spacer(Modifier.width(6.dp)); Text("未找到设备？查看排查指引", color = CompanionColors.Secondary, fontSize = 13.sp)
        }
        if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("指定 IP 地址连接") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请输入电视网络设置中显示的内网 IPv4 地址：")
                OutlinedTextField(ip, { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) }, label = { Text("电视 IP 地址") }, singleLine = true,
                    isError = ip.count { it == '.' } == 3 && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }))
            }
        }, confirmButton = { TextButton(submit, enabled = valid) { Text("立即连接") } }, dismissButton = { TextButton({ manual = false }) { Text("取消") } })
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("设备发现排查") }, text = { Text("1. 手机和电视连接同一路由器，避免访客网络。\n\n2. 在电视开发者选项中开启 USB／网络调试；新系统可使用无线调试配对。\n\n3. 电视开启调试后点击重新检测，搜索最长约 20 秒。\n\n4. 路由器隔离或广播受限时，可手动输入电视 IP，并在电视确认授权。") }, confirmButton = { TextButton({ help = false }) { Text("知道了") } })
    }
}

@Composable
private fun DeviceDiscoveryCard(tv: FoundTv, connected: Boolean, onClick: () -> Unit) {
    CompanionCard(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).background(CompanionColors.Raised, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tv, null, Modifier.size(27.dp), tint = if (connected) CompanionColors.Green else CompanionColors.Blue)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tv.title, localize = false, color = CompanionColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (tv.host == "demo") "演示设备 · 无真实电视" else if (connected) "当前电视" else if (tv.tlsPort != null) "无线调试" else if (tv.legacyOpen) "网络调试已开启" else "请确认已开启调试", color = CompanionColors.Muted, fontSize = 11.sp)
            }
            TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = if (connected) CompanionColors.Green else CompanionColors.Blue)) {
                Text(if (connected) "已连接" else "连接", fontSize = 13.sp)
                if (!connected) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun DiscoveryRadar(searching: Boolean) {
    val still = LocalInspectionMode.current
    val angle = if (searching && !still) rememberInfiniteTransition(label = "radar").animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "beam").value else 38f
    Canvas(Modifier.size(192.dp)) {
        val c = center; val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(CompanionColors.Blue.copy(alpha = .12f), Color.Transparent)), r)
        drawCircle(CompanionColors.Blue.copy(alpha = .045f), r)
        drawCircle(CompanionColors.Blue.copy(alpha = .07f), r * .80f)
        drawCircle(Color(0xFF303139), r * .64f)
        drawCircle(Color(0xFF292C34), r * .42f)
        drawCircle(CompanionColors.Blue.copy(alpha = .12f), r * .25f)
        drawCircle(CompanionColors.Blue.copy(alpha = .45f), r * .16f, style = Stroke(1.5.dp.toPx()))
        val rad = Math.toRadians(angle.toDouble())
        drawLine(CompanionColors.Blue.copy(alpha = .65f), c, Offset(c.x + kotlin.math.cos(rad).toFloat() * r * .43f, c.y + kotlin.math.sin(rad).toFloat() * r * .43f), 1.5.dp.toPx())
        drawCircle(CompanionColors.Green, 3.dp.toPx(), Offset(r * 1.58f, r * .28f))
        drawCircle(CompanionColors.Blue, 2.5.dp.toPx(), Offset(r * .30f, r * 1.65f))
        drawCircle(CompanionColors.Secondary.copy(alpha = .5f), 2.dp.toPx(), Offset(r * .38f, r * .45f))
    }
}

@Composable
fun ConnectionFlowScreen(state: ConnectState, onEvent: (ConnectEvent) -> Unit, onShareLogs: () -> Unit, onNavigate: (Int) -> Unit, onBack: () -> Unit = {}) {
    val tv = when (state) {
        is ConnectState.Connecting -> state.tv
        is ConnectState.PressAllow -> state.tv
        is ConnectState.Pairing -> state.tv
        is ConnectState.Connected -> state.tv
        is ConnectState.TurnOnDebugging -> state.tv
        is ConnectState.Unreachable -> state.tv
        is ConnectState.Denied -> state.tv
        is ConnectState.Failed -> state.tv
        else -> null
    }
    var code by rememberSaveable(tv?.host) { mutableStateOf("") }
    var port by rememberSaveable(tv?.host) { mutableStateOf("") }
    val waiting = state is ConnectState.Connecting || state is ConnectState.PressAllow || (state is ConnectState.Pairing && state.busy)
    CompanionScreen("连接设备", null, 2, onNavigate, onBack = onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Tv, null, Modifier.size(56.dp), tint = CompanionColors.Blue)
            Text(tv?.title ?: "连接电视", localize = tv == null, color = CompanionColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        CompanionCard {
            val title = when (state) {
                is ConnectState.Connecting -> "正在建立连接"
                is ConnectState.PressAllow -> "请在电视上确认授权"
                is ConnectState.Pairing -> "无线调试配对"
                is ConnectState.Connected -> "已连接电视"
                is ConnectState.TurnOnDebugging -> "请开启网络调试"
                is ConnectState.Unreachable -> "暂时无法连接电视"
                is ConnectState.Denied -> "电视拒绝了连接"
                else -> "连接未完成"
            }
            Text(title, color = CompanionColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            val description = when (state) {
                is ConnectState.Connecting -> "请保持手机和电视在同一网络，并留意电视上的调试授权弹窗。"
                is ConnectState.PressAllow -> "电视显示“允许 USB 调试？”时，先勾选“始终允许这台计算机”，再用电视遥控器选择“允许”。"
                is ConnectState.Pairing -> "在电视的开发者选项中打开“无线调试 → 使用配对码配对设备”，输入电视显示的六位配对码。"
                is ConnectState.TurnOnDebugging -> "电视能够响应，但网络调试尚未开启。请在电视“设置 → 系统 → 开发者选项”打开无线调试、网络调试或 ADB over network。选项置灰时，可先将电视 Wi-Fi 连接到手机所在网络，再重试。"
                is ConnectState.Unreachable -> "请确认电视已开机、地址正确，并与手机处于同一家庭网络。访客网络或路由器隔离可能阻止连接。"
                is ConnectState.Denied -> "请重新连接，并在电视上选择“允许”。"
                is ConnectState.Failed -> state.message
                else -> "可以使用遥控器和应用投送。"
            }
            Text(description, color = CompanionColors.Secondary, fontSize = 14.sp)
            if (waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state is ConnectState.Pairing) {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("六位配对码") }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                if (state.tv.pairPort == null) OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("配对端口") }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                state.error?.let { Text(it, color = CompanionColors.Error, fontSize = 13.sp) }
                val validPort = state.tv.pairPort ?: port.toIntOrNull()
                CompanionButton(if (state.busy) "配对中…" else "配对并连接", { onEvent(ConnectEvent.SubmitCode(code, port.toIntOrNull())) }, Modifier.fillMaxWidth(),
                    enabled = !state.busy && code.length == 6 && validPort != null && validPort in 1..65535, primary = true)
            }
        }
        if (state is ConnectState.TurnOnDebugging || state is ConnectState.Unreachable || state is ConnectState.Denied || state is ConnectState.Failed || (state is ConnectState.PressAllow && state.canRetry)) {
            CompanionButton("重试连接", { onEvent(ConnectEvent.Retry) }, Modifier.fillMaxWidth(), primary = true)
        }
        CompanionButton("选择其他设备", { onEvent(ConnectEvent.Back) }, Modifier.fillMaxWidth())
        if (state is ConnectState.Failed) TextButton(onShareLogs) { Text("导出诊断日志") }
    }
}
