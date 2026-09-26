// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.PlanInfo
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.RichText
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5EmptyState
import com.slte.app.ui.v5.V5ErrorState
import com.slte.app.ui.v5.V5LoadingState
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5PullRefresh
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.utils.FormatUtils

/**
 * 套餐购买页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SlteCard` + `EmptyState/ErrorState` 版本；入口与行为逐项对齐
 * （见交付报告的「套餐页入口对账清单」）：返回、下拉刷新、整页错误 + 重试、空态、套餐卡列表、
 * 首项主推、流量/周期胶囊、面板富文本介绍、订阅按钮、以及整套购买流程弹层。
 *
 * 加载态原来用 `LottieLoadingIcon`（v4 遗留），本轮换成 [V5LoadingState]。两个原因：
 * 1. 用户本轮的要求是「全部 V5」，而这处动效是 v4 观感，算残留；
 * 2. **Lottie 在 Robolectric 下根本不渲染**——实测 `35b` 截图正文区一个像素都没有
 *    （整页只剩顶栏标题），加载态截图的凭证价值为零。换成 v5 状态卡片后三态都有实证。
 */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onGoToOrders: () -> Unit = {},
    viewModel: PlansViewModel = hiltViewModel(),
    purchaseViewModel: PurchaseViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val purchaseStep by purchaseViewModel.step.collectAsStateWithLifecycle()

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.plans_title),
            onBack = onBack,
        )

        val errorRes = data.errorMessageRes
        when {
            data.phase == ContentPhase.Loading -> {
                StateScrollable { V5LoadingState() }
            }

            errorRes != null && data.plans.isEmpty() -> {
                StateScrollable {
                    V5ErrorState(
                        message = stringResource(errorRes),
                        onRetry = viewModel::retry,
                    )
                }
            }

            data.plans.isEmpty() -> {
                StateScrollable {
                    V5EmptyState(title = stringResource(R.string.plan_empty))
                }
            }

            else -> {
                V5PullRefresh(
                    isRefreshing = data.phase == ContentPhase.Refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                    ) {
                        val plans = data.plans.distinctBy { it.id }
                        itemsIndexed(plans, key = { _, plan -> plan.id }) { index, plan ->
                            PlanCard(
                                plan = plan,
                                // 面板未下发「推荐」标记，取列表首项作为主推套餐，其余降级为 TONAL。
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
    val c = V5ThemeColors.current
    val context = LocalContext.current
    val firstPrice = plan.periodPrices.firstOrNull()
    val periodName =
        firstPrice?.let { FormatUtils.periodLabel(it.period, context) }.orEmpty()

    V5CardFlat(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.height(6.dp))
                        V5Chip(tone = ChipTone.ACCENT, text = stringResource(R.string.plans_recommended))
                    }
                }
                if (firstPrice != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(firstPrice.price.toLongOrNull()?.toInt() ?: 0),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.accent,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.plan_separator) + " " + periodName,
                            fontSize = 11.5.sp,
                            color = c.text3,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlanPill(
                    label = stringResource(R.string.plans_traffic_label),
                    value = "${plan.transferEnable}${stringResource(R.string.plans_traffic_unit)}",
                )
                if (periodName.isNotEmpty()) {
                    PlanPill(value = periodName)
                }
            }

            if (!plan.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                Spacer(modifier = Modifier.height(12.dp))
                // 面板下发的 content 统一走 RichText（HTML 走 HtmlText、Markdown 走渲染器），
                // 与公告页保持同一套支持格式。此前 `benefits.size > 1` 会把多行内容降级成
                // 纯文本逐行加勾：`**加粗**` 只剩「加粗**」、`- 列表` 被 trimStart 掉符号，
                // 「套餐页不支持 md」的根因就在这里，而不是渲染器没接线。
                RichText(
                    text = plan.content,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            V5Button(
                text = stringResource(R.string.plans_subscribe),
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                style = if (isRecommended) ButtonStyle.PRIMARY else ButtonStyle.TONAL,
            )
        }
    }
}

/** 套餐信息胶囊（v5：`surface2` 底 + 14dp 圆角，主值等宽）。 */
@Composable
private fun PlanPill(
    value: String,
    label: String? = null,
) {
    val c = V5ThemeColors.current
    Row(
        modifier =
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(text = label, fontSize = 11.5.sp, color = c.text3, maxLines = 1)
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 不滚动内容的脚手架（保持可滚动，否则空/错态下拉刷新失效，理由同公告页）。 */
@Composable
private fun StateScrollable(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
