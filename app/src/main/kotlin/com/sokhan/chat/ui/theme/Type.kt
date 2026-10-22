package com.sokhan.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokhan.chat.R

/**
 * One family for both scripts.
 *
 * Vazirmatn (SIL OFL) is drawn for Persian first and carries a clean Latin
 * with it, so switching the app language never changes the texture of the
 * text. Persian needs more line height than Latin because of the dots and
 * the marks below the baseline, so the scale is tuned accordingly.
 */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

/** Trim the extra leading Compose adds, but keep Persian marks uncropped. */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = VazirmatnFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = TightLineHeight
)

val SokhanTypography = Typography().copy(
    displaySmall = style(30, 38, FontWeight.Bold, (-0.2)),
    headlineSmall = style(24, 32, FontWeight.Bold, (-0.1)),
    titleLarge = style(20, 28, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 26),
    bodyMedium = style(14, 23),
    bodySmall = style(13, 20),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 17, FontWeight.Medium, 0.3),
    labelSmall = style(11, 16, FontWeight.Normal, 0.4)
)

/**
 * Radii: bubbles are soft but not pill-shaped, cards use a single family so
 * the screen does not look like a collection of unrelated stickers.
 */
val SokhanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
