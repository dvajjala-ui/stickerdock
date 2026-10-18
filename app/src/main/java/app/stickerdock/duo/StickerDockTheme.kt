package app.stickerdock.duo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StickerDockLightColors = lightColorScheme(
    primary = Color(0xFF182820),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F3EC),
    onPrimaryContainer = Color(0xFF143D2B),
    secondary = Color(0xFF5D6676),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBF4C8),
    onSecondaryContainer = Color(0xFF28321B),
    tertiary = Color(0xFF9B4050),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DE),
    onTertiaryContainer = Color(0xFF40000D),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF18211C),
    surface = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBEEEC),
    surfaceContainer = Color(0xFFF0F3F1),
    surfaceContainerLow = Color(0xFFF5F7F5),
    surfaceContainerHighest = Color(0xFFE1E7E3),
    onSurface = Color(0xFF20251E),
    surfaceVariant = Color(0xFFEFF1EB),
    onSurfaceVariant = Color(0xFF60665C),
    outlineVariant = Color(0xFFDFE4D9),
    outline = Color(0xFF7A8291),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val StickerDockDarkColors = darkColorScheme(
    primary = Color(0xFFB0DEC1),
    onPrimary = Color(0xFF132A1D),
    primaryContainer = Color(0xFF243D2E),
    onPrimaryContainer = Color(0xFFDEF5E5),
    secondary = Color(0xFFB9C3D4),
    onSecondary = Color(0xFF253040),
    secondaryContainer = Color(0xFF303947),
    onSecondaryContainer = Color(0xFFE9EDF4),
    tertiary = Color(0xFFFFB1BC),
    onTertiary = Color(0xFF5F1122),
    tertiaryContainer = Color(0xFF7D2938),
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE5E8EF),
    surface = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF252A34),
    surfaceContainer = Color(0xFF20242D),
    surfaceContainerLow = Color(0xFF1A1E26),
    surfaceContainerHighest = Color(0xFF303641),
    onSurface = Color(0xFFE5E8EF),
    surfaceVariant = Color(0xFF505969),
    onSurfaceVariant = Color(0xFFC3CAD6),
    outline = Color(0xFF8D95A4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun StickerDockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StickerDockDarkColors else StickerDockLightColors,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-1).sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-.5).sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-.5).sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-.2).sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
        ),
        content = content
    )
}
