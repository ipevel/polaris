// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.domain.model.isPlanValid
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.UsageCard
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.screen.giftcard.GiftCardRedeemSheet
import com.slte.app.ui.screen.giftcard.GiftCardRedeemViewModel
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.utils.AppLog
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import com.slte.app.utils.sanitizeLog

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOrders: () -> Unit = {},
    onInvite: () -> Unit = {},
    onRenew: () -> Unit = {},
    onTickets: () -> Unit = {},
    onSettings: () -> Unit = {},
    onAbout: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val errorMessageRes by viewModel.errorMessageRes.collectAsStateWithLifecycle()
    var showLogoutSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val toast = rememberToast()
    // 优先读面板后台配置（telegram_discuss_link），编译期参数兜底
    val telegramUrl = data.telegramDiscussLink ?: BuildConfig.TELEGRAM_GROUP_URL

    // 礼品卡兑换：弹窗状态 + 结果提示 + 成功后刷新个人中心
    val giftCardViewModel: GiftCardRedeemViewModel = hiltViewModel()
    val giftCardState by giftCardViewModel.state.collectAsStateWithLifecycle()
    val giftCardTip by giftCardViewModel.tip.collectAsStateWithLifecycle()
    val giftCardRedeemed by giftCardViewModel.redeemed.collectAsStateWithLifecycle()

    LaunchedEffect(giftCardTip) {
        giftCardTip?.let { tip ->
            tip.messageRes?.let { toast.show(it) }
            tip.message?.let { toast.show(it) }
            giftCardViewModel.clearTip()
        }
    }

    LaunchedEffect(giftCardRedeemed) {
        if (giftCardRedeemed > 0) viewModel.refresh()
    }

    SlteScaffold(
        title = stringResource(R.string.profile_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
            contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
        ) {
            item {
                if (data.userInfoError) {
                    ErrorCard(
                        messageRes = R.string.notice_error,
                        onRetry = viewModel::retry,
                    )
                } else {
                    UserInfoCard(email = data.email, balance = data.balance)
                }
            }

            item {
                val errorRes = errorMessageRes
                if (data.isLoading) {
                    LoadingCard()
                } else if (errorRes != null) {
                    ErrorCard(
                        messageRes = errorRes,
                        onRetry = viewModel::retry,
                    )
                } else {
                    val info = data.subscribeInfo
                    val hasPlan = info?.hasPlan == true
                    UsageCard(
                        planName = info?.planName ?: "",
                        usedBytes = info?.usedTraffic ?: 0L,
                        totalBytes = info?.transferEnable ?: 0L,
                        isValid = isPlanValid(info),
                        hasPlan = hasPlan,
                        daysUntilExpired = data.daysUntilExpired,
                        expiredAtDate = info?.expiredAt?.takeIf { it > 0L }?.let { FormatUtils.formatExpiryDate(it) },
                        actionText =
                        stringResource(
                            if (hasPlan) R.string.plan_renew_button else R.string.plan_buy_button,
                        ),
                        actionEnabled = true,
                        onAction = onRenew,
                    )
                }
            }

            item {
                NavigateCard(
                    icon = SlteIcons.Orders,
                    title = stringResource(R.string.profile_orders),
                    onClick = onOrders,
                )
            }

            item {
                NavigateCard(
                    icon = SlteIcons.InviteCode,
                    title = stringResource(R.string.gift_card_title),
                    onClick = giftCardViewModel::open,
                )
            }

            item {
                NavigateCard(
                    icon = SlteIcons.InviteRow,
                    title = stringResource(R.string.invite_title),
                    onClick = onInvite,
                )
            }

            item {
                NavigateCard(
                    icon = SlteIcons.Ticket,
                    title = stringResource(R.string.profile_tickets),
                    onClick = onTickets,
                )
            }

            if (telegramUrl.isNotBlank()) {
                item {
                    NavigateCard(
                        icon = SlteIcons.Telegram,
                        title = stringResource(R.string.profile_telegram),
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
                            } catch (e: Exception) {
                                AppLog.w("Polaris-Profile", "打开 Telegram 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                            }
                        },
                    )
                }
            }
            item {
                NavigateCard(
                    icon = SlteIcons.Settings,
                    title = stringResource(R.string.settings_title),
                    onClick = onSettings,
                )
            }
            item {
                NavigateCard(
                    icon = SlteIcons.About,
                    title = stringResource(R.string.profile_about),
                    onClick = onAbout,
                )
            }

            item {
                LogoutCard(onClick = { showLogoutSheet = true })
            }
        }
    }

    if (showLogoutSheet) {
        LogoutConfirmSheet(
            onConfirm = {
                showLogoutSheet = false
                onLogout()
            },
            onDismiss = { showLogoutSheet = false },
        )
    }

    if (giftCardState.visible) {
        GiftCardRedeemSheet(
            state = giftCardState,
            onCodeChange = giftCardViewModel::updateCode,
            onSubmit = giftCardViewModel::submit,
            onDismiss = giftCardViewModel::dismiss,
        )
    }
}
