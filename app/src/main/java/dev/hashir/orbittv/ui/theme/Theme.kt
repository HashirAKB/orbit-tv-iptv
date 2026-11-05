package dev.hashir.orbittv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A near-black cinema surface with a single hot accent. Deliberately flat and
// dark so channel artwork is the only thing with colour on screen.
val Ink = Color(0xFF07070B)
val Surface1 = Color(0xFF101018)
val Surface2 = Color(0xFF1A1A25)
val Accent = Color(0xFFE11D48)
val AccentSoft = Color(0xFFFB7185)
val TextPrimary = Color(0xFFF4F4F7)
val TextSecondary = Color(0xFF9A9AAB)
val TextTertiary = Color(0xFF63637A)
val Online = Color(0xFF34D399)

private val OrbitColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Accent,
    onPrimaryContainer = Color.White,
    secondary = AccentSoft,
    onSecondary = Color.Black,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2C2C3A),
    error = Color(0xFFF87171),
)

private val OrbitTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    // Dark-only by design: channel artwork is the only colour on screen.
    MaterialTheme(
        colorScheme = OrbitColors,
        typography = OrbitTypography,
        content = content,
    )
}
