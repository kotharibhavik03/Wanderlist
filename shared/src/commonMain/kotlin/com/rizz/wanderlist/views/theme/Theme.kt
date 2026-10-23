
package com.rizz.wanderlist.views.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WanderlistLightColors = lightColorScheme(
    primary = Color(0xFF2864A5),
    onPrimary = Color.White,
    secondary = Color(0xFF537DA8),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF10243A),
    surface = Color.White,
    onSurface = Color(0xFF10243A),
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = Color(0xFF526477),
    outline = Color(0xFFD5DDE6)
)

private val WanderlistDarkColors = darkColorScheme(
    primary = Color(0xFF8DBBEE),
    onPrimary = Color(0xFF10243A),
    secondary = Color(0xFFA8C9EA),
    background = Color(0xFF0B1725),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF142438),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF20354A),
    onSurfaceVariant = Color(0xFFB7C5D5),
    outline = Color(0xFF40566D)
)

@Composable
fun WanderlistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        WanderlistDarkColors
    } else {
        WanderlistLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
