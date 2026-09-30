package com.example.nianchulai

import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

internal enum class DisplayMode(val label: String) { SYSTEM("系統"), LIGHT("淺色"), DARK("深色") }
internal val themePresets = listOf(
    "活力珊瑚" to 4f, "陽光橘" to 42f, "薄荷綠" to 155f,
    "晴空藍" to 190f, "葡萄紫" to 285f, "莓果粉" to 335f
)

internal class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var mode by mutableStateOf(runCatching {
        DisplayMode.valueOf(prefs.getString("mode", DisplayMode.SYSTEM.name).orEmpty())
    }.getOrDefault(DisplayMode.SYSTEM))
        private set
    var hue by mutableFloatStateOf(prefs.getFloat("hue", 190f).coerceIn(0f, 360f))
        private set

    fun selectMode(value: DisplayMode) {
        mode = value
        prefs.edit().putString("mode", value.name).apply()
    }

    fun selectHue(value: Float) {
        hue = value.coerceIn(0f, 360f)
        prefs.edit().putFloat("hue", hue).apply()
    }
}

internal fun hueColor(hue: Float): Color = Color.hsv(hue, 0.75f, 0.95f)

@Composable
internal fun AppTheme(settings: ThemeSettings, activity: MainActivity, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        DisplayMode.SYSTEM -> isSystemInDarkTheme()
        DisplayMode.LIGHT -> false
        DisplayMode.DARK -> true
    }
    // Preserve each hue's brightest tone that still gives white labels >= 4.5:1 contrast.
    // Dark themes use saturated bright accents instead of washing different hues into pastels.
    val primary = if (dark) Color.hsv(settings.hue, 0.58f, 0.95f) else
        (95 downTo 42).asSequence().map { Color.hsv(settings.hue, 0.85f, it / 100f) }
            .first { it.luminance() <= 0.18f }
    val container = Color.hsv(settings.hue, if (dark) 0.65f else 0.2f, if (dark) 0.28f else 0.98f)
    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = Color.Black,
        primaryContainer = container, onPrimaryContainer = Color.White,
        secondary = primary, secondaryContainer = container, onSecondaryContainer = Color.White
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = Color.Black,
        secondary = primary, secondaryContainer = container, onSecondaryContainer = Color.Black
    )
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(scheme.background.toArgb())
                else SystemBarStyle.light(scheme.background.toArgb(), scheme.background.toArgb()),
            navigationBarStyle = if (dark) SystemBarStyle.dark(scheme.surface.toArgb())
                else SystemBarStyle.light(scheme.surface.toArgb(), scheme.surface.toArgb())
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
