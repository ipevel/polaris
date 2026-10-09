// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Sheet
import com.slte.app.utils.Dimens

/**
 * 退出登录二次确认。
 *
 * v5 迁移（`00cb5d4` 删除 v4 组件）时丢了这个弹层，`onLogout` 被直接接到 `logout()` 上，
 * 于是「退出登录」一按就掉线重登——v4 的 `LogoutConfirmSheet` 语义就此回退。
 * `profile_logout` / `logout_confirm_message` 两条三语文案一直留在 strings.xml 里没人引用，
 * 这里正是它们的消费方。
 *
 * 沿用 v4 的「底部弹层」形态（而非 AlertDialog）：与 v5 其它选择类弹层一致，
 * 危险动作用 `DANGER` 实色按钮，取消用 `NEUTRAL`，避免误触。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoutConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    V5Sheet(
        title = stringResource(R.string.profile_logout),
        subtitle = stringResource(R.string.logout_confirm_message),
        onDismiss = onDismiss,
    ) {
        V5Button(
            text = stringResource(R.string.profile_logout),
            style = ButtonStyle.DANGER,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
            },
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        V5Button(
            text = stringResource(R.string.purchase_cancel),
            style = ButtonStyle.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
            },
        )
    }
}
