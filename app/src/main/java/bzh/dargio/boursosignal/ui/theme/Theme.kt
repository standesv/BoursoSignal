package bzh.dargio.boursosignal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette : vert "finance" sobre, décliné clair / sombre.
private val Green = Color(0xFF0B7A4B)
private val GreenDark = Color(0xFF4ADE80)
val BullGreen = Color(0xFF16A34A)
val BearRed = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF2E7D5B),
    background = Color(0xFFF7F8F7),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    secondary = Color(0xFF86EFAC),
    background = Color(0xFF0E1512),
    surface = Color(0xFF151D19),
)

@Composable
fun BoursoSignalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
