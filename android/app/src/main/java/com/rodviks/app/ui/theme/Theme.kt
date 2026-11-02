package com.rodviks.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BluePrimary = Color(0xFF4C8DFF)
val BlueDeep = Color(0xFF2E6BE6)
val BlueContainerDark = Color(0xFF1E3A6E)
val BlueContainerLight = Color(0xFFDBE7FF)

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color(0xFF002B6B),
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFF9FCAFF),
    onSecondary = Color(0xFF00315E),
    secondaryContainer = Color(0xFF22486F),
    onSecondaryContainer = Color(0xFFD5E5FF),
    tertiary = Color(0xFF6FC8A0),
    onTertiary = Color(0xFF003926),
    background = Color(0xFF0E1319),
    onBackground = Color(0xFFE1E5EB),
    surface = Color(0xFF0E1319),
    onSurface = Color(0xFFE1E5EB),
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = Color(0xFFB5BFCC),
    surfaceContainer = Color(0xFF151C24),
    surfaceContainerHigh = Color(0xFF1C2530),
    surfaceContainerHighest = Color(0xFF26303C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3C4754),
    outlineVariant = Color(0xFF2A3440)
)

private val LightColors = lightColorScheme(
    primary = BlueDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = Color(0xFF00215C),
    secondary = Color(0xFF2E6BE6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3FF),
    onSecondaryContainer = Color(0xFF00215C),
    tertiary = Color(0xFF1F6B4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA4F2C8),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E6EF),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainer = Color(0xFFEDF0F5),
    surfaceContainerHigh = Color(0xFFE0E6EF),
    surfaceContainerHighest = Color(0xFFD2D9E3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

@Composable
fun RodviksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RodviksTypography,
        content = content
    )
}
