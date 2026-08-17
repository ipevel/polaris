package com.slte.app.ui.screen.invite

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.theme.SlteColors
import com.slte.app.utils.Dimens

/** 邀请返利页：佣金概览、操作按钮、邀请码、佣金记录；数据由 SlteApp 预加载，进入时无需 Loading。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    onBack: () -> Unit = {},
    viewModel: InviteViewModel
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = LocalContext.current

    com.slte.app.ui.component.ToastTip(
        messageRes = data.toastRes,
        onDismiss = viewModel::clearToast
    )

    SlteScaffold(
        title = stringResource(R.string.invite_title),
        onBack = onBack
    ) { innerPadding ->
        SltePullRefresh(
            isRefreshing = data.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(innerPadding)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
            contentPadding = PaddingValues(
                vertical = Dimens.dashboardScreenPaddingV
            )
        ) {
            item { InviteStatCard(stat = data.stat) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing)
                ) {
                    InviteActionButton(
                        icon = Icons.Rounded.SwapHoriz,
                        text = stringResource(R.string.invite_transfer),
                        tint = SlteColors.current.iconBlue,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::showTransferSheet
                    )
                    InviteActionButton(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        text = stringResource(R.string.invite_withdraw),
                        tint = SlteColors.current.iconBlue,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::showWithdrawSheet
                    )
                }
            }

            item {
                InviteCodeCard(
                    codes = data.codes,
                    isGenerating = data.isGenerating,
                    onGenerate = viewModel::generateCode,
                    context = context
                )
            }

            item {
                CommissionRecordsCard(records = data.records)
            }
        }
        }
    }

    if (data.showTransferSheet) {
        TransferSheet(
            availableBalance = data.stat.availableBalance,
            onDismiss = viewModel::hideTransferSheet,
            onConfirm = { yuan -> viewModel.transferCommission(yuan) }
        )
    }

    if (data.showWithdrawSheet) {
        WithdrawSheet(
            availableBalance = data.stat.availableBalance,
            onDismiss = viewModel::hideWithdrawSheet,
            onConfirm = { method, account -> viewModel.withdraw(method, account) }
        )
    }

    LoadingOverlay(visible = data.isGenerating, onDismiss = viewModel::cancelLoading)
}
