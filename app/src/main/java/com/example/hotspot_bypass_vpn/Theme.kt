package com.example.hotspot_bypass_vpn

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Minimalist, flat visual language (see gui-preview/AGENTS.md): near-black
 * background, one cyan accent, thin dividers, no gradients or decorative cards.
 */
object AppColors {
    val Background = Color(0xFF080C0E)
    val Surface = Color(0xFF12191C)
    val Accent = Color(0xFF25C7D3)
    val AccentPressed = Color(0xFF58DBE3)
    val AccentSoft = Color(0xFF9EE8EC)
    val AccentMuted = Color(0xFF4DB9C1)
    val OnAccent = Color(0xFF041012)

    val TextPrimary = Color(0xFFF4F7F7)
    val TextSecondary = Color(0xFF8A9499)
    val TextLabel = Color(0xFF9BA6AB)
    val TextFieldLabel = Color(0xFFAEB6B9)
    val TextSubtle = Color(0xFFD6DDDD)
    val TextDisabled = Color(0xFF687277)
    val TabInactive = Color(0xFF788187)
    val TextLog = Color(0xFF778287)

    val Divider = Color(0xFF293136)
    val Outline = Color(0xFF30383D)
    val OutlineStrong = Color(0xFF465055)
    val Warning = Color(0xFFF2B84B)
    val Error = Color(0xFFFF6B6B)
}

private val ColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = AppColors.AccentSoft,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Background,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Divider,
    error = AppColors.Error,
)

object AppText {
    val Brand = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em, color = AppColors.Accent)
    val Headline = TextStyle(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.04).em, color = AppColors.TextPrimary)
    val Lede = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, color = AppColors.TextSecondary)
    val Section = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, color = AppColors.TextPrimary)
    val Eyebrow = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.16.em, color = AppColors.TextLabel)
    val RowLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em, color = AppColors.TextLabel)
    val RowValue = TextStyle(fontSize = 17.sp, letterSpacing = (-0.015).em, color = AppColors.TextPrimary, fontFeatureSettings = "tnum")
    val FieldLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextFieldLabel)
    val Body = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.TextSecondary)
    val Action = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
    val Button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val Log = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, color = AppColors.TextLog, fontFeatureSettings = "tnum")
}

@Composable
fun BypassVPNTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography(),
        content = content
    )
}
