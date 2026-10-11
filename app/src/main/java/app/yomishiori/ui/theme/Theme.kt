package app.yomishiori.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.container
import app.yomishiori.data.prefs.AppSettings
import app.yomishiori.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A5598),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDCBC),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF1A2668),
    primaryContainer = Color(0xFF323D7F),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF444659),
    tertiary = Color(0xFFFFB86C),
    tertiaryContainer = Color(0xFF693C00),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
)

val supportsDynamicColor get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun YomishioriTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings by context.container.preferences.settings.collectAsStateWithLifecycle(AppSettings())
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        settings.dynamicColor && supportsDynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }?.run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
