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
    onPrimary = SokhanColors.Indigo90,
    primaryContainer = SokhanColors.Indigo90,
    onPrimaryContainer = SokhanColors.Indigo30,
    secondary = SokhanColors.TealBean,
    secondaryContainer = SokhanColors.TealSoft,
    onSecondaryContainer = SokhanColors.TealBean,
    tertiary = SokhanColors.Pinkish,
    tertiaryContainer = SokhanColors.PinkishSoft,
    surface = SokhanColors.SurfaceLight,
    surfaceContainer = SokhanColors.SurfaceContainerLight
)

private val DarkScheme = darkColorScheme(
    primary = SokhanColors.Indigo80,
    onPrimary = SokhanColors.Indigo30,
    primaryContainer = SokhanColors.Indigo30,
    onPrimaryContainer = SokhanColors.Indigo90,
    secondary = SokhanColors.TealSoft,
    secondaryContainer = SokhanColors.TealBean,
    onSecondaryContainer = SokhanColors.TealSoft,
    tertiary = SokhanColors.PinkishSoft,
    surface = SokhanColors.SurfaceDark,
    surfaceContainer = SokhanColors.SurfaceContainerDark
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
