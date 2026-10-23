package com.smartmedguardian.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light Theme Colors
val BackgroundLight = Color(0xFFF2F4F8)
val SurfaceLight = Color(0xFFFFFFFF)
val LightShadowLight = Color(0xFFFFFFFF)
val DarkShadowLight = Color(0xFFD1D9E6)

// Dark Theme Colors (Deep Charcoal / Dark Blue-Gray - Medical Slate)
val BackgroundDark = Color(0xFF0F172A) // Deep charcoal slate blue-gray, NOT pure black (Slate 900)
val SurfaceDark = Color(0xFF1E293B)    // Clearly distinguishable lighter dark slate card background (Slate 800)
val LightShadowDark = Color(0xFF2C3C52) // Subtle top-left slate highlight glow
val DarkShadowDark = Color(0x80030712)  // Soft, subtle bottom-right shadow (avoiding heavy black blurs)

// Medical Accents
val MedicalGreen = Color(0xFF2E7D32)
val MedicalGreenDark = Color(0xFF34D399) // Soothing, vibrant emerald green for high-contrast dark mode
val Indigo = Color(0xFF5C6BC0)
val IndigoDark = Color(0xFF818CF8)

val SuccessLight = Color(0xFF2ECC71)
val SuccessDark = Color(0xFF34D399)

val WarningLight = Color(0xFFF59E0B)
val WarningDark = Color(0xFFFBBF24)

val ErrorLight = Color(0xFFEF4444)
val ErrorDark = Color(0xFFF87171)

// Static Text Base Colors
val TextDarkLight = Color(0xFF1E293B)
val TextLightLight = Color(0xFF64748B)
val TextDarkOnDark = Color(0xFFF8FAFC) // High-contrast near-white (98% brightness)
val TextLightOnDark = Color(0xFFCBD5E1) // Soft slate gray for secondary text (82% brightness)
val TextMutedOnDark = Color(0xFF64748B) // Muted gray for disabled items

// Dynamic Theme Colors (Automatically adapt to Light / Dark Mode)
val Background: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) BackgroundDark else BackgroundLight

val Surface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) SurfaceDark else SurfaceLight

val LightShadow: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) LightShadowDark else LightShadowLight

val DarkShadow: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) DarkShadowDark else DarkShadowLight

val MedicalAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) MedicalGreenDark else MedicalGreen

val TextDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) TextDarkOnDark else TextDarkLight

val TextLight: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) TextLightOnDark else TextLightLight

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) TextMutedOnDark else TextLightLight.copy(alpha = 0.6f)

val Success: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) SuccessDark else SuccessLight

val Warning: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) WarningDark else WarningLight

val Error: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) ErrorDark else ErrorLight

