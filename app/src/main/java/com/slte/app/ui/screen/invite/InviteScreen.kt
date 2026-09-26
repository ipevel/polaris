// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5PageBody
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5PullRefresh
import com.slte.app.ui.v5.V5TopBar

/**
 * 邀请返利页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SltePullRefresh` + `SlteCard` 版本；入口与行为逐项对齐
 * （见本轮交付报告的「邀请页入口对账清单」）：返回、下拉刷新、「转赠佣金」、「提现」、
 * 邀请码卡（生成 / 复制 / 空态）、佣金记录卡（含空态）、转赠面板、提现面板、
 * 生成邀请码时的全屏 Loading 遮罩、轻提示。
 *
 * 骨架选 [V5PageBody]（无导航的二级页）而不是 `LazyColumn`：这一屏固定四块内容，
 * 不是由面板数据决定长度的列表，`LazyColumn` 在这里只多一层无意义的惰性开销。
 */
@Composable
fun InviteScreen(
    viewModel: InviteViewModel,
    onBack: () -> Unit = {},
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    ToastTip(
        message = data.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.invite_title),
            onBack = onBack,
        )

        V5PullRefresh(
            isRefreshing = data.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            V5PageBody {
                InviteStatCard(stat = data.stat)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    V5Button(
                        text = stringResource(R.string.invite_transfer),
                        onClick = viewModel::showTransferSheet,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.TONAL,
                    )
                    V5Button(
                        text = stringResource(R.string.invite_withdraw),
                        onClick = viewModel::showWithdrawSheet,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.NEUTRAL,
                    )
                }

                InviteCodeCard(
                    codes = data.codes,
                    isGenerating = data.isGenerating,
                    onGenerate = viewModel::generateCode,
                    context = context,
                )

                CommissionRecordsCard(records = data.records)
            }
        }
    }

    if (data.sheet == InviteSheet.Transfer) {
        TransferSheet(
            availableBalance = data.stat.availableBalance,
            isSubmitting = data.isSubmitting,
            onDismiss = viewModel::hideTransferSheet,
            onConfirm = { yuan -> viewModel.transferCommission(yuan) },
        )
    }

    if (data.sheet == InviteSheet.Withdraw) {
        WithdrawSheet(
            methodsState = data.withdrawMethods,
            isSubmitting = data.isSubmitting,
            onDismiss = viewModel::hideWithdrawSheet,
            onRetryMethods = viewModel::retryWithdrawMethods,
            onConfirm = { method, account -> viewModel.withdraw(method, account) },
        )
    }

    LoadingOverlay(visible = data.isGenerating, onDismiss = viewModel::cancelLoading)
}
