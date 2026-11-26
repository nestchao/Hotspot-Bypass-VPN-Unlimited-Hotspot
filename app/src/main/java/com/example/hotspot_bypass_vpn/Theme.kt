package com.example.hotspot_bypass_vpn

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

// Dark Theme (Cyber)
val DarkPurpleBg = Color(0xFF101719)
val DarkCardPurple = Color(0xFF1A2427)
val CyberTeal = Color(0xFF68D7CE)
val CyberPurple = Color(0xFF92A7AE)

// Light Theme (Clean)
val LightBg = Color(0xFFF6F9F8)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimaryTeal = Color(0xFF006E67)
val LightSecondary = Color(0xFF476A70)

private val DarkColorScheme = darkColorScheme(
    primary = CyberTeal,
    secondary = CyberPurple,
    background = DarkPurpleBg,
    surface = DarkCardPurple,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = CyberTeal.copy(alpha = 0.1f),
    onPrimaryContainer = CyberTeal
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryTeal,
    secondary = LightSecondary,
    background = LightBg,
    surface = LightSurface,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = LightPrimaryTeal.copy(alpha = 0.1f),
    onPrimaryContainer = LightPrimaryTeal
)

@Composable
fun BypassVPNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        // Add typography here if needed
        content = content
    )
}
