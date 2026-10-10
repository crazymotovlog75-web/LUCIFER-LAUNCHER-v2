package net.kdt.pojavlaunch.lucifer.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ── LUCIFER visual identity ─────────────────────────────────────────────── */
val LucBackground = Color(0xFF0A0F14)   // deep space dark blue/black
val LucSurface = Color(0xFF0D141B)      // raised surface
val LucPanel = Color(0x99101A24)        // translucent glass panel fill
val LucBorder = Color(0xFF1F2937)       // subtle 1dp borders
val LucAccent = Color(0xFF00E5FF)       // neon cyan / teal
val LucAccentDim = Color(0x3300E5FF)    // cyan glow / active tint
val LucOnAccent = Color(0xFF04252B)     // dark text on cyan
val LucTextPrimary = Color(0xFFFFFFFF)  // headers
val LucTextMuted = Color(0xFFB0BEC5)    // subtitles
val LucSuccess = Color(0xFF22C55E)      // online dots

val LucShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

private val LucTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 5.sp,
        fontSize = 22.sp,
        color = LucTextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = LucTextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = LucTextPrimary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        color = LucTextMuted
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        color = LucTextMuted
    )
)

@Composable
fun LuciferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LucAccent,
            onPrimary = LucOnAccent,
            primaryContainer = LucAccentDim,
            secondary = LucAccent,
            background = LucBackground,
            onBackground = LucTextPrimary,
            surface = LucSurface,
            onSurface = LucTextPrimary,
            onSurfaceVariant = LucTextMuted,
            surfaceVariant = LucPanel,
            outline = LucBorder,
            error = Color(0xFFFF5252)
        ),
        typography = LucTypography,
        shapes = LucShapes,
        content = content
    )
}
