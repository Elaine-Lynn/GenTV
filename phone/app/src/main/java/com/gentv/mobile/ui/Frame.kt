package com.gentv.mobile.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight

/**
 * Every screen: a white rounded sheet on the pale-blue backdrop, a scrolling body and an
 * optional pinned bottom action so the main button is always in reach.
 */
@Composable
fun ScreenFrame(
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(GenTVPalette.Backdrop).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 0.dp, vertical = 0.dp)
                .background(GenTVPalette.Backdrop),
        ) {
            if (top != null) Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp)) { top() }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = body,
            )
            if (bottom != null) Column(
                Modifier.fillMaxWidth().background(GenTVPalette.Backdrop).padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = bottom,
            )
        }
    }
}

/**
 * The bar on every screen inside My TV: a back link on the left and the TV's name on the right,
 * so it's always clear which TV the buttons act on.
 */
@Composable
fun TopBar(back: String, onBack: () -> Unit, tvName: String?, showSettings: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextLink(back, onBack, icon = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack)
        Spacer(Modifier.weight(1f))
        if (showSettings) TextLink("Settings", LocalAppActions.current.settings)
        if (tvName != null) Text(
            tvName, style = MaterialTheme.typography.labelMedium, color = GenTVPalette.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp).background(GenTVPalette.Grey, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Text(text, style = MaterialTheme.typography.headlineMedium, color = GenTVPalette.Ink, textAlign = align,
        modifier = modifier.fillMaxWidth().semantics { heading() })

@Composable
fun Body(text: String, color: Color = GenTVPalette.Muted, align: TextAlign = TextAlign.Start) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = align, modifier = Modifier.fillMaxWidth())

/** Speech bubble under the GenTV symbol at the top of a screen. */
@Composable
fun Bubble(text: String, color: Color = GenTVPalette.BlueSoft, textColor: Color = GenTVPalette.Ink, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(color).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Text(text, style = MaterialTheme.typography.titleLarge, color = textColor, modifier = Modifier.semantics { heading() })
    }
}

/** Screen moods; each currently shows the GenTV symbol. */
enum class Mood(val res: Int, val description: String) {
    STAND(com.gentv.mobile.R.drawable.ic_gentv, "GenTV television symbol"),
    SEARCH(com.gentv.mobile.R.drawable.ic_gentv, "GenTV checking a television"),
    HAPPY(com.gentv.mobile.R.drawable.ic_gentv, "GenTV ready"),
    CHEER(com.gentv.mobile.R.drawable.ic_gentv, "GenTV completed"),
    WORRIED(com.gentv.mobile.R.drawable.ic_gentv, "GenTV needs attention"),
}

/** The mascot at the top of a screen, gently bobbing. */
@Composable
fun HeroMascot(worried: Boolean = false, mood: Mood = if (worried) Mood.WORRIED else Mood.STAND, height: androidx.compose.ui.unit.Dp = 150.dp) {
    if (height < 120.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("GenTV", style = MaterialTheme.typography.titleLarge, color = GenTVPalette.Ink)
            Spacer(Modifier.weight(1f))
            Text(if (worried) "连接状态" else "电视好伴侣", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
        }
    } else Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width * .76f
            val h = size.height * .68f
            val x = (size.width - w) / 2
            val y = size.height * .08f
            drawRoundRect(Color(0xFF0B0B0D), Offset(x + 8f, y + 12f), Size(w, h), CornerRadius(20f))
            drawRoundRect(Color(0xFF29292D), Offset(x, y), Size(w, h), CornerRadius(18f))
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF9FB4C8), Color(0xFF456986), Color(0xFF162C46)), Offset(x, y), Offset(x+w, y+h)), Offset(x+7f,y+7f), Size(w-14f,h-14f), CornerRadius(12f))
            drawRoundRect(Color.White.copy(alpha=.1f), Offset(x+20f,y+20f), Size(w*.44f,h*.62f), CornerRadius(8f))
            drawLine(Color(0xFF86868B), Offset(size.width*.5f,y+h+2f), Offset(size.width*.5f,y+h+size.height*.09f), 6f)
            drawRoundRect(Color(0xFF86868B),Offset(size.width*.39f,y+h+size.height*.09f),Size(size.width*.22f,5f),CornerRadius(3f))
        }
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Switch(
        checked = on, onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(0xFF14281B), checkedTrackColor = GenTVPalette.Green, checkedBorderColor = GenTVPalette.Green,
            uncheckedThumbColor = GenTVPalette.Muted, uncheckedTrackColor = GenTVPalette.Grey, uncheckedBorderColor = GenTVPalette.Line,
        ),
    )
}

/** Numbered instruction, used on Get ready and the help states. */
@Composable
fun NumberedStep(n: Int, title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(GenTVPalette.Blue), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.titleMedium, color = Color(0xFF14213A))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
        }
    }
}

/** A little TV with a colourful screen, standing in for the TV's own picture. */
@Composable
fun TvPicture(width: androidx.compose.ui.unit.Dp = 92.dp) {
    Canvas(Modifier.size(width, width * 0.66f)) {
        val w = size.width; val h = size.height
        drawRoundRect(GenTVPalette.Grey, Offset(0f, 0f), Size(w, h * 0.86f), CornerRadius(w * 0.04f))
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF9FB4C8), Color(0xFF456986), Color(0xFF162C46)), Offset(0f, 0f), Offset(w, h)),
            Offset(w * 0.035f, h * 0.05f), Size(w * 0.93f, h * 0.74f), CornerRadius(w * 0.02f),
        )
        drawLine(GenTVPalette.Muted, Offset(w * 0.2f, h * 0.86f), Offset(w * 0.14f, h), strokeWidth = w * 0.03f)
        drawLine(GenTVPalette.Muted, Offset(w * 0.8f, h * 0.86f), Offset(w * 0.86f, h), strokeWidth = w * 0.03f)
    }
}

/** Card for one TV in the scan list and on My TVs. */
@Composable
fun TvCard(title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GenTVPalette.Card)
            .border(.5.dp, GenTVPalette.Line, RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPicture(84.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val marquee = if (androidx.compose.ui.platform.LocalInspectionMode.current) Modifier else Modifier.basicMarquee(
                iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = 24.dp,
            )
            Text(title, localize = false, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 22.sp), color = GenTVPalette.Ink,
                maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().then(marquee))
            Text(subtitle, localize = false, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 18.sp), color = GenTVPalette.Muted,
                maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().then(marquee))
        }
        if (trailing != null) trailing() else if (onClick != null) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = GenTVPalette.Muted)
        }
    }
}

/** Round coloured badge holding a glyph, for group headers. */
@Composable
fun Badge(color: Color, glyph: String) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = Color(0xFF14213A))
    }
}

@Composable
fun Outline(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    CardBox(modifier, border = GenTVPalette.Line, content = content)
