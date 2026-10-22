package com.sokhan.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sokhan.chat.domain.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = SokhanColors.Indigo40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SokhanColors.Indigo90,
    onPrimaryContainer = SokhanColors.Indigo30,
    secondary = SokhanColors.TealBean,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = SokhanColors.TealSoft,
    onSecondaryContainer = SokhanColors.TealDark,
    tertiary = SokhanColors.Copper40,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = SokhanColors.Copper90,
    onTertiaryContainer = SokhanColors.Copper30,
    surface = SokhanColors.SurfaceLight,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = SokhanColors.SurfaceLight,
    surfaceContainer = SokhanColors.SurfaceContainerLight,
    surfaceContainerHigh = SokhanColors.SurfaceContainerHighLight,
    surfaceContainerHighest = SokhanColors.SurfaceContainerHighestLight,
    outlineVariant = SokhanColors.OutlineVariantLight,
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410E0B)
)

private val DarkScheme = darkColorScheme(
    primary = SokhanColors.Indigo80,
    onPrimary = SokhanColors.Indigo30,
    primaryContainer = SokhanColors.Indigo30,
    onPrimaryContainer = SokhanColors.Indigo90,
    secondary = SokhanColors.TealSoft,
    onSecondary = SokhanColors.TealDark,
    secondaryContainer = SokhanColors.TealDark,
    onSecondaryContainer = SokhanColors.TealSoft,
    tertiary = SokhanColors.Copper80,
    onTertiary = SokhanColors.Copper30,
    tertiaryContainer = SokhanColors.Copper30,
    onTertiaryContainer = SokhanColors.Copper90,
    surface = SokhanColors.SurfaceDark,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0C0C12),
    surfaceContainerLow = SokhanColors.SurfaceDark,
    surfaceContainer = SokhanColors.SurfaceContainerDark,
    surfaceContainerHigh = SokhanColors.SurfaceContainerHighDark,
    surfaceContainerHighest = SokhanColors.SurfaceContainerHighestDark,
    outlineVariant = SokhanColors.OutlineVariantDark,
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
)

@Composable
fun SokhanTheme(
    themeMode: ThemeMode,
    dynamicColors: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SokhanTypography,
        shapes = SokhanShapes,
        content = content
    )
}
