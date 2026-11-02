package ro.punguta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1BE),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2C1800),
    tertiary = Color(0xFF3A6472),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7FBF3),
    surface = Color(0xFFF7FBF3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D5A3),
    onPrimary = Color(0xFF003918),
    primaryContainer = Color(0xFF145229),
    onPrimaryContainer = Color(0xFFB4F1BE),
    secondary = Color(0xFFFFB94C),
    onSecondary = Color(0xFF482A00),
    secondaryContainer = Color(0xFF684300),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFA2CDDD),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF101510),
    surface = Color(0xFF101510),
)

/** Semantic colors for money that are not part of the Material scheme. */
object MoneyColors {
    val income = Color(0xFF2E7D32)
    val incomeDark = Color(0xFF81C784)
    val warning = Color(0xFFE6A100)
}

@Composable
fun PungutaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
