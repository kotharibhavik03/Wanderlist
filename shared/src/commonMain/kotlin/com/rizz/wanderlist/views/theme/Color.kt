package com.rizz.wanderlist.views.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Every Material 3 colour role is set. Any role left out falls back to Material's
// default purple palette, which shows up as a tint on cards, chips and sheets.

internal val WanderlistLightColors = lightColorScheme(
    primary = Color(0xFF2864A5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    inversePrimary = Color(0xFFA5C8FF),
    secondary = Color(0xFF537DA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0F2A44),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2F0E6),
    onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF10243A),
    surface = Color.White,
    onSurface = Color(0xFF10243A),
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = Color(0xFF526477),
    surfaceTint = Color(0xFF2864A5),
    inverseSurface = Color(0xFF263A50),
    inverseOnSurface = Color(0xFFEAF1F8),
    // Text-field borders: at least 3:1 against the surface, as WCAG asks for form controls.
    outline = Color(0xFF73879B),
    // Dividers and card borders, which are decorative and can be subtle.
    outlineVariant = Color(0xFFD5DDE6),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD9E0E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5F9),
    surfaceContainer = Color(0xFFECF0F5),
    surfaceContainerHigh = Color(0xFFE6EBF1),
    surfaceContainerHighest = Color(0xFFE0E6ED)
)

internal val WanderlistDarkColors = darkColorScheme(
    primary = Color(0xFF8DBBEE),
    onPrimary = Color(0xFF10243A),
    primaryContainer = Color(0xFF1F4B7D),
    onPrimaryContainer = Color(0xFFD4E3FF),
    inversePrimary = Color(0xFF2864A5),
    secondary = Color(0xFFA8C9EA),
    onSecondary = Color(0xFF0F2A44),
    secondaryContainer = Color(0xFF2B4766),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFF80D5C8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFB2F0E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1725),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF142438),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF20354A),
    onSurfaceVariant = Color(0xFFB7C5D5),
    surfaceTint = Color(0xFF8DBBEE),
    inverseSurface = Color(0xFFE8EEF5),
    inverseOnSurface = Color(0xFF1B2D42),
    outline = Color(0xFF8396AA),
    outlineVariant = Color(0xFF40566D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3D53),
    surfaceDim = Color(0xFF0B1725),
    surfaceContainerLowest = Color(0xFF07111C),
    surfaceContainerLow = Color(0xFF0F1E2F),
    surfaceContainer = Color(0xFF142438),
    surfaceContainerHigh = Color(0xFF1B2D42),
    surfaceContainerHighest = Color(0xFF22364C)
)

/** Colours with app meaning that Material has no role for: a place's Visited / Wishlist status. */
@Immutable
data class StatusColors(
    val visitedContainer: Color,
    val onVisitedContainer: Color,
    val wishlistContainer: Color,
    val onWishlistContainer: Color
)

internal val LightStatusColors = StatusColors(
    visitedContainer = Color(0xFFD7F0DA),
    onVisitedContainer = Color(0xFF0F3D16),
    wishlistContainer = Color(0xFFFFE9C2),
    onWishlistContainer = Color(0xFF4A3000)
)

internal val DarkStatusColors = StatusColors(
    visitedContainer = Color(0xFF1E4D27),
    onVisitedContainer = Color(0xFFBDEBC3),
    wishlistContainer = Color(0xFF5A4100),
    onWishlistContainer = Color(0xFFFFDEA3)
)
