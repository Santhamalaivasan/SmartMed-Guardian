package com.smartmedguardian.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalThemeIsDark = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = MedicalGreen,
    secondary = Indigo,
    tertiary = SuccessLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextDarkLight,
    onSurface = TextDarkLight,
    onSurfaceVariant = TextLightLight,
    error = ErrorLight
)

private val DarkColorScheme = darkColorScheme(
    primary = MedicalGreenDark,        // Soothing vibrant emerald green (0xFF34D399)
    onPrimary = Color(0xFF022C22),      // Dark green text for maximum contrast on primary emerald green button
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = IndigoDark,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = SuccessDark,
    onTertiary = Color(0xFF022C22),
    background = BackgroundDark,       // Deep charcoal / dark blue-gray (0xFF0F172A)
    onBackground = TextDarkOnDark,     // High-contrast near-white (0xFFF8FAFC)
    surface = SurfaceDark,             // Lighter dark slate card background (0xFF1E293B)
    onSurface = TextDarkOnDark,        // High-contrast near-white (0xFFF8FAFC)
    surfaceVariant = Color(0xFF283548),
    onSurfaceVariant = TextLightOnDark, // Soft slate gray (0xFFCBD5E1)
    error = ErrorDark,                 // Soft legible red (0xFFF87171)
    onError = Color(0xFF450A0A)
)

@Composable
fun SmartMedGuardianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
