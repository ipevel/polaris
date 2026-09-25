// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.PlanInfo
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.EmptyState
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.RichText
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onGoToOrders: () -> Unit = {},
    viewModel: PlansViewModel = hiltViewModel(),
    purchaseViewModel: PurchaseViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val purchaseStep by purchaseViewModel.step.collectAsStateWithLifecycle()

    SlteScaffold(
        title = stringResource(R.string.plans_title),
        onBack = onBack,
    ) { innerPadding ->
        val errorRes = data.errorMessageRes
        when {
            data.phase == ContentPhase.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
                }
            }
            errorRes != null && data.plans.isEmpty() -> {
                ErrorState(
                    message = stringResource(errorRes),
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            data.plans.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.plan_empty),
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                SltePullRefresh(
                    isRefreshing = data.phase == ContentPhase.Refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    LazyColumn(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.dashboardScreenPaddingH),
                        verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
                        contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
                    ) {
                        val plans = data.plans.distinctBy { it.id }
                        itemsIndexed(plans, key = { _, plan -> plan.id }) { index, plan ->
                            PlanCard(
                                plan = plan,
                                // 面板未下发「推荐」标记，取列表首项作为主推套餐，其余降级为 Tonal。
                                isRecommended = index == 0,
                                onSubscribe = { purchaseViewModel.startPurchase(plan) },
                            )
                        }
                    }
                }
            }
        }
    }

    PurchaseFlow(
        step = purchaseStep,
        onSelectPeriod = purchaseViewModel::selectPeriod,
        onUpdateCoupon = purchaseViewModel::updateCouponCode,
        onVerifyCoupon = purchaseViewModel::verifyCoupon,
        onConfirmOrder = purchaseViewModel::showConfirmWarning,
        onCancelWarning = purchaseViewModel::cancelWarning,
        onConfirmWarning = purchaseViewModel::confirmWarning,
        onSelectPayment = purchaseViewModel::selectPaymentMethod,
        onConfirmPayment = purchaseViewModel::confirmPayment,
        onPaymentReturn = purchaseViewModel::onPaymentReturn,
        onDismiss = purchaseViewModel::goBack,
        onGoToOrders = onGoToOrders,
    )
}

@Composable
private fun PlanCard(
    plan: PlanInfo,
    isRecommended: Boolean,
    onSubscribe: () -> Unit,
) {
    val context = LocalContext.current
    val firstPrice = plan.periodPrices.firstOrNull()
    val periodName =
        firstPrice?.let { FormatUtils.periodLabel(it.period, context) }.orEmpty()

    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimens.gap.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = plan.name,
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (firstPrice != null) {
                    Spacer(modifier = Modifier.width(Dimens.gap.md))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(firstPrice.price.toLongOrNull()?.toInt() ?: 0),
                            style = SlteType.value.copy(fontSize = 26.sp, lineHeight = 32.sp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.plan_separator) + " " + periodName,
                            style = SlteType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlanPill(
                    label = stringResource(R.string.plans_traffic_label),
                    value = "${plan.transferEnable}${stringResource(R.string.plans_traffic_unit)}",
                )
                if (periodName.isNotEmpty()) {
                    PlanPill(value = periodName, mono = false)
                }
            }

            if (!plan.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gap.md))
                HorizontalDivider(
                    thickness = Dimens.dividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(Dimens.gap.md))
                // 面板下发的 content 统一走 RichText（HTML 走 HtmlText、Markdown 走渲染器），
                // 与公告页保持同一套支持格式。此前 `benefits.size > 1` 会把多行内容降级成
                // 纯文本逐行加勾：`**加粗**` 只剩「加粗**」、`- 列表` 被 trimStart 掉符号，
                // 「套餐页不支持 md」的根因就在这里，而不是渲染器没接线。
                RichText(
                    text = plan.content,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.lg))

            SlteButton(
                text = stringResource(R.string.plans_subscribe),
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                style = if (isRecommended) SlteButtonStyle.Primary else SlteButtonStyle.Tonal,
            )
        }
    }
}

@Composable
private fun PlanPill(
    value: String,
    label: String? = null,
    mono: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(SlteRadii.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.gap.md, vertical = Dimens.gap.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = SlteType.label,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
            }
            Text(
                text = value,
                style = if (mono) SlteType.valueSmall else SlteType.label,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
