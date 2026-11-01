package com.rutina.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.rutina.app.app
import com.rutina.app.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.R

private val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xF3, 0xE9, 0xD8)
private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** Verde matcha para lo cumplido. */
val DoneGreen = Color(0xFF6F8A4E)

/** Rojo bermellón de los sellos hanko y del sol de los carteles. */
val Shu = Color(0xFFD7402B)

/** Gótica redondeada japonesa para todo el texto. */
val ZenMaru = FontFamily(
    Font(R.font.zen_maru_regular, FontWeight.Normal),
    Font(R.font.zen_maru_medium, FontWeight.Medium),
    Font(R.font.zen_maru_bold, FontWeight.Bold),
)

/** Letra gruesa de cartel japonés para los títulos. */
val Script = FontFamily(Font(R.font.dela_gothic))

/** Letra de pincel para los kanji de los sellos. */
val Fude = FontFamily(Font(R.font.yuji_syuku))

/** De día: papel crema, tinta negra y rojo bermellón, como un cartel japonés antiguo. */
private val LightColors = lightColorScheme(
    primary = Shu,
    onPrimary = Color(0xFFFBF3E4),
    primaryContainer = Color(0xFFF6D9CF),
    onPrimaryContainer = Color(0xFF5A170C),
    secondary = Color(0xFF2F4A6D),
    onSecondary = Color(0xFFFBF3E4),
    secondaryContainer = Color(0xFFDCE3EC),
    onSecondaryContainer = Color(0xFF14243A),
    tertiary = Color(0xFFC9707A),
    tertiaryContainer = Color(0xFFF6DDDA),
    onTertiaryContainer = Color(0xFF4A1A20),
    background = Color(0xFFF3E9D8),
    onBackground = Color(0xFF221D1B),
    surface = Color(0xFFFBF5EA),
    onSurface = Color(0xFF221D1B),
    surfaceVariant = Color(0xFFEFE4D2),
    onSurfaceVariant = Color(0xFF574B44),
    surfaceContainerLowest = Color(0xFFFFFBF4),
    surfaceContainerLow = Color(0xFFFBF5EA),
    surfaceContainer = Color(0xFFF7EFE2),
    surfaceContainerHigh = Color(0xFFF3EADB),
    surfaceContainerHighest = Color(0xFFEFE4D2),
    outline = Color(0xFF2A2422),
    outlineVariant = Color(0xFFE0D2BC),
    error = Color(0xFFA3232A),
    errorContainer = Color(0xFFF7DCD8),
    onErrorContainer = Color(0xFF4F0F12),
)

/** De noche: fondo negro tinta, texto crema y el mismo rojo. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0614A),
    onPrimary = Color(0xFF2A0D07),
    primaryContainer = Color(0xFF5C1F15),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFFA9BEDA),
    onSecondary = Color(0xFF14243A),
    secondaryContainer = Color(0xFF2E3D52),
    onSecondaryContainer = Color(0xFFDCE3EC),
    tertiary = Color(0xFFE8A3A8),
    tertiaryContainer = Color(0xFF5A2A30),
    onTertiaryContainer = Color(0xFFF6DDDA),
    background = Color(0xFF1A1615),
    onBackground = Color(0xFFF3E9D8),
    surface = Color(0xFF25201E),
    onSurface = Color(0xFFF3E9D8),
    surfaceVariant = Color(0xFF332C29),
    onSurfaceVariant = Color(0xFFD2C6B7),
    surfaceContainerLowest = Color(0xFF151110),
    surfaceContainerLow = Color(0xFF201B19),
    surfaceContainer = Color(0xFF25201E),
    surfaceContainerHigh = Color(0xFF2C2624),
    surfaceContainerHighest = Color(0xFF332C29),
    outline = Color(0xFF9A8C7D),
    outlineVariant = Color(0xFF3D3532),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD4),
)

private fun TextStyle.zen(weight: FontWeight, size: Int, line: Int) =
    copy(fontFamily = ZenMaru, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Letra legible: un punto por encima de los tamaños de Material, sin pasarse. */
private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Script),
        displayMedium = displayMedium.copy(fontFamily = Script),
        displaySmall = displaySmall.copy(fontFamily = Script),
        headlineLarge = headlineLarge.copy(fontFamily = Script),
        headlineMedium = headlineMedium.copy(fontFamily = Script),
        // Títulos de los diálogos: la letra de cartel es muy ancha, mejor algo más pequeña
        headlineSmall = headlineSmall.copy(fontFamily = Script, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = titleLarge.zen(FontWeight.Bold, 22, 28),
        titleMedium = titleMedium.zen(FontWeight.Bold, 17, 23),
        titleSmall = titleSmall.zen(FontWeight.Bold, 15, 21),
        bodyLarge = bodyLarge.zen(FontWeight.Medium, 16, 24),
        bodyMedium = bodyMedium.zen(FontWeight.Medium, 15, 21),
        bodySmall = bodySmall.zen(FontWeight.Medium, 13, 19),
        labelLarge = labelLarge.zen(FontWeight.Bold, 15, 20),
        labelMedium = labelMedium.zen(FontWeight.Bold, 13, 18),
        labelSmall = labelSmall.zen(FontWeight.Medium, 12, 16),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Colores extra de la app que no forman parte del esquema de Material. */
data class Pastels(
    val background: Brush,
    val accent: Brush,
    val accentVertical: Brush,
    /** Sombra dura desplazada de las tarjetas, como una impresión. */
    val cardShadow: Color,
    val sun: Color,
    val petal: Color,
    val wave: Color,
    /** Grano del papel. */
    val grain: Color,
    val isDark: Boolean,
)

val LocalPastels = staticCompositionLocalOf {
    Pastels(
        Brush.verticalGradient(listOf(Color.White, Color.White)), Brush.horizontalGradient(listOf(Color.Red, Color.Red)),
        Brush.verticalGradient(listOf(Color.Red, Color.Red)), Color.Black, Color.Red, Color.Red, Color.Blue, Color.Black, false,
    )
}

@Composable
fun RutinaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (context.app().themeMode.value) {
        ThemeMode.CLARO -> false
        ThemeMode.OSCURO -> true
        ThemeMode.SISTEMA -> systemDark
    }
    // Iconos de la barra de estado oscuros sobre el papel crema y claros sobre el fondo negro
    val activity = context as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
    }
    val colors = if (dark) DarkColors else LightColors
    val pastels = if (dark) {
        Pastels(
            background = Brush.verticalGradient(listOf(Color(0xFF1C1716), Color(0xFF171312))),
            accent = Brush.horizontalGradient(listOf(Color(0xFFF0614A), Color(0xFFDD4A33))),
            accentVertical = Brush.verticalGradient(listOf(Color(0xFFF0614A), Color(0xFFDD4A33))),
            cardShadow = Color(0xFF7A2216),
            sun = Color(0xFFD7402B).copy(alpha = 0.85f),
            petal = Color(0xFFE8A3A8).copy(alpha = 0.45f),
            wave = Color(0xFF8DA3C4).copy(alpha = 0.16f),
            grain = Color(0xFFF3E9D8).copy(alpha = 0.04f),
            isDark = true,
        )
    } else {
        Pastels(
            background = Brush.verticalGradient(listOf(Color(0xFFF5ECDC), Color(0xFFEFE3CF))),
            accent = Brush.horizontalGradient(listOf(Color(0xFFDD4A33), Color(0xFFCC3A26))),
            accentVertical = Brush.verticalGradient(listOf(Color(0xFFDD4A33), Color(0xFFCC3A26))),
            cardShadow = Color(0xFF2A2422),
            sun = Shu.copy(alpha = 0.92f),
            petal = Color(0xFFDE8E96).copy(alpha = 0.6f),
            wave = Color(0xFF2F4A6D).copy(alpha = 0.16f),
            grain = Color(0xFF5A4636).copy(alpha = 0.07f),
            isDark = false,
        )
    }
    CompositionLocalProvider(LocalPastels provides pastels) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
