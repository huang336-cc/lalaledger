package com.lightledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lightledger.app.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = Color(0xFFD5EFE9),
    onPrimaryContainer = Color(0xFF1E4A42),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1ECF9),
    onSecondaryContainer = Color(0xFF2A4560),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightExpense,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = Color(0xFF2A4A44),
    onPrimaryContainer = Color(0xFFBCE7DC),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF16283C),
    secondaryContainer = Color(0xFF2A3D55),
    onSecondaryContainer = Color(0xFFCBDDF2),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkExpense,
)

/** 支出/收入语义色，随主题切换 */
data class SemanticColors(
    val expense: Color,
    val income: Color,
)

@Composable
fun LightLedgerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    // 通过 CompositionLocal 提供支出/收入语义色
    val semantics = if (useDark) {
        SemanticColors(expense = DarkExpense, income = DarkIncome)
    } else {
        SemanticColors(expense = LightExpense, income = LightIncome)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSemanticColors provides semantics
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val LocalSemanticColors = androidx.compose.runtime.staticCompositionLocalOf {
    SemanticColors(expense = LightExpense, income = LightIncome)
}

/** 便捷访问支出/收入色 */
object SemanticTheme {
    val colors: SemanticColors
        @Composable get() = LocalSemanticColors.current
}
