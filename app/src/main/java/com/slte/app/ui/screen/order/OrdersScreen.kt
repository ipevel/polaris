// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.order

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.OrderInfo
import com.slte.app.domain.model.OrderStatus
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.SlteIcons
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
 * 我的订单页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SlteCard` + `EmptyState/ErrorState/LoadingBox` 版本；
 * 入口与行为逐项对齐（见交付报告的「订单页入口对账清单」）：返回、下拉刷新、刷新失败提示、
 * 重试、空态、单卡去重列表、明细三行、待支付订单的「取消订单 / 继续支付」。
 *
 * 注意三态的触发条件与公告/工单**不同**（原样保留，未顺手"统一"）：
 * 只有在 `orders` 为空时才走整页加载/错误/空态；一旦已有订单，加载与失败都只表现为
 * 下拉刷新的指示器或轻提示——这样用户刷新时列表不会闪成白屏。
 */
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onPay: (String) -> Unit = {},
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()

    ToastTip(
        message = data.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.profile_orders),
            onBack = onBack,
        )

        val errorRes = data.errorMessageRes
        when {
            data.phase == ContentPhase.Loading && data.orders.isEmpty() -> {
                StateScrollable { V5LoadingState() }
            }

            errorRes != null && data.orders.isEmpty() -> {
                StateScrollable {
                    V5ErrorState(
                        message = stringResource(errorRes),
                        onRetry = viewModel::retry,
                    )
                }
            }

            data.orders.isEmpty() -> {
                StateScrollable {
                    V5EmptyState(title = stringResource(R.string.order_empty))
                }
            }

            else -> {
                V5PullRefresh(
                    isRefreshing = data.phase == ContentPhase.Refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OrderList(
                        orders = data.orders,
                        onCancel = viewModel::cancelOrder,
                        onPay = onPay,
                    )
                }
            }
        }
    }
}

/**
 * 订单列表：一笔订单一行的清单，全部装在同一张卡里。
 *
 * 保留 v4 的这个决定：卡片竖排时相邻边界只有 1.25:1，分组几乎不可见。
 */
@Composable
private fun OrderList(
    orders: List<OrderInfo>,
    onCancel: (String) -> Unit,
    onPay: (String) -> Unit,
) {
    val unique = remember(orders) { orders.distinctBy { it.tradeNo } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
                unique.forEachIndexed { index, order ->
                    OrderRow(
                        order = order,
                        topDivider = index > 0,
                        onCancel = { onCancel(order.tradeNo) },
                        onPay = { onPay(order.tradeNo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderRow(
    order: OrderInfo,
    topDivider: Boolean,
    onCancel: () -> Unit,
    onPay: () -> Unit,
) {
    val c = V5ThemeColors.current
    val isPending = order.statusClass == OrderStatus.PENDING

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = order.planName.ifBlank { stringResource(R.string.order_unknown_plan) },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OrderStatusChip(status = order.statusClass)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = c.hairline2)
            Spacer(modifier = Modifier.height(12.dp))

            OrderDetailRow(
                label = stringResource(R.string.order_price),
                value = formatCurrency(order.totalAmount),
                titleSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderDetailRow(
                label = stringResource(R.string.order_created),
                value = FormatUtils.formatDate(order.createdAt),
                titleSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderDetailRow(
                label = stringResource(R.string.order_id),
                value = order.tradeNo,
                titleSize = 13.sp,
            )

            if (isPending) {
                Spacer(modifier = Modifier.height(15.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    V5Button(
                        text = stringResource(R.string.order_cancel),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.NEUTRAL,
                    )
                    V5Button(
                        text = stringResource(R.string.order_pay),
                        onClick = onPay,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.PRIMARY,
                    )
                }
            }
        }
    }
}

/** 明细行：左侧文案 + 右侧等宽数值（数值右对齐，同列小数点/位数不抖）。 */
@Composable
private fun OrderDetailRow(
    label: String,
    value: String,
    titleSize: TextUnit,
) {
    val c = V5ThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = c.text3,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 订单状态徽标（v5 胶囊 + 状态图标）：待支付警示 / 已完成成功 / 已取消中性 / 异常危险。 */
@Composable
private fun OrderStatusChip(status: OrderStatus) {
    val (label, tone, icon) =
        when (status) {
            OrderStatus.PENDING ->
                Triple(stringResource(R.string.order_status_pending), ChipTone.WARN, SlteIcons.OrderPending)
            OrderStatus.COMPLETED ->
                Triple(stringResource(R.string.order_status_completed), ChipTone.OK, SlteIcons.OrderCompleted)
            OrderStatus.CANCELLED ->
                Triple(stringResource(R.string.order_status_cancelled), ChipTone.NEUTRAL, SlteIcons.OrderCancelled)
            OrderStatus.ABNORMAL ->
                Triple(stringResource(R.string.order_status_abnormal), ChipTone.DANGER, SlteIcons.OrderAbnormal)
        }

    V5Chip(tone = tone, text = label, icon = icon)
}

/** 不滚动内容的脚手架（保持可滚动，否则空/错态下拉刷新失效，理由同公告页）。 */
@Composable
private fun StateScrollable(
    horizontalPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
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
