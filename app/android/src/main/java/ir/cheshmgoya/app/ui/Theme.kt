package ir.cheshmgoya.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Colours tuned for hospital use: bold, high contrast, a thick yellow highlight. */
@Immutable
data class AacColors(
    val background: Color,
    val onBackground: Color,
    val cell: Color,
    val onCell: Color,
    val outline: Color,
    val highlight: Color,
    val onHighlight: Color,
    val yes: Color,
    val no: Color,
    val emergency: Color,
    val nav: Color,
    val control: Color,
    val suggestion: Color,
    val warning: Color,
)

fun aacColors(dark: Boolean, highContrast: Boolean): AacColors = when {
    dark && highContrast -> AacColors(
        background = Color.Black, onBackground = Color.White, cell = Color(0xFF121212), onCell = Color.White,
        outline = Color.White, highlight = Color(0xFFFFD600), onHighlight = Color.Black,
        yes = Color(0xFF00701A), no = Color(0xFFB00020), emergency = Color(0xFFD50000), nav = Color(0xFF0D3C8C),
        control = Color(0xFF263238), suggestion = Color(0xFF4A148C), warning = Color(0xFFFF6D00),
    )
    dark -> AacColors(
        background = Color(0xFF101418), onBackground = Color(0xFFECEFF1), cell = Color(0xFF1F262E), onCell = Color(0xFFECEFF1),
        outline = Color(0xFF455A64), highlight = Color(0xFFFFD600), onHighlight = Color.Black,
        yes = Color(0xFF2E7D32), no = Color(0xFFC62828), emergency = Color(0xFFD50000), nav = Color(0xFF1565C0),
        control = Color(0xFF37474F), suggestion = Color(0xFF6A1B9A), warning = Color(0xFFEF6C00),
    )
    highContrast -> AacColors(
        background = Color.White, onBackground = Color.Black, cell = Color.White, onCell = Color.Black,
        outline = Color.Black, highlight = Color(0xFFFFD600), onHighlight = Color.Black,
        yes = Color(0xFF00701A), no = Color(0xFFB00020), emergency = Color(0xFFD50000), nav = Color(0xFF0D3C8C),
        control = Color(0xFF263238), suggestion = Color(0xFF4A148C), warning = Color(0xFFE65100),
    )
    else -> AacColors(
        background = Color(0xFFF4F6F8), onBackground = Color(0xFF14181C), cell = Color.White, onCell = Color(0xFF14181C),
        outline = Color(0xFFB0BEC5), highlight = Color(0xFFFFD600), onHighlight = Color.Black,
        yes = Color(0xFF2E7D32), no = Color(0xFFC62828), emergency = Color(0xFFD50000), nav = Color(0xFF1565C0),
        control = Color(0xFF455A64), suggestion = Color(0xFF6A1B9A), warning = Color(0xFFE65100),
    )
}

val LocalAacColors = staticCompositionLocalOf { aacColors(dark = true, highContrast = true) }
val LocalFontScale = staticCompositionLocalOf { 1f }

@Composable
fun CheshmGoyaTheme(dark: Boolean, highContrast: Boolean, fontScale: Float, content: @Composable () -> Unit) {
    val colors = aacColors(dark, highContrast)
    val scheme = if (dark) darkColorScheme(background = colors.background, surface = colors.background, primary = Color(0xFF90CAF9))
    else lightColorScheme(background = colors.background, surface = colors.background, primary = Color(0xFF1565C0))
    MaterialTheme(colorScheme = scheme) {
        // The whole interface is Persian: always right-to-left, whatever the system language.
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalAacColors provides colors,
            LocalFontScale provides fontScale,
            content = content,
        )
    }
}
