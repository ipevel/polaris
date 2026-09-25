// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// v5（VmShell）Material 配色：与 V5Theme 保持一致，整个 App（含尚未重构为
// v5 布局的页面）统一使用 v5 色板
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2F6BF6),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF16AC6C),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFFEE8A2C),
        error = Color(0xFFE5484D),
        background = Color(0xFFF1F0F6),
        onBackground = Color(0xFF191C26),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF191C26),
        surfaceVariant = Color(0xFFF6F5FA),
        onSurfaceVariant = Color(0xFF575E72),
        outline = Color(0xFFE8E6EF),
        outlineVariant = Color(0xFFEFEDF5),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF6E97FF),
        onPrimary = Color(0xFF0B0D15),
        secondary = Color(0xFF3DCC8E),
        onSecondary = Color(0xFF0B0D15),
        tertiary = Color(0xFFF5A25B),
        error = Color(0xFFF4776D),
        background = Color(0xFF0B0D15),
        onBackground = Color(0xFFEDF0F8),
        surface = Color(0xFF171A25),
        onSurface = Color(0xFFEDF0F8),
        surfaceVariant = Color(0xFF1F2331),
        onSurfaceVariant = Color(0xFFA7AEC2),
        outline = Color(0x14FFFFFF),
        outlineVariant = Color(0x0DFFFFFF),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        // v5 设计令牌全局下发：v5 原生页面与 v4 布局页面共享同一套明暗状态
        LocalV5Colors provides if (darkTheme) DarkV5Colors else LightV5Colors,

        LocalTextSelectionColors provides
            TextSelectionColors(
                handleColor = extendedColors.accentInteractive,
                backgroundColor = extendedColors.textSelectionBg,
            ),
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SlteTypography,
            shapes = SlteShapes,
            content = content,
        )
    }
}

object SlteColors {
    val current: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
