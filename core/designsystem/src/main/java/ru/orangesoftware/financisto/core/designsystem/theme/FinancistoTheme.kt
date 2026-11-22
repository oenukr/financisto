// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

object FinancistoTheme {
    val semanticColors: FinancistoSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFinancistoSemanticColors.current

    val dimens: FinancistoDimens = FinancistoDimens
}

@Composable
fun FinancistoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FinancistoDarkColorScheme else FinancistoLightColorScheme
    val semanticColors = if (darkTheme) DarkFinancistoSemanticColors else LightFinancistoSemanticColors

    CompositionLocalProvider(LocalFinancistoSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinancistoTypography,
            shapes = FinancistoShapes,
            content = content,
        )
    }
}
