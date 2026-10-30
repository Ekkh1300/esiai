package com.sokhan.chat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ESI Agent palette — "ink & signal".
 *
 * A deep indigo carries the user's own voice, a cool teal marks the assistant
 * (the accent rail on assistant cards), and a warm copper is reserved for
 * warnings and destructive accents. Surfaces are cool-neutral greys with a
 * touch of violet so the app does not feel like a spreadsheet.
 */
object SokhanColors {
    // Brand — indigo
    val Indigo80 = Color(0xFFC3C4FF)
    val Indigo90 = Color(0xFFE3E3FF)
    val Indigo30 = Color(0xFF23249B)
    val Indigo40 = Color(0xFF4B4FD6)

    // Assistant signal — teal
    val TealBean = Color(0xFF00696E)
    val TealSoft = Color(0xFFA6F0F5)
    val TealDark = Color(0xFF00494D)

    // Warm accent — copper
    val Copper40 = Color(0xFF8C5A2E)
    val Copper80 = Color(0xFFFFB77C)
    val Copper90 = Color(0xFFFFDDB8)
    val Copper30 = Color(0xFF5E3A17)

    // Surfaces — cool neutral, slightly violet
    val SurfaceLight = Color(0xFFFBFAFD)
    val SurfaceContainerLight = Color(0xFFF2F1F8)
    val SurfaceContainerHighLight = Color(0xFFEBEAF3)
    val SurfaceContainerHighestLight = Color(0xFFE3E2EE)
    val OutlineVariantLight = Color(0xFFCBC9D8)

    val SurfaceDark = Color(0xFF111118)
    val SurfaceContainerDark = Color(0xFF1B1A24)
    val SurfaceContainerHighDark = Color(0xFF24232F)
    val SurfaceContainerHighestDark = Color(0xFF2E2D3B)
    val OutlineVariantDark = Color(0xFF464553)
}
