package pe.edu.unmsm.siderurgica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF174C61), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EAF0), onPrimaryContainer = Color(0xFF103544),
    secondary = Color(0xFF795400), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2A3), onSecondaryContainer = Color(0xFF392800),
    background = Color(0xFFF5F7F9), onBackground = Color(0xFF172B36),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF172B36),
    surfaceVariant = Color(0xFFE7EDF0), onSurfaceVariant = Color(0xFF465C68),
    outline = Color(0xFF718590)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9CCDDD), onPrimary = Color(0xFF073544),
    primaryContainer = Color(0xFF174C61), onPrimaryContainer = Color(0xFFD6EAF0),
    secondary = Color(0xFFFFD077), onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF5C4100), onSecondaryContainer = Color(0xFFFFE2A3),
    background = Color(0xFF0F1C24), onBackground = Color(0xFFE1EAF0),
    surface = Color(0xFF162630), onSurface = Color(0xFFE1EAF0),
    surfaceVariant = Color(0xFF263C47), onSurfaceVariant = Color(0xFFB7C9D2)
)
@Composable fun SteelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
