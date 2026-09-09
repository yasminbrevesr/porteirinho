package br.com.porteirinho.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF155E75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDF4),
    onPrimaryContainer = Color(0xFF063642),
    secondary = Color(0xFF3F6D62),
    error = Color(0xFFB42318),
    background = Color(0xFFF5F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EEF0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF74D0E8),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF0B5062),
    secondary = Color(0xFFA4D0C3),
    background = Color(0xFF0E171A),
    surface = Color(0xFF142126),
)

@Composable
fun PorteirinhoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
