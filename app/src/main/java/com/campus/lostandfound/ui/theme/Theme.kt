package com.campus.lostandfound.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
