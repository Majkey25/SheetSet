package cz.teply.sheetset.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.teply.sheetset.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7E7),
    onPrimaryContainer = Color(0xFF111111),
    inversePrimary = Color(0xFFDDDDDD),
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7E7),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E7E7),
    onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFF7F7F4),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF444444),
    surfaceTint = Color(0xFF111111),
    inverseSurface = Color(0xFF111111),
    inverseOnSurface = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFFF7F7F4),
    surfaceContainerHigh = Color(0xFFF0F0EC),
    surfaceContainerHighest = Color(0xFFE8E8E3),
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFD0D0D0),
    error = Color(0xFF111111),
    onError = Color.White,
    errorContainer = Color(0xFFE7E7E7),
    onErrorContainer = Color(0xFF111111),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF383838),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD8D8D8),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF353535),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF101010),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFD2D2D2),
    surfaceTint = Color(0xFFF2F2F2),
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color(0xFF181818),
    surfaceBright = Color(0xFF363636),
    surfaceDim = Color(0xFF101010),
    surfaceContainer = Color(0xFF1D1D1D),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    outline = Color(0xFFA0A0A0),
    outlineVariant = Color(0xFF484848),
    error = Color(0xFFF2F2F2),
    onError = Color(0xFF111111),
    errorContainer = Color(0xFF383838),
    onErrorContainer = Color.White,
    scrim = Color.Black,
)

private val MonochromeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

private val SheetTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun SheetSetTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode == ThemeMode.DARK) DarkColors else LightColors,
        typography = SheetTypography,
        shapes = MonochromeShapes,
        content = content,
    )
}
