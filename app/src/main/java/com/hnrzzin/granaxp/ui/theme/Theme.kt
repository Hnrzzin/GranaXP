package com.hnrzzin.granaxp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GranaXPColors.Primary,
    onPrimary = GranaXPColors.OnPrimary,
    primaryContainer = GranaXPColors.PrimaryLight,
    onPrimaryContainer = GranaXPColors.Primary,
    secondary = GranaXPColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = GranaXPColors.SecondaryLight,
    onSecondaryContainer = GranaXPColors.SecondaryDark,
    tertiary = GranaXPColors.Info,
    onTertiary = Color.White,
    error = GranaXPColors.Error,
    onError = Color.White,
    errorContainer = GranaXPColors.ErrorLight,
    onErrorContainer = GranaXPColors.Error,
    background = GranaXPColors.Background,
    onBackground = GranaXPColors.OnBackground,
    surface = GranaXPColors.Surface,
    onSurface = GranaXPColors.OnSurface,
    surfaceVariant = GranaXPColors.SurfaceVariant,
    onSurfaceVariant = GranaXPColors.Gray600,
    outline = GranaXPColors.Gray400,
    outlineVariant = GranaXPColors.Gray300,
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = GranaXPColors.PrimaryLight,
    onPrimary = GranaXPColors.PrimaryDark,
    primaryContainer = GranaXPColors.Primary,
    onPrimaryContainer = GranaXPColors.PrimaryLight,
    secondary = GranaXPColors.SecondaryLight,
    onSecondary = GranaXPColors.SecondaryDark,
    secondaryContainer = GranaXPColors.Secondary,
    onSecondaryContainer = GranaXPColors.SecondaryLight,
    tertiary = Color(0xFF90CAF9),
    onTertiary = GranaXPColors.Info,
    error = Color(0xFFF2B8B5),
    onError = GranaXPColors.Error,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = GranaXPColors.Gray900,
    onBackground = GranaXPColors.Gray50,
    surface = GranaXPColors.Gray800,
    onSurface = GranaXPColors.Gray50,
    surfaceVariant = GranaXPColors.Gray700,
    onSurfaceVariant = GranaXPColors.Gray300,
    outline = GranaXPColors.Gray500,
    outlineVariant = GranaXPColors.Gray600,
    scrim = Color.Black
)

data class GranaXPColorScheme(
    val primary: Color = GranaXPColors.Primary,
    val onPrimary: Color = GranaXPColors.OnPrimary,
    val secondary: Color = GranaXPColors.Secondary,
    val onSecondary: Color = Color.White,
    val error: Color = GranaXPColors.Error,
    val onError: Color = Color.White,
    val background: Color = GranaXPColors.Background,
    val onBackground: Color = GranaXPColors.OnBackground,
    val surface: Color = GranaXPColors.Surface,
    val onSurface: Color = GranaXPColors.OnSurface,
    val success: Color = GranaXPColors.Success,
    val warning: Color = GranaXPColors.Warning,
    val info: Color = GranaXPColors.Info
)

val LocalGranaXPColors = staticCompositionLocalOf {
    GranaXPColorScheme()
}

@Composable
fun GranaXPTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val granaXPColors = GranaXPColorScheme(
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        error = colorScheme.error,
        onError = colorScheme.onError,
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        onSurface = colorScheme.onSurface
    )

    CompositionLocalProvider(LocalGranaXPColors provides granaXPColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GranaXPTypography,
            content = content
        )
    }
}

object GranaXPTheme {
    val colors: GranaXPColorScheme
        @Composable
        get() = LocalGranaXPColors.current
}
