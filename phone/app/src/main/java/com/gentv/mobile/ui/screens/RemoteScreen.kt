package com.gentv.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gentv.mobile.RemoteApp
import com.gentv.mobile.bar
import com.gentv.mobile.ordered
import com.gentv.mobile.RemoteState
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.Bubble
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.TextLink
import com.gentv.mobile.ui.CompanionColors
import com.gentv.mobile.ui.CompanionScreen
import com.gentv.mobile.ui.CompanionCard
import com.gentv.mobile.ui.CompanionButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.core.ops.RemoteKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.unit.min

private object RemotePalette {
    val Backdrop = CompanionColors.Background
    val Blue = CompanionColors.Blue
    val BlueSoft = CompanionColors.Raised
    val Card = CompanionColors.Card
    val Ink = CompanionColors.Text
    val Muted = CompanionColors.Secondary
    val Orange = Color(0xFFF0C789)
    val Red = CompanionColors.Error
    val RedSoft = Color(0xFF3B292B)
}
private val DeepBlue = Color(0xFF202024)
private val PadPaper = Color(0xFF252529)
private val HoldDelayMs = 350L
private val HoldEveryMs = 125L // about 8 keys a second
private val TouchpadStep = 48.dp

/** The phone remote: one screen, nothing to scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    s: RemoteState,
    tvName: String?,
    onKey: (RemoteKey) -> Unit,
    onType: (String) -> Unit,
    onLaunch: (RemoteApp) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onPin: (String) -> Unit = {},
    onMovePin: (String, Int) -> Unit = { _, _ -> },
    onNavigate: (Int) -> Unit = { onBack() },
) {
    val haptic = LocalHapticFeedback.current
    val enabled = !s.reconnecting
    var touchpad by rememberSaveable { mutableStateOf(true) }
    var keyboard by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf(false) }
    val press: (RemoteKey) -> Unit = { k ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKey(k)
    }

    CompanionScreen("Remote", tvName, 1, onNavigate, showTitle = false) {
        if (s.reconnecting) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在重新连接电视…", color = CompanionColors.Secondary, fontSize = 12.sp)
        }
        if (s.message != null) CompanionCard {
            Text(s.message, color = CompanionColors.Secondary, fontSize = 13.sp)
            TextButton(onClick = onDismissMessage) { Text("知道了") }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CompanionColors.Card).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Icons.Rounded.PowerSettingsNew, "Power", enabled, CompanionColors.Raised, CompanionColors.Error) { press(RemoteKey.POWER) }
            RoundIcon(Icons.Rounded.Keyboard, "Keyboard", enabled, CompanionColors.Raised, CompanionColors.Blue) { keyboard = true }
            RoundIcon(Icons.Rounded.Menu, "Menu", enabled, CompanionColors.Raised, CompanionColors.Text) { press(RemoteKey.MENU) }
            RoundIcon(Icons.AutoMirrored.Rounded.VolumeOff, "Mute", enabled, CompanionColors.Raised, CompanionColors.Text) { press(RemoteKey.MUTE) }
        }
        ModeSwitch(touchpad, enabled) { touchpad = it }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f).alpha(if (enabled) 1f else .4f)) {
                if (touchpad) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF27272A)).border(1.dp, Color.White.copy(alpha = .04f), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize(.52f)) { Touchpad(enabled, press) }
                        HoldKey(Icons.Rounded.KeyboardArrowUp, "Up", enabled, Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(52.dp, 44.dp)) { press(RemoteKey.UP) }
                        HoldKey(Icons.Rounded.KeyboardArrowDown, "Down", enabled, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(52.dp, 44.dp)) { press(RemoteKey.DOWN) }
                        HoldKey(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", enabled, Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(44.dp, 52.dp)) { press(RemoteKey.LEFT) }
                        HoldKey(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", enabled, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(44.dp, 52.dp)) { press(RemoteKey.RIGHT) }
                    }
                } else DPad(enabled, press)
            }
        }
        Text(if (touchpad) "轻滑移动 · 点按确认 · 长按返回" else "方向按键 · 长按连续移动", color = CompanionColors.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth().height(146.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VolumeRocker(enabled, press)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavPill(Icons.AutoMirrored.Rounded.ArrowBack, "Back", enabled, Modifier.weight(1f)) { press(RemoteKey.BACK) }
                    NavPill(Icons.Rounded.Home, "Home", enabled, Modifier.weight(1f)) { press(RemoteKey.HOME) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavPill(Icons.Rounded.PlayArrow, "Play or pause", enabled, Modifier.weight(1f)) { press(RemoteKey.PLAY_PAUSE) }
                    NavPill(Icons.Rounded.Keyboard, "Keyboard", enabled, Modifier.weight(1f)) { keyboard = true }
                }
            }
            Column(Modifier.width(52.dp).fillMaxHeight().clip(CircleShape).background(CompanionColors.Raised)) {
                HoldKey(Icons.Rounded.FastForward, "Fast forward", enabled, Modifier.weight(1f).fillMaxWidth()) { press(RemoteKey.FAST_FORWARD) }
                HoldKey(Icons.Rounded.FastRewind, "Rewind", enabled, Modifier.weight(1f).fillMaxWidth()) { press(RemoteKey.REWIND) }
            }
        }
        QuickApps(s, enabled, onLaunch) { allApps = true }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = CompanionColors.Blue, surface = CompanionColors.Card, onSurface = CompanionColors.Text)) {
        if (keyboard) KeyboardSheet(enabled, press, onType) { keyboard = false }
        if (allApps) AppsSheet(s, onLaunch, onPin, onMovePin) { allApps = false }
    }
}

// ---------- top row ----------

@Composable
private fun TopRow(tvName: String?, enabled: Boolean, onBack: () -> Unit, onPower: () -> Unit, onKeyboard: () -> Unit, onApps: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIcon(Icons.Rounded.PowerSettingsNew, "Power", enabled, RemotePalette.RedSoft, RemotePalette.Red, onClick = onPower)
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(999.dp)).background(RemotePalette.BlueSoft)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back to My TV" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp), tint = RemotePalette.Blue)
            Spacer(Modifier.width(6.dp))
            Text(
                tvName ?: "My TV", style = MaterialTheme.typography.titleMedium, color = RemotePalette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        }
        RoundIcon(Icons.Rounded.Keyboard, "Keyboard", enabled, RemotePalette.BlueSoft, RemotePalette.Blue, onClick = onKeyboard)
        RoundIcon(Icons.Rounded.Apps, "Apps", enabled, RemotePalette.BlueSoft, RemotePalette.Blue, onClick = onApps)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, enabled: Boolean, bg: Color, tint: Color, size: Dp = 48.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
}

// ---------- mode switch ----------

@Composable
private fun ModeSwitch(touchpad: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(999.dp)).background(RemotePalette.BlueSoft).padding(4.dp)) {
            Segment("方向按键", !touchpad, enabled) { onChange(false) }
            Segment("滑动触控", touchpad, enabled) { onChange(true) }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF46464B) else Color.Transparent, label = "seg")
    Box(
        Modifier.height(40.dp).widthIn(min = 104.dp)
            .then(if (selected) Modifier.shadow(3.dp, RoundedCornerShape(999.dp), ambientColor = RemotePalette.Blue.copy(alpha = 0.25f), spotColor = RemotePalette.Blue.copy(alpha = 0.25f)) else Modifier)
            .clip(RoundedCornerShape(999.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) RemotePalette.Blue else RemotePalette.Muted, modifier = Modifier.padding(horizontal = 14.dp))
    }
}

// ---------- the pad ----------

private enum class Zone(val key: RemoteKey?) { UP(RemoteKey.UP), DOWN(RemoteKey.DOWN), LEFT(RemoteKey.LEFT), RIGHT(RemoteKey.RIGHT), OK(null) }

/** Which part of a pad [size] wide the finger at [p] is on, or null outside the dial. */
private fun zoneAt(p: Offset, size: Float): Zone? {
    val c = size / 2
    val dx = p.x - c
    val dy = p.y - c
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    if (d > c * 1.04f) return null
    if (d < c * 0.34f) return Zone.OK
    return if (abs(dx) >= abs(dy)) (if (dx > 0) Zone.RIGHT else Zone.LEFT) else (if (dy > 0) Zone.DOWN else Zone.UP)
}

/** Fires [tick] now, then again every [HoldEveryMs] after [HoldDelayMs], until the finger lifts. */
private suspend fun PressGestureScope.repeatWhileHeld(tick: () -> Unit) = coroutineScope {
    tick()
    val job = launch {
        delay(HoldDelayMs)
        while (isActive) { tick(); delay(HoldEveryMs) }
    }
    try { awaitRelease() } finally { job.cancel() }
}

@Composable
private fun DPad(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    var down by remember { mutableStateOf<Zone?>(null) }
    val shape = CircleShape
    Box(
        Modifier.fillMaxSize()

            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF333337), DeepBlue))),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            Box(
                Modifier.size(side)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { p ->
                            val z = zoneAt(p, size.width.toFloat())
                            if (z == null || !on) return@detectTapGestures
                            down = z
                            try {
                                val k = z.key
                                if (k == null) { send(RemoteKey.OK); awaitRelease() }
                                else repeatWhileHeld { send(k) }
                            } finally { down = null }
                        })
                    },
            ) {
                DialDrawing(down, Modifier.fillMaxSize())
                // Chevrons and the OK disc, over the drawing.
                val r = side / 2
                val mid = r * 0.67f
                Chevron(Icons.Rounded.KeyboardArrowUp, "Up", enabled, down == Zone.UP, Modifier.align(Alignment.Center).offset(y = -mid)) { send(RemoteKey.UP) }
                Chevron(Icons.Rounded.KeyboardArrowDown, "Down", enabled, down == Zone.DOWN, Modifier.align(Alignment.Center).offset(y = mid)) { send(RemoteKey.DOWN) }
                Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", enabled, down == Zone.LEFT, Modifier.align(Alignment.Center).offset(x = -mid)) { send(RemoteKey.LEFT) }
                Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", enabled, down == Zone.RIGHT, Modifier.align(Alignment.Center).offset(x = mid)) { send(RemoteKey.RIGHT) }
                val ok = down == Zone.OK
                val okScale by animateFloatAsState(if (ok) 0.93f else 1f, label = "ok")
                Box(
                    Modifier.align(Alignment.Center).size(side * 0.4f).scale(okScale)
                        .shadow(if (ok) 2.dp else 8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = DeepBlue)
                        .clip(CircleShape).background(if (ok) Color(0xFF45454B) else Color(0xFF222225))
                        .semantics { contentDescription = "OK"; role = Role.Button; onClick { send(RemoteKey.OK); true } },
                    contentAlignment = Alignment.Center,
                ) { Text("OK", localize = false, style = MaterialTheme.typography.titleLarge, color = RemotePalette.Blue) }
            }
        }
    }
}

/** Four wedges around a hub; the one under the finger is lighter and a touch larger. */
@Composable
private fun DialDrawing(down: Zone?, modifier: Modifier) {
    val pressed = listOf(Zone.RIGHT, Zone.DOWN, Zone.LEFT, Zone.UP).map { z ->
        animateFloatAsState(if (down == z) 1f else 0f, label = "wedge").value
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val outer = size.width / 2
        val inner = outer * 0.42f
        val thick = outer - inner
        val mid = inner + thick / 2
        // Angles in Canvas degrees: 0 = right, 90 = down, 180 = left, 270 = up.
        for (i in 0 until 4) {
            val p = pressed[i]
            val gap = 5f
            withTransform({ scale(1f + 0.04f * p, 1f + 0.04f * p, c) }) {
                drawArc(
                    color = Color.White.copy(alpha = 0.025f + 0.12f * p),
                    startAngle = i * 90f - 45f + gap / 2, sweepAngle = 90f - gap, useCenter = false,
                    topLeft = Offset(c.x - mid, c.y - mid), size = Size(mid * 2, mid * 2),
                    style = Stroke(width = thick - 2f),
                )
            }
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, desc: String, enabled: Boolean, pressed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Touch handling is the dial's; this carries the glyph and the accessibility action.
    Box(
        modifier.size(56.dp).semantics { contentDescription = desc; role = Role.Button; if (enabled) onClick { onClick(); true } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(if (pressed) 42.dp else 38.dp), tint = CompanionColors.Secondary) }
}

@Composable
private fun Touchpad(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    val haptic = LocalHapticFeedback.current
    val stepPx = with(LocalDensity.current) { TouchpadStep.toPx() }
    val math = remember(stepPx) { TouchpadMath(stepPx) }
    val shape = CircleShape
    Box(
        Modifier.fillMaxSize()

            .clip(shape).background(PadPaper)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (on) send(RemoteKey.OK) },
                    onLongPress = { if (on) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); send(RemoteKey.BACK) } },
                )
            }
            .pointerInput(stepPx) {
                detectDragGestures(
                    onDragStart = { math.reset() },
                    onDragEnd = { math.reset() },
                    onDragCancel = { math.reset() },
                ) { change, drag ->
                    change.consume()
                    if (on) math.drag(drag.x, drag.y).forEach { send(it) }
                }
            }
            .semantics { contentDescription = "Touchpad" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 14.dp.toPx()
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) { drawCircle(RemotePalette.Blue.copy(alpha = 0.06f), .8.dp.toPx(), Offset(x, y)); x += gap }
                y += gap
            }
        }
        Box(Modifier.size(86.dp).background(Color(0xFF19191C), CircleShape))
        Text(
            "OK", localize = false,
            style = MaterialTheme.typography.titleLarge, color = CompanionColors.Text, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 10.dp),
        )
    }
}

// ---------- keys under the pad ----------

@Composable
private fun NavPill(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.height(52.dp).shadow(3.dp, shape, ambientColor = RemotePalette.Blue.copy(alpha = 0.2f), spotColor = RemotePalette.Blue.copy(alpha = 0.2f))
            .clip(shape).background(RemotePalette.BlueSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = RemotePalette.Blue)

    }
}

@Composable
private fun VolumeRocker(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    val shape = RoundedCornerShape(999.dp)
    Column(
        Modifier.width(64.dp).fillMaxHeight()
            .shadow(3.dp, shape, ambientColor = RemotePalette.Blue.copy(alpha = 0.2f), spotColor = RemotePalette.Blue.copy(alpha = 0.2f))
            .clip(shape).background(RemotePalette.BlueSoft),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HoldKey(Icons.Rounded.Add, "Volume up", enabled, Modifier.weight(1f).fillMaxWidth()) { send(RemoteKey.VOLUME_UP) }
        Box(
            Modifier.size(44.dp).shadow(3.dp, CircleShape, ambientColor = RemotePalette.Blue.copy(alpha = 0.25f), spotColor = RemotePalette.Blue.copy(alpha = 0.25f))
                .clip(CircleShape).background(Color(0xFF353538))
                .clickable(enabled = enabled, role = Role.Button) { send(RemoteKey.MUTE) }
                .semantics { contentDescription = "Mute" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, Modifier.size(22.dp), tint = RemotePalette.Blue) }
        HoldKey(Icons.Rounded.Remove, "Volume down", enabled, Modifier.weight(1f).fillMaxWidth()) { send(RemoteKey.VOLUME_DOWN) }
    }
}

/** A key that repeats while it is held, like the arrows. */
@Composable
private fun HoldKey(icon: ImageVector, desc: String, enabled: Boolean, modifier: Modifier, tick: () -> Unit) {
    val onTick by rememberUpdatedState(tick)
    val on by rememberUpdatedState(enabled)
    var held by remember { mutableStateOf(false) }
    Box(
        modifier.heightIn(min = 48.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!on) return@detectTapGestures
                    held = true
                    try { repeatWhileHeld { onTick() } } finally { held = false }
                })
            }
            .semantics { contentDescription = desc; role = Role.Button; if (enabled) onClick { onTick(); true } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(if (held) 30.dp else 26.dp), tint = RemotePalette.Blue) }
}

@Composable
private fun MediaPill(enabled: Boolean, press: (RemoteKey) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .shadow(3.dp, shape, ambientColor = RemotePalette.Blue.copy(alpha = 0.2f), spotColor = RemotePalette.Blue.copy(alpha = 0.2f))
            .clip(shape).background(RemotePalette.BlueSoft).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundIcon(Icons.Rounded.FastRewind, "Rewind", enabled, Color.Transparent, RemotePalette.Blue) { press(RemoteKey.REWIND) }
        Box(
            Modifier.size(52.dp).shadow(4.dp, CircleShape, ambientColor = RemotePalette.Blue.copy(alpha = 0.3f), spotColor = RemotePalette.Blue.copy(alpha = 0.3f))
                .clip(CircleShape).background(RemotePalette.Blue)
                .clickable(enabled = enabled, role = Role.Button) { press(RemoteKey.PLAY_PAUSE) }
                .semantics { contentDescription = "Play or pause" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(30.dp), tint = CompanionColors.Secondary) }
        RoundIcon(Icons.Rounded.FastForward, "Fast forward", enabled, Color.Transparent, RemotePalette.Blue) { press(RemoteKey.FAST_FORWARD) }
    }
}

@Composable
private fun QuickApps(s: RemoteState, enabled: Boolean, onLaunch: (RemoteApp) -> Unit, onAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        s.bar().forEach { app ->
            Box(
                Modifier.size(52.dp).shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = RemotePalette.Blue.copy(alpha = 0.2f), spotColor = RemotePalette.Blue.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF353538))
                    .clickable(enabled = enabled, role = Role.Button) { onLaunch(app) }
                    .semantics { contentDescription = app.label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(app.pkg, app.label, 34.dp) }
        }
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(RemotePalette.BlueSoft)
                .clickable(enabled = enabled, role = Role.Button, onClick = onAll)
                .semantics { contentDescription = "All apps" }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Apps, null, Modifier.size(22.dp), tint = RemotePalette.Blue)
            Spacer(Modifier.width(6.dp))
            Text("All", style = MaterialTheme.typography.labelMedium, color = RemotePalette.Ink, maxLines = 1)
        }
    }
}

// ---------- sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardSheet(enabled: Boolean, press: (RemoteKey) -> Unit, onType: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val send = {
        if (text.isNotEmpty()) { onType(text); text = "" }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("输入到电视", style = MaterialTheme.typography.titleMedium, color = RemotePalette.Ink)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text("搜索或输入文字") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIcon(Icons.AutoMirrored.Rounded.Backspace, "Delete", enabled, RemotePalette.BlueSoft, RemotePalette.Blue) { press(RemoteKey.DELETE) }
                RoundIcon(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter", enabled, RemotePalette.BlueSoft, RemotePalette.Blue) { press(RemoteKey.ENTER) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).background(RemotePalette.Blue.copy(alpha = if (enabled) 1f else 0.5f))
                        .clickable(enabled = enabled, role = Role.Button) { send() }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("发送", style = MaterialTheme.typography.labelLarge, color = Color(0xFF003064)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsSheet(s: RemoteState, onLaunch: (RemoteApp) -> Unit, onPin: (String) -> Unit, onMove: (String, Int) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                s.loadingApps -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Spinner(); Spacer(Modifier.width(10.dp))
                    Text("Loading apps…", style = MaterialTheme.typography.bodyLarge, color = RemotePalette.Muted)
                }
                s.apps.isEmpty() -> Text("No apps found", style = MaterialTheme.typography.bodyLarge, color = RemotePalette.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                else -> {
                    val ordered = s.ordered()
                    val pinned = com.gentv.core.lists.RemoteBar.pinned(s.apps.map { it.pkg }, s.pinned)
                    val by = s.apps.associateBy { it.pkg }
                    Text("Apps on your remote", style = MaterialTheme.typography.titleMedium, color = RemotePalette.Ink)
                    Text("Tap the star to put an app on the remote's bar. Use the arrows to choose which comes first.", style = MaterialTheme.typography.bodyMedium, color = RemotePalette.Muted)
                    if (pinned.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pinned.forEachIndexed { i, pkg ->
                            val app = by[pkg] ?: return@forEachIndexed
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                AppIcon(app.pkg, app.label, 44.dp)
                                Text(app.label, localize = false, style = MaterialTheme.typography.labelSmall, color = RemotePalette.Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Row {
                                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Move ${app.label} earlier", i > 0) { onMove(pkg, -1) }
                                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Move ${app.label} later", i < pinned.lastIndex) { onMove(pkg, +1) }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().height(380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ordered.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app ->
                                    val on = app.pkg in pinned
                                    Box(Modifier.weight(1f)) {
                                        Column(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                .clickable(role = Role.Button) { onClose(); onLaunch(app) }
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AppIcon(app.pkg, app.label, 56.dp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(app.label, localize = false, style = MaterialTheme.typography.labelMedium, color = RemotePalette.Ink, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(40.dp).clip(CircleShape)
                                                .clickable(role = Role.Checkbox) { onPin(app.pkg) }
                                                .semantics { contentDescription = if (on) "Take ${app.label} off the remote bar" else "Put ${app.label} on the remote bar" },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(22.dp), tint = if (on) RemotePalette.Orange else RemotePalette.Muted)
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) RemotePalette.Blue else RemotePalette.Muted.copy(alpha = 0.35f)) }
}
