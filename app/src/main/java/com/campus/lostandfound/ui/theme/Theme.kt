package com.campus.lostandfound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val DarkColors = darkColorScheme(
    primary = FindOrange,
    onPrimary = FindInk,
    secondary = FindGreen,
    tertiary = FindPurple,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkMuted,
    outline = ColorTokens.darkOutline
)

private val LightColors = lightColorScheme(
    primary = FindOrange,
    onPrimary = FindPaper,
    primaryContainer = ColorTokens.orangeContainer,
    onPrimaryContainer = FindOrangeDark,
    secondary = FindGreen,
    secondaryContainer = ColorTokens.greenContainer,
    tertiary = FindPurple,
    tertiaryContainer = ColorTokens.purpleContainer,
    background = FindIvory,
    surface = FindPaper,
    surfaceVariant = ColorTokens.warmSurface,
    onBackground = FindInk,
    onSurface = FindInk,
    onSurfaceVariant = FindMuted,
    outline = FindLine
)

private object ColorTokens {
    val orangeContainer = androidx.compose.ui.graphics.Color(0xFFFFE4D2)
    val greenContainer = androidx.compose.ui.graphics.Color(0xFFDDF5E5)
    val purpleContainer = androidx.compose.ui.graphics.Color(0xFFEDE2FA)
    val warmSurface = androidx.compose.ui.graphics.Color(0xFFF7F0E7)
    val darkOutline = androidx.compose.ui.graphics.Color(0xFF3D4449)
}

@Composable
fun CampusLostAndFoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
