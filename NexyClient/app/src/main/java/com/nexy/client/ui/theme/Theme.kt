/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeStyle {
    Pink, Blue, Green, Purple, Orange, Teal
}

// --- PINK PALETTE ---
private val PrimaryPink = Color(0xFFFFB7D5)
private val OnPrimary = Color(0xFF560027)
private val PrimaryContainerPink = Color(0xFF5C283D)
private val OnPrimaryContainerPink = Color(0xFFFFD9E2)
private val SecondaryPink = Color(0xFFFF80AB)
private val OnSecondary = Color(0xFF4F0026)
private val SecondaryContainerPink = Color(0xFF8C0046)
private val OnSecondaryContainerPink = Color(0xFFFFD9E2)

// --- BLUE PALETTE ---
private val PrimaryBlue = Color(0xFFB3C5FF)
private val OnPrimaryBlue = Color(0xFF002A78)
private val PrimaryContainerBlue = Color(0xFF233D73)
private val OnPrimaryContainerBlue = Color(0xFFDAE2FF)
private val SecondaryBlue = Color(0xFF5387FF)
private val OnSecondaryBlue = Color(0xFF002468)
private val SecondaryContainerBlue = Color(0xFF003898)
private val OnSecondaryContainerBlue = Color(0xFFDAE2FF)

// --- GREEN PALETTE ---
private val PrimaryGreen = Color(0xFFB7F397)
private val OnPrimaryGreen = Color(0xFF215100)
private val PrimaryContainerGreen = Color(0xFF2F4D20)
private val OnPrimaryContainerGreen = Color(0xFFD2FFBC)
private val SecondaryGreen = Color(0xFF82DB66)
private val OnSecondaryGreen = Color(0xFF123800)
private val SecondaryContainerGreen = Color(0xFF1E4E06)
private val OnSecondaryContainerGreen = Color(0xFFD2FFBC)

// --- PURPLE PALETTE ---
private val PrimaryPurple = Color(0xFFD0BCFF)
private val OnPrimaryPurple = Color(0xFF381E72)
private val PrimaryContainerPurple = Color(0xFF4F378B)
private val OnPrimaryContainerPurple = Color(0xFFEADDFF)
private val SecondaryPurple = Color(0xFFCCC2DC)
private val OnSecondaryPurple = Color(0xFF332D41)
private val SecondaryContainerPurple = Color(0xFF4A4458)
private val OnSecondaryContainerPurple = Color(0xFFE8DEF8)

// --- ORANGE PALETTE ---
private val PrimaryOrange = Color(0xFFFFB784)
private val OnPrimaryOrange = Color(0xFF4F2500)
private val PrimaryContainerOrange = Color(0xFF713700)
private val OnPrimaryContainerOrange = Color(0xFFFFDCC6)
private val SecondaryOrange = Color(0xFFFFB68F)
private val OnSecondaryOrange = Color(0xFF542100)
private val SecondaryContainerOrange = Color(0xFF783200)
private val OnSecondaryContainerOrange = Color(0xFFFFDBC9)

// --- TEAL PALETTE ---
private val PrimaryTeal = Color(0xFF80D5D4)
private val OnPrimaryTeal = Color(0xFF003737)
private val PrimaryContainerTeal = Color(0xFF004F4F)
private val OnPrimaryContainerTeal = Color(0xFF9CF1F0)
private val SecondaryTeal = Color(0xFFB0CCCB)
private val OnSecondaryTeal = Color(0xFF1B3534)
private val SecondaryContainerTeal = Color(0xFF324B4B)
private val OnSecondaryContainerTeal = Color(0xFFA6E8E7)


// Common Dark Colors
private val BackgroundDark = Color(0xFF000000) // Pure Black
private val OnBackgroundDark = Color(0xFFEAEAEA)
private val SurfaceDark = Color(0xFF121212) 
private val OnSurfaceDark = Color(0xFFEAEAEA)
private val SurfaceVariantDark = Color(0xFF1C1C1C)
private val OnSurfaceVariantDark = Color(0xFFD4C4C9)
private val ErrorColor = Color(0xFFFFB4AB)

// Common Light Colors
private val BackgroundLight = Color(0xFFFFFBFF)
private val SurfaceLight = Color(0xFFFFFBFF)

// --- COLOR SCHEMES ---

private fun getDarkColorScheme(style: ThemeStyle) = when(style) {
    ThemeStyle.Pink -> darkColorScheme(
        primary = PrimaryPink,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainerPink,
        onPrimaryContainer = OnPrimaryContainerPink,
        secondary = SecondaryPink,
        onSecondary = OnSecondary,
        secondaryContainer = SecondaryContainerPink,
        onSecondaryContainer = OnSecondaryContainerPink,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
    ThemeStyle.Blue -> darkColorScheme(
        primary = PrimaryBlue,
        onPrimary = OnPrimaryBlue,
        primaryContainer = PrimaryContainerBlue,
        onPrimaryContainer = OnPrimaryContainerBlue,
        secondary = SecondaryBlue,
        onSecondary = OnSecondaryBlue,
        secondaryContainer = SecondaryContainerBlue,
        onSecondaryContainer = OnSecondaryContainerBlue,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
    ThemeStyle.Green -> darkColorScheme(
        primary = PrimaryGreen,
        onPrimary = OnPrimaryGreen,
        primaryContainer = PrimaryContainerGreen,
        onPrimaryContainer = OnPrimaryContainerGreen,
        secondary = SecondaryGreen,
        onSecondary = OnSecondaryGreen,
        secondaryContainer = SecondaryContainerGreen,
        onSecondaryContainer = OnSecondaryContainerGreen,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
    ThemeStyle.Purple -> darkColorScheme(
        primary = PrimaryPurple,
        onPrimary = OnPrimaryPurple,
        primaryContainer = PrimaryContainerPurple,
        onPrimaryContainer = OnPrimaryContainerPurple,
        secondary = SecondaryPurple,
        onSecondary = OnSecondaryPurple,
        secondaryContainer = SecondaryContainerPurple,
        onSecondaryContainer = OnSecondaryContainerPurple,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
    ThemeStyle.Orange -> darkColorScheme(
        primary = PrimaryOrange,
        onPrimary = OnPrimaryOrange,
        primaryContainer = PrimaryContainerOrange,
        onPrimaryContainer = OnPrimaryContainerOrange,
        secondary = SecondaryOrange,
        onSecondary = OnSecondaryOrange,
        secondaryContainer = SecondaryContainerOrange,
        onSecondaryContainer = OnSecondaryContainerOrange,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
    ThemeStyle.Teal -> darkColorScheme(
        primary = PrimaryTeal,
        onPrimary = OnPrimaryTeal,
        primaryContainer = PrimaryContainerTeal,
        onPrimaryContainer = OnPrimaryContainerTeal,
        secondary = SecondaryTeal,
        onSecondary = OnSecondaryTeal,
        secondaryContainer = SecondaryContainerTeal,
        onSecondaryContainer = OnSecondaryContainerTeal,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = ErrorColor
    )
}

private fun getLightColorScheme(style: ThemeStyle) = when(style) {
    ThemeStyle.Pink -> lightColorScheme(
        primary = Color(0xFFB9005B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001D),
        secondary = Color(0xFF9C4073),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
    ThemeStyle.Blue -> lightColorScheme(
        primary = Color(0xFF005AC1),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF575E71),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
    ThemeStyle.Green -> lightColorScheme(
        primary = Color(0xFF386A20),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F397),
        onPrimaryContainer = Color(0xFF042100),
        secondary = Color(0xFF55624C),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
    ThemeStyle.Purple -> lightColorScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
    ThemeStyle.Orange -> lightColorScheme(
        primary = Color(0xFF984800),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDCC6),
        onPrimaryContainer = Color(0xFF301400),
        secondary = Color(0xFF775748),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
    ThemeStyle.Teal -> lightColorScheme(
        primary = Color(0xFF006A6A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF1F0),
        onPrimaryContainer = Color(0xFF002020),
        secondary = Color(0xFF4A6363),
        onSecondary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
    )
}

@Composable
fun NexyClientTheme(
    darkTheme: Boolean = true,
    themeStyle: ThemeStyle = ThemeStyle.Pink,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) getDarkColorScheme(style = themeStyle) else getLightColorScheme(style = themeStyle)
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
