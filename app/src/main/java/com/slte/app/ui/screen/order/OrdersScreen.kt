// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.OrderInfo
import com.slte.app.domain.model.OrderStatus
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.EmptyState
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LoadingBox
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 状态徽标统一高度：24dp = 16dp 行高 + 上下各 4dp 内边距，胶囊形。 */
private val ChipMinHeight = 24.dp

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

    SlteScaffold(
        title = stringResource(R.string.profile_orders),
        onBack = onBack,
    ) { innerPadding ->
        val errorRes = data.errorMessageRes
        when {
            data.phase == ContentPhase.Loading && data.orders.isEmpty() -> {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingBox()
                }
            }
            errorRes != null && data.orders.isEmpty() -> {
                ErrorState(
                    message = stringResource(errorRes),
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            data.orders.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.order_empty),
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                SltePullRefresh(
                    isRefreshing = data.phase == ContentPhase.Refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.padding(innerPadding),
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

@Composable
private fun OrderList(
    orders: List<OrderInfo>,
    onCancel: (String) -> Unit,
    onPay: (String) -> Unit,
) {
    // 一笔订单一行的清单，全部装在同一张卡里；卡片竖排时相邻边界只有 1.25:1，分组几乎不可见。
    val unique = remember(orders) { orders.distinctBy { it.tradeNo } }
    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.dashboardScreenPaddingH),
        contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
    ) {
        item {
            SlteCard(modifier = Modifier.fillMaxWidth()) {
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
    val isPending = order.statusClass == OrderStatus.PENDING

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimens.gap.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = order.planName.ifBlank { stringResource(R.string.order_unknown_plan) },
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                OrderStatusChip(status = order.statusClass)
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            OrderDetailRow(
                label = stringResource(R.string.order_price),
                value = formatCurrency(order.totalAmount),
                valueStyle = SlteType.value,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            OrderDetailRow(
                label = stringResource(R.string.order_created),
                value = FormatUtils.formatDate(order.createdAt),
                valueStyle = SlteType.valueSmall,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            OrderDetailRow(
                label = stringResource(R.string.order_id),
                value = order.tradeNo,
                valueStyle = SlteType.valueSmall,
            )

            if (isPending) {
                Spacer(modifier = Modifier.height(Dimens.gap.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
                ) {
                    SlteButton(
                        text = stringResource(R.string.order_cancel),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        style = SlteButtonStyle.Neutral,
                    )
                    SlteButton(
                        text = stringResource(R.string.order_pay),
                        onClick = onPay,
                        modifier = Modifier.weight(1f),
                        style = SlteButtonStyle.Primary,
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
    valueStyle: TextStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Dimens.gap.md))
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OrderStatusChip(status: OrderStatus) {
    val style =
        when (status) {
            OrderStatus.PENDING ->
                StatusStyle(
                    stringResource(R.string.order_status_pending),
                    SlteIcons.OrderPending,
                    SlteColors.current.statusWarning,
                    SlteColors.current.statusWarningBg,
                )
            OrderStatus.COMPLETED ->
                StatusStyle(
                    stringResource(R.string.order_status_completed),
                    SlteIcons.OrderCompleted,
                    SlteColors.current.statusSuccess,
                    SlteColors.current.statusSuccessBg,
                )
            OrderStatus.CANCELLED ->
                StatusStyle(
                    stringResource(R.string.order_status_cancelled),
                    SlteIcons.OrderCancelled,
                    SlteColors.current.statusNeutral,
                    SlteColors.current.statusNeutralBg,
                )
            OrderStatus.ABNORMAL ->
                StatusStyle(
                    stringResource(R.string.order_status_abnormal),
                    SlteIcons.OrderAbnormal,
                    SlteColors.current.statusDanger,
                    SlteColors.current.statusDangerBg,
                )
        }

    Surface(
        shape = RoundedCornerShape(SlteRadii.pill),
        color = style.bg,
    ) {
        Row(
            modifier =
            Modifier
                .defaultMinSize(minHeight = ChipMinHeight)
                .padding(
                    horizontal = Dimens.gap.sm,
                    vertical = Dimens.gap.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.paymentMethodDotSize),
                tint = style.fg,
            )
            Spacer(modifier = Modifier.width(Dimens.gap.xs))
            Text(
                text = style.text,
                style = SlteType.caption,
                color = style.fg,
            )
        }
    }
}

private data class StatusStyle(
    val text: String,
    val icon: ImageVector,
    val fg: Color,
    val bg: Color,
)
