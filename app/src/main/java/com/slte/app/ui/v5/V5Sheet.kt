// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slte.app.ui.component.AppLocaleContent
import com.slte.app.ui.component.LocalAppLocale
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 底部面板（v6 iOS 语言）：小圆角 + 顶部抓手 + 17sp 居中粗标题。
 *
 * 行为沿用 Material3 `ModalBottomSheet`（滚动、IME 避让、拖拽关闭、无障碍语义都不重写），
 * 只把视觉收敛到 v6：[V5SheetShape]、[V5SheetTitleStyle]、V5 配色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V5Sheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    dismissible: Boolean = true,
    compact: Boolean = false,
    shape: Shape = V5SheetShape,
    titleStyle: TextStyle = V5SheetTitleStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
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
        containerColor = c.surface,
        dragHandle = null,
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
                // iOS 抓手
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.surface3),
                )
                Spacer(modifier = Modifier.height(Dimens.gap.md))
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
                        color = c.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        text = subtitle,
                        style = SlteType.bodySmall,
                        color = c.text3,
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
