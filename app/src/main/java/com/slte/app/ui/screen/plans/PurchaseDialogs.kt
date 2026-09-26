// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.component.LocaleAwareAlertDialog
import com.slte.app.ui.theme.V5ThemeColors

/**
 * 购买流程的三个确认/错误弹窗（v5）。
 *
 * 弹窗保留 Material3 的 `AlertDialog`（经 `LocaleAwareAlertDialog` 包一层语言）而不是换成自绘
 * 卡片：它承担返回键关闭、点击外部关闭、无障碍焦点陷阱与读屏语义，换掉要重写这些。
 * 这里只把**按钮换成 v5 风格的文字按钮**：v4 用的是 Material `TextButton`（会吃外层主题的
 * 主色与内边距），v5 统一为「无涟漪 + 强调色文字 + 10dp 圆角按压区」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmWarningDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LocaleAwareAlertDialog(
        onDismissRequest = onCancel,
        containerColor = V5ThemeColors.current.surface,
        titleContentColor = V5ThemeColors.current.text,
        textContentColor = V5ThemeColors.current.text2,
        title = {
            Text(
                text = stringResource(R.string.purchase_warning_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(text = stringResource(R.string.purchase_warning_message))
        },
        dismissButton = {
            V5DialogAction(stringResource(R.string.purchase_cancel), primary = false) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCancel()
            }
        },
        confirmButton = {
            V5DialogAction(stringResource(R.string.purchase_confirm), primary = true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
            }
        },
    )
}

@Composable
internal fun ExistingOrderErrorDialog(
    errorMessageRes: Int,
    onGoToOrders: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LocaleAwareAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = V5ThemeColors.current.surface,
        titleContentColor = V5ThemeColors.current.text,
        textContentColor = V5ThemeColors.current.text2,
        title = {
            Text(
                text = stringResource(R.string.purchase_existing_order_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(text = stringResource(R.string.purchase_existing_order_message))
        },
        dismissButton = {
            V5DialogAction(stringResource(R.string.purchase_cancel), primary = false) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
            }
        },
        confirmButton = {
            V5DialogAction(stringResource(R.string.order_pay), primary = true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onGoToOrders()
            }
        },
    )
}

@Composable
internal fun OrderCreateErrorDialog(
    errorMessageRes: Int,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LocaleAwareAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = V5ThemeColors.current.surface,
        titleContentColor = V5ThemeColors.current.text,
        textContentColor = V5ThemeColors.current.text2,
        title = {
            Text(
                text = stringResource(R.string.purchase_error_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(text = stringResource(errorMessageRes))
        },
        confirmButton = {
            V5DialogAction(stringResource(R.string.purchase_confirm), primary = true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
            }
        },
    )
}

/** 弹窗动作位（v5 文字按钮）：强调色文字 + 无涟漪 + 12dp 圆角按压区。 */
@Composable
private fun V5DialogAction(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) c.accent else c.text2,
            fontSize = 14.sp,
        )
    }
}
