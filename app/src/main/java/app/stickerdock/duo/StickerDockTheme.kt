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
    primary = Color(0xFF12130F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9FF6A),
    onPrimaryContainer = Color(0xFF12130F),
    secondary = Color(0xFF5C621F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F6C8),
    onSecondaryContainer = Color(0xFF252900),
    tertiary = Color(0xFFB7472F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD2),
    onTertiaryContainer = Color(0xFF3D0900),
    background = Color(0xFFF5F3EC),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFFFFEF9),
    surfaceContainerHigh = Color(0xFFE9E6DD),
    surfaceContainer = Color(0xFFF0EEE6),
    surfaceContainerLow = Color(0xFFF8F6EF),
    surfaceContainerHighest = Color(0xFFDFDCD2),
    onSurface = Color(0xFF1B1C17),
    surfaceVariant = Color(0xFFE9E6DD),
    onSurfaceVariant = Color(0xFF5F6158),
    outlineVariant = Color(0xFFD5D3C9),
    outline = Color(0xFF797B70),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val StickerDockDarkColors = darkColorScheme(
    primary = Color(0xFFD9FF6A),
    onPrimary = Color(0xFF202800),
    primaryContainer = Color(0xFF3B4B00),
    onPrimaryContainer = Color(0xFFE3FF91),
    secondary = Color(0xFFD0D79A),
    onSecondary = Color(0xFF2D3200),
    secondaryContainer = Color(0xFF444A14),
    onSecondaryContainer = Color(0xFFF0F6C8),
    tertiary = Color(0xFFFFB4A2),
    onTertiary = Color(0xFF631000),
    tertiaryContainer = Color(0xFF8C2B18),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF11120F),
    onBackground = Color(0xFFE5E3DB),
    surface = Color(0xFF181A15),
    surfaceContainerHigh = Color(0xFF2B2D26),
    surfaceContainer = Color(0xFF23251F),
    surfaceContainerLow = Color(0xFF1D1F19),
    surfaceContainerHighest = Color(0xFF34362E),
    onSurface = Color(0xFFE5E3DB),
    surfaceVariant = Color(0xFF46483F),
    onSurfaceVariant = Color(0xFFC8C6BD),
    outline = Color(0xFF929388),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun StickerDockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StickerDockDarkColors else StickerDockLightColors,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)),
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
