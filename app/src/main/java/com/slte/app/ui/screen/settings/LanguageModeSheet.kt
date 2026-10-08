// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.v5.SheetOption
import com.slte.app.ui.v5.V5Sheet
import com.slte.app.utils.isTraditionalChinese
import java.util.Locale

enum class LanguageMode(
    val locale: Locale?,
    val labelRes: Int,
) {
    FOLLOW_SYSTEM(null, R.string.language_follow_system),
    SIMPLIFIED(Locale.SIMPLIFIED_CHINESE, R.string.language_simplified),
    TRADITIONAL(Locale.TRADITIONAL_CHINESE, R.string.language_traditional),
    ENGLISH(Locale.ENGLISH, R.string.language_english),
    ;

    companion object {

        fun fromLocale(locale: Locale?): LanguageMode = when {
            locale == null -> FOLLOW_SYSTEM
            locale.language == "zh" && isTraditionalChinese(locale) -> TRADITIONAL
            locale.language == "zh" -> SIMPLIFIED
            locale.language == "en" -> ENGLISH
            else -> FOLLOW_SYSTEM
        }
    }
}

@Composable
internal fun LanguageModeSheet(
    currentMode: LanguageMode,
    onDismiss: () -> Unit,
    onSelect: (LanguageMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    V5Sheet(
        title = stringResource(R.string.settings_language),
        onDismiss = onDismiss,
    ) {
        Column(
            // selectableGroup：把整列声明成一个单选组，屏幕阅读器才会播报「N 项中的第 M 项」
            // 并在组内做方向键导航；Role.RadioButton 只给单行角色，管不到组。
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageMode.entries.forEach { mode ->
                val selected = currentMode == mode
                SheetOption(
                    title = stringResource(mode.labelRes),
                    selected = selected,
                    modifier = Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(mode)
                        },
                    ),
                )
            }
        }
    }
}
