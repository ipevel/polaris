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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.component.formatNegCurrency
import com.slte.app.ui.component.formatPlusCurrency
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5FieldHint
import com.slte.app.utils.FormatUtils

@Composable
internal fun OrderPaymentSheet(
    step: PurchaseStep.OrderPayment,
    onSelectPayment: (Int) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = V5ThemeColors.current
    SlteSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.purchase_order_info),
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        val payAmount = step.payAmount

        if (step.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = c.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface2)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OrderInfoRow(
                    label = stringResource(R.string.purchase_product),
                    value = step.planName,
                    isValueEmphasize = true,
                    valueMono = false,
                )
                OrderInfoDivider()
                OrderInfoRow(
                    label = stringResource(R.string.purchase_product_price),
                    value = formatCurrency(step.productPrice),
                )
                if (step.couponDiscount > 0) {
                    OrderInfoDivider()
                    OrderInfoRow(
                        label = stringResource(R.string.purchase_coupon_discount),
                        value = formatNegCurrency(step.couponDiscount),
                    )
                }
                if (step.surplusAmount > 0) {
                    OrderInfoDivider()
                    OrderInfoRow(
                        label = stringResource(R.string.purchase_surplus),
                        value = formatNegCurrency(step.surplusAmount),
                    )
                }
                if (step.balanceAmount > 0) {
                    OrderInfoDivider()
                    OrderInfoRow(
                        label = stringResource(R.string.purchase_balance),
                        value = formatNegCurrency(step.balanceAmount),
                    )
                }
                if (step.refundAmount > 0) {
                    OrderInfoDivider()
                    OrderInfoRow(
                        label = stringResource(R.string.purchase_refund),
                        value = formatPlusCurrency(step.refundAmount),
                    )
                }
                if (step.handlingAmount > 0) {
                    OrderInfoDivider()
                    OrderInfoRow(
                        label = stringResource(R.string.purchase_handling),
                        value = formatCurrency(step.handlingAmount),
                    )
                }
                OrderInfoDivider()
                OrderInfoRow(
                    label = stringResource(R.string.purchase_payable),
                    value = formatCurrency(payAmount),
                    isValueEmphasize = true,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!step.zeroPayable) {
                V5FieldHint(text = stringResource(R.string.purchase_payment_method))
                Spacer(modifier = Modifier.height(8.dp))
                if (step.paymentMethods.isEmpty()) {
                    Text(
                        text = stringResource(R.string.purchase_payment_method_empty),
                        fontSize = 12.5.sp,
                        color = c.text3,
                    )
                } else {
                    PaymentMethodList(
                        methods = step.paymentMethods,
                        selectedId = step.selectedMethod,
                        onSelect = onSelectPayment,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                V5Button(
                    text = stringResource(R.string.back),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.NEUTRAL,
                )
                V5Button(
                    text =
                    if (step.zeroPayable) {
                        stringResource(R.string.order_activate_now)
                    } else {
                        stringResource(
                            R.string.purchase_pay_amount,
                            FormatUtils.balance(payAmount),
                        )
                    },
                    onClick = onConfirmPayment,
                    modifier = Modifier.weight(2f),
                    style = ButtonStyle.PRIMARY,
                    onClickEnabled = step.zeroPayable || step.selectedMethod != null,
                    loading = step.isPaying,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
