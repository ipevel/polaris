// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 底部面板（Material3 `ModalBottomSheet`）。
 *
 * [shape]/[titleStyle] 是给 v5 页面预留的收敛点：v5 的面板是 26dp 顶圆角 + 17sp 粗标题，
 * 与 v4 的 22dp 圆角 + 18sp 半粗标题是两套语言。默认值保持 v4 原值，因此既有调用方零影响；
 * v5 调用方显式传入即可，不需要另造一个面板组件（否则滚动、IME 避让、locales 覆盖、
 * 无障碍语义这些 Material 行为都要重写一遍）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlteSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    dismissible: Boolean = true,
    compact: Boolean = false,
    shape: Shape = SlteShapes.extraLarge,
    titleStyle: TextStyle = SlteType.heading,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                dismissible || value != SheetValue.Hidden
            },
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AppLocaleContent(locale = LocalAppLocale.current) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = Dimens.sheetPaddingH,
                        vertical = Dimens.sheetPaddingV,
                    ),
            ) {
                header?.let { headerContent ->

                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { headerContent() }
                }
                if (title != null) {
                    if (header != null) Spacer(modifier = Modifier.height(Dimens.gap.md))
                    Text(
                        text = title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        text = subtitle,
                        style = SlteType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) Dimens.gap.sm else Dimens.gap.xl))
                content()
                Spacer(modifier = Modifier.height(Dimens.gap.lg))
            }
        }
    }
}
