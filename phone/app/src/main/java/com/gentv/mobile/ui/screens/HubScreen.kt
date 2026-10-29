package com.gentv.mobile.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
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
import com.gentv.mobile.TvSummary
import com.gentv.mobile.ui.GenIcons

private object HomePalette {
    val Background = Color(0xFF131315)
    val Card = Color(0xFF222224)
    val Text = Color(0xFFE5E1E4)
    val Secondary = Color(0xFFC6C6CB)
    val Muted = Color(0xFF9296A1)
    val Blue = Color(0xFFAAC7FF)
    val Green = Color(0xFF47E266)
    val Border = Color.White.copy(alpha = .075f)
}

/** Homepage-only theme and presentation. Callbacks retain the existing TV operations. */
@Composable
fun HubScreen(
    tv: TvSummary?,
    appsOff: Int,
    fireTv: Boolean,
    onTidy: () -> Unit,
    onRemote: () -> Unit,
    onFiles: () -> Unit,
    onHomeScreen: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    onCheck: () -> Unit = {},
    onSpace: () -> Unit = {},
    onAds: () -> Unit = {},
    onApps: () -> Unit = {},
    onDesktop: () -> Unit = {},
    onPush: () -> Unit = onApps,
    onSettings: () -> Unit = {},
    tvScreen: android.graphics.Bitmap? = null,
    onRefreshScreen: () -> Unit = {},
) {
    androidx.compose.runtime.LaunchedEffect(tv?.serial) { onRefreshScreen() }
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val statusLight = controller?.isAppearanceLightStatusBars
        val navLight = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            statusLight?.let { controller?.isAppearanceLightStatusBars = it }
            navLight?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = HomePalette.Blue, background = HomePalette.Background,
        surface = HomePalette.Card, onSurface = HomePalette.Text, onBackground = HomePalette.Text,
    )) {
        Column(Modifier.fillMaxSize().background(HomePalette.Background).safeDrawingPadding()) {
            HomeHeader(tv, onBack, onSettings)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceShowcase(tv, onBack, tvScreen)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickEntry("应用管理", GenIcons.Apps, onApps, Modifier.weight(1f))
                    QuickEntry("文件快传", GenIcons.Send, onFiles, Modifier.weight(1f))
                    if (!fireTv) QuickEntry("电视桌面", GenIcons.Home, onHomeScreen, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("系统维护与优化", color = HomePalette.Secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("按需检测", color = HomePalette.Muted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MaintenanceCard("空间深度清理", "查看存储与缓存", "待检测", GenIcons.Storage, false, onSpace, Modifier.weight(1f).fillMaxHeight())
                    MaintenanceCard("广告过滤", "查看电视 DNS 配置", "待检测", GenIcons.Shield, true, onAds, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureEntry("电视诊断", "检查应用与桌面状态", GenIcons.Check, onCheck, Modifier.weight(1f).fillMaxHeight())
                    FeatureEntry("配置还原", "撤销最近的电视更改", GenIcons.Restore, onRestore, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureEntry("应用精简", if (appsOff > 0) "已停用 $appsOff 项" else "选择需要停用的应用", GenIcons.Clean, onTidy, Modifier.weight(1f).fillMaxHeight())
                    if (!fireTv) FeatureEntry("桌面整理", "排序、改名与外观", GenIcons.Home, onDesktop, Modifier.weight(1f).fillMaxHeight())
                    else Spacer(Modifier.weight(1f))
                }
            }
            HomeNavigation(onRemote, onBack, onPush)
        }
    }
}

@Composable
private fun HomeHeader(tv: TvSummary?, onDevices: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF101012)).heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDevices).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.Wifi, null, Modifier.size(17.dp), tint = HomePalette.Green)
            Text(tv?.title ?: "选择电视", localize = tv == null, color = HomePalette.Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", Modifier.size(22.dp), tint = HomePalette.Secondary) }
    }
}

@Composable
private fun HomeSurface(modifier: Modifier = Modifier, brush: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(brush ?: Brush.linearGradient(listOf(HomePalette.Card, HomePalette.Card)))
        .border(1.dp, HomePalette.Border, RoundedCornerShape(20.dp)), content = content)
}

@Composable
private fun DeviceShowcase(tv: TvSummary?, onDevices: () -> Unit, screenshot: android.graphics.Bitmap?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
        TelevisionPanel(tv, onDevices, screenshot)
    }
}

@Composable
private fun TelevisionPanel(tv: TvSummary?, onDevices: () -> Unit, screenshot: android.graphics.Bitmap?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF7A8392), Color(0xFF25282D), Color(0xFF15171B)))).padding(2.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF071323))) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF183E5C), Color(0xFF06182B), Color(0xFF31527C)), Offset.Zero, Offset(size.width, size.height)))
                    drawRect(Brush.radialGradient(listOf(Color(0xFF3AA481).copy(alpha = .24f), Color.Transparent), center = Offset(size.width * .50f, size.height * .50f), radius = size.width * .5f))
                    drawRect(Brush.radialGradient(listOf(HomePalette.Blue.copy(alpha = .38f), Color.Transparent), center = Offset(size.width * .95f, size.height * .80f), radius = size.width * .62f))
                    val spacing = 7.dp.toPx()
                    var y = spacing
                    while (y < size.height) {
                        var x = spacing
                        while (x < size.width) { drawCircle(Color.White.copy(alpha = .05f), .8.dp.toPx(), Offset(x, y)); x += spacing }
                        y += spacing
                    }
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, .40f to Color.Transparent, 1f to Color.Black.copy(alpha = .6f)))
                }
                if (screenshot != null) {
                    androidx.compose.foundation.Image(screenshot.asImageBitmap(), "Connected TV screen",
                        Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent, Color.Black.copy(alpha = .7f)))))
                }
                Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Row(Modifier.clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDevices).heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("切换设备", fontSize = 12.sp, color = HomePalette.Secondary)
                            Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(15.dp), tint = HomePalette.Secondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(tv?.model ?: "我的电视", localize = tv == null, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (tv != null) Text("Android TV", fontSize = 10.sp, color = Color.White.copy(alpha = .65f))
                        }

                    }
                }
            }
        }
        Box(Modifier.width(48.dp).height(10.dp).background(Brush.verticalGradient(listOf(Color(0xFF43454B), Color(0xFF252629)))))
        Box(Modifier.width(146.dp).height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF454647), Color(0xFF87898A), Color(0xFF454647)))).border(.5.dp, Color.White.copy(alpha = .2f), CircleShape))
    }
}

@Composable
private fun ScreenPill(text: String, icon: ImageVector? = null) {
    Row(Modifier.background(Color(0xBB06121D), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = HomePalette.Blue)
        else Box(Modifier.size(5.dp).background(HomePalette.Green, CircleShape))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HomePalette.Text)
    }
}

@Composable
private fun QuickEntry(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    HomeSurface(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(43.dp).background(Color(0xFF111113), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = HomePalette.Text) }
            Text(title, fontSize = 12.sp, color = HomePalette.Text, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MaintenanceCard(title: String, subtitle: String, status: String, icon: ImageVector, green: Boolean, onClick: () -> Unit, modifier: Modifier) {
    HomeSurface(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AccentIcon(icon, if (green) HomePalette.Green else HomePalette.Blue, 36)
                Text(status, color = if (green) HomePalette.Green else HomePalette.Secondary, fontSize = 11.sp, modifier = Modifier.background(if (green) HomePalette.Green.copy(alpha = .12f) else Color.Transparent, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 16.sp, color = HomePalette.Text, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 11.sp, color = HomePalette.Secondary)
            }
        }
    }
}

@Composable
private fun FeatureEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    HomeSurface(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 136.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(25.dp), tint = HomePalette.Blue)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, color = HomePalette.Text, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 11.sp, lineHeight = 16.sp, color = HomePalette.Muted,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AccentIcon(icon: ImageVector, tint: Color, size: Int) {
    Box(Modifier.size(size.dp).background(tint.copy(alpha = .12f), CircleShape).border(1.dp, tint.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .6f).dp), tint = tint)
    }
}

@Composable
private fun Chevron() { Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Color(0xFF646C7E)) }

@Composable
private fun HomeNavigation(onRemote: () -> Unit, onDevices: () -> Unit, onApps: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF101012)).padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        NavItem("首页", Icons.Outlined.Tv, true, {})
        NavItem("遥控器", GenIcons.Remote, false, onRemote)
        NavItem("发现设备", Icons.Outlined.Sensors, false, onDevices)
        NavItem("应用投送", Icons.Outlined.Cast, false, onApps)
    }
}

@Composable
private fun NavItem(title: String, icon: ImageVector, current: Boolean, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 64.dp).heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp)).semantics { selected = current }.clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val color = if (current) HomePalette.Blue else HomePalette.Secondary
        Icon(icon, null, Modifier.size(23.dp), tint = color)
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
