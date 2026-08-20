package cz.teply.sheetset.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MonochromeColors = lightColorScheme(
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
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF444444),
    surfaceTint = Color(0xFF111111),
    inverseSurface = Color(0xFF111111),
    inverseOnSurface = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFFF3F3F3),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE7E7E7),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFD0D0D0),
    error = Color(0xFF111111),
    onError = Color.White,
    errorContainer = Color(0xFFE7E7E7),
    onErrorContainer = Color(0xFF111111),
    scrim = Color.Black,
)

private val MonochromeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun SheetSetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonochromeColors,
        typography = Typography(),
        shapes = MonochromeShapes,
        content = content,
    )
}
