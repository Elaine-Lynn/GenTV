package com.gentv.mobile.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object CompanionColors {
    val Background = Color(0xFF131315)
    val Card = Color(0xFF222224)
    val Raised = Color(0xFF2A2A2C)
    val Text = Color(0xFFE5E1E4)
    val Secondary = Color(0xFFC6C6CB)
    val Muted = Color(0xFF9296A1)
    val Blue = Color(0xFFAAC7FF)
    val Green = Color(0xFF47E266)
    val Error = Color(0xFFFFB4AB)
}

@Composable
fun CompanionScreen(title: String, tvName: String?, tab: Int, onNavigate: (Int) -> Unit,
    scroll: Boolean = true, onBack: (() -> Unit)? = null, showTitle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            lightNav?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = CompanionColors.Blue, onPrimary = Color(0xFF003064),
        surface = CompanionColors.Card, background = CompanionColors.Background, onSurface = CompanionColors.Text,
        onBackground = CompanionColors.Text, surfaceContainerHigh = CompanionColors.Raised)) {
        Column(Modifier.fillMaxSize().background(CompanionColors.Background).safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF101012)).heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(GenIcons.Back, "返回", tint = CompanionColors.Blue) }
                if (showTitle) {
                Box(Modifier.size(8.dp).background(if (tvName != null) CompanionColors.Green else CompanionColors.Blue, CircleShape))
                Text(title, color = CompanionColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                }
                Row(Modifier.clip(CircleShape).background(CompanionColors.Raised).clickable { onNavigate(2) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Wifi, null, Modifier.size(17.dp), tint = CompanionColors.Green)
                    Text(tvName ?: "局域网", fontSize = if (showTitle) 12.sp else 14.sp, color = CompanionColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, localize = tvName == null, modifier = Modifier.widthIn(max = if (showTitle) 132.dp else 260.dp))
                }
                if (!showTitle) Spacer(Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            Row(Modifier.fillMaxWidth().background(Color(0xFF101012)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                val icons = listOf(Icons.Outlined.Tv, GenIcons.Remote, Icons.Outlined.Sensors, Icons.Outlined.Cast)
                listOf("首页", "遥控器", "发现设备", "应用投送").forEachIndexed { index, label ->
                    Column(Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).semantics { selected = index == tab }
                        .clickable(role = Role.Tab) { if (index != tab) onNavigate(index) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val color = if (index == tab) CompanionColors.Blue else CompanionColors.Secondary
                        Icon(icons[index], null, Modifier.size(23.dp), tint = color)
                        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CompanionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CompanionColors.Card)
        .border(1.dp, Color.White.copy(alpha = .055f), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun CompanionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) CompanionColors.Blue else CompanionColors.Raised,
            contentColor = if (primary) Color(0xFF003064) else CompanionColors.Text)) { Text(text, fontSize = 14.sp) }
}
