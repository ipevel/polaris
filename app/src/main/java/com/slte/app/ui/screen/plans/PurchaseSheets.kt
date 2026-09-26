// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.PlanInfo
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.component.formatNegCurrency
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.utils.FormatUtils

/**
 * 选择周期 / 用券 / 确认下单面板（v5）。
 *
 * 行为逐项保留：周期两列网格（选中蓝底）、优惠券输入 + 验证（验证中转圈）、
 * 原价与优惠金额两行、确认按钮的可用条件（未填券 或 已验券）、确认按钮显示最终价。
 */
@Composable
internal fun SelectPeriodSheet(
    step: PurchaseStep.SelectPeriod,
    onSelectPeriod: (String) -> Unit,
    onUpdateCoupon: (String) -> Unit,
    onVerifyCoupon: () -> Unit,
    onConfirmOrder: () -> Unit,
    onDismiss: () -> Unit,
) {
    SlteSheet(
        onDismiss = onDismiss,
        title = "${stringResource(R.string.plans_subscribe)} - ${step.plan.name}",
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        PeriodGrid(
            periods = step.plan.periodPrices,
            selectedPeriod = step.selectedPeriod,
            onSelect = onSelectPeriod,
        )

        Spacer(modifier = Modifier.height(16.dp))

        CouponInput(
            code = step.couponCode,
            onCodeChange = onUpdateCoupon,
            onVerify = onVerifyCoupon,
            isVerifying = step.isVerifying,
        )

        Spacer(modifier = Modifier.height(16.dp))

        PriceRow(
            label = stringResource(R.string.order_price),
            value = formatCurrency(step.priceCents),
        )
        PriceRow(
            label = stringResource(R.string.purchase_coupon_discount),
            value =
            if (step.couponDiscount > 0) {
                formatNegCurrency(step.couponDiscount)
            } else {
                FormatUtils.balance(step.couponDiscount)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        V5Button(
            text = "${stringResource(R.string.purchase_confirm_order)} ${formatCurrency(step.finalPrice)}",
            onClick = onConfirmOrder,
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.PRIMARY,
            onClickEnabled = step.couponCode.isBlank() || step.couponVerified,
        )
    }
}

/** 周期两列网格（v5：选中 = 蓝底白字，未选 = `surface2` 底）。 */
@Composable
internal fun PeriodGrid(
    periods: List<PlanInfo.PeriodPrice>,
    selectedPeriod: String,
    onSelect: (String) -> Unit,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val rows = periods.chunked(2)
    val shape = RoundedCornerShape(14.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { pp ->
                    val selected = selectedPeriod == pp.period
                    val price = pp.price.toLongOrNull()?.toInt() ?: 0
                    Column(
                        modifier =
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (selected) c.accent else c.surface2)
                            .then(
                                noRippleClickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(pp.period)
                                },
                            )
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = FormatUtils.periodLabel(pp.period, context),
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else c.text2,
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = formatCurrency(price),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else c.text,
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 优惠券输入 + 验证（v5 输入框 + 尾部动作位）。 */
@Composable
internal fun CouponInput(
    code: String,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    isVerifying: Boolean,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    V5Input(
        value = code,
        onValueChange = onCodeChange,
        placeholder = stringResource(R.string.purchase_coupon_hint),
        icon = SlteIcons.Coupon,
        iconDesc = stringResource(R.string.purchase_coupon_hint),
        small = true,
        trailing = {
            val enabled = code.isNotBlank() && !isVerifying
            Box(
                modifier =
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) c.accentBg else c.surface3)
                    .then(
                        noRippleClickable(
                            if (enabled) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onVerify()
                                }
                            } else {
                                null
                            },
                        ),
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isVerifying) {
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.purchase_verify),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) c.accent else c.text3,
                    )
                }
            }
        },
    )
}
