package ro.punguta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.punguta.app.R

// Visual world: a soft grey ground, white rounded cards, near-black for the one primary action
// and for selection, and a single coral-orange accent that marks what matters right now.

private val Ink = Color(0xFF141414)
private val Ground = Color(0xFFEEEEEC)
private val Card = Color(0xFFFFFFFF)
private val Coral = Color(0xFFEF5B36)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E3),
    onPrimaryContainer = Ink,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE3DA),
    onSecondaryContainer = Color(0xFF5A1A08),
    tertiary = Color(0xFF1E8E5A),
    onTertiary = Color.White,
    background = Ground,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F1EF),
    onSurfaceVariant = Color(0xFF66665F),
    surfaceContainerLowest = Card,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFFF4F4F2),
    surfaceContainerHighest = Color(0xFFEAEAE7),
    outline = Color(0xFFCFCFCA),
    outlineVariant = Color(0xFFE4E4E0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4DE),
    onErrorContainer = Color(0xFF5C1306),
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
)

private val Dark = darkColorScheme(
    // In the dark the brightest element takes the primary role, as black does in the light.
    primary = Color(0xFFF4F4F2),
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A2A28),
    onPrimaryContainer = Color(0xFFF4F4F2),
    secondary = Color(0xFFFF6E4A),
    onSecondary = Color(0xFF2B0A02),
    secondaryContainer = Color(0xFF4A1C10),
    onSecondaryContainer = Color(0xFFFFD9CE),
    tertiary = Color(0xFF5BD39A),
    onTertiary = Color(0xFF00391F),
    background = Color(0xFF0E0E0D),
    onBackground = Color(0xFFF4F4F2),
    surface = Color(0xFF1B1B1A),
    onSurface = Color(0xFFF4F4F2),
    surfaceVariant = Color(0xFF242422),
    onSurfaceVariant = Color(0xFFA9A9A2),
    surfaceContainerLowest = Color(0xFF151514),
    surfaceContainerLow = Color(0xFF1B1B1A),
    surfaceContainer = Color(0xFF1B1B1A),
    surfaceContainerHigh = Color(0xFF242422),
    surfaceContainerHighest = Color(0xFF2E2E2C),
    outline = Color(0xFF4A4A46),
    outlineVariant = Color(0xFF2E2E2C),
    error = Color(0xFFFF5470),
    onError = Color(0xFF3B0A02),
    errorContainer = Color(0xFF5A1A0C),
    onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFF4F4F2),
    inverseOnSurface = Ink,
)

/** Money and state colors that have no Material role. */
@Immutable
data class MoneyPalette(val income: Color, val warning: Color, val accent: Color)

private val LightMoney = MoneyPalette(income = Color(0xFF1E8E5A), warning = Color(0xFFC98A00), accent = Coral)
private val DarkMoney = MoneyPalette(income = Color(0xFF5BD39A), warning = Color(0xFFF2B53A), accent = Color(0xFFFF6E4A))

val LocalMoneyPalette = staticCompositionLocalOf { LightMoney }

/** Geist, bundled as a variable font so every weight works offline. */
@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700), geist(800))

private val base = TextStyle(fontFamily = Geist)

private val AppTypography = Typography(
    displaySmall = base.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    headlineMedium = base.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineSmall = base.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** Money is set in tabular figures so columns of amounts line up. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PungutaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalMoneyPalette provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
