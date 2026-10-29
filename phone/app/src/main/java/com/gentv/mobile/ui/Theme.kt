package com.gentv.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gentv.mobile.R

/** Shared charcoal surfaces, mist-blue actions and green operational status. */
object GenTVPalette {
    val Backdrop = Color(0xFF131315)
    val Card = Color(0xFF222224)
    val Ink = Color(0xFFE5E1E4)
    val Muted = Color(0xFFA5A5AD)
    val Line = Color(0xFF36363A)
    val Blue = Color(0xFFAAC7FF)
    val BlueSoft = Color(0xFF293244)
    val Green = Color(0xFF47E266)
    val GreenSoft = Color(0xFF203B28)
    val GreenInk = Color(0xFF69DF83)
    val Orange = Color(0xFFD6B886)
    val Purple = Color(0xFFA5A5AD)
    val Grey = Color(0xFF333337)
    val Amber = Color(0xFF3B3021)
    val AmberInk = Color(0xFFF0C788)
    val Red = Color(0xFFFF9B9B)
    val RedSoft = Color(0xFF40282C)
    val Yellow = Color(0xFF2A2A2C)
}

val Nunito = FontFamily.SansSerif

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 34.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(500), fontSize = 13.sp),
)

@Composable
fun GenTVTheme(content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GenTVPalette.Blue, onPrimary = Color(0xFF14213A), onSecondary = Color(0xFF102817),
            primaryContainer = GenTVPalette.BlueSoft, onPrimaryContainer = GenTVPalette.Blue,
            secondaryContainer = GenTVPalette.GreenSoft, onSecondaryContainer = GenTVPalette.GreenInk,
            surfaceContainer = GenTVPalette.Card, surfaceContainerHigh = GenTVPalette.Grey,
            surfaceContainerHighest = GenTVPalette.Grey, surfaceContainerLow = GenTVPalette.Card,
            surfaceVariant = GenTVPalette.Grey, onSurfaceVariant = GenTVPalette.Muted, outline = GenTVPalette.Line,
            errorContainer = GenTVPalette.RedSoft, onErrorContainer = GenTVPalette.Red, onError = Color(0xFF381316), background = GenTVPalette.Backdrop, surface = GenTVPalette.Card,
            onSurface = GenTVPalette.Ink, onBackground = GenTVPalette.Ink, secondary = GenTVPalette.Green, error = GenTVPalette.Red,
        ),
        typography = typography,
        content = content,
    )
}
