// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.component.formatNegCurrency
import com.slte.app.ui.component.formatPlusCurrency
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@Composable
internal fun OrderPaymentSheet(
    step: PurchaseStep.OrderPayment,
    onSelectPayment: (Int) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    SlteSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.purchase_order_info),
    ) {
        val payAmount = step.payAmount

        if (step.isLoading) {
            LottieLoadingIcon(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(Dimens.icon.lg),
            )
            Spacer(modifier = Modifier.height(Dimens.gap.xl))
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SlteRadii.inner),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimens.gap.lg,
                            vertical = Dimens.gap.md,
                        ),
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
            }

            Spacer(modifier = Modifier.height(Dimens.gap.lg))

            if (!step.zeroPayable) {
                Text(
                    text = stringResource(R.string.purchase_payment_method),
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                if (step.paymentMethods.isEmpty()) {
                    Text(
                        text = stringResource(R.string.purchase_payment_method_empty),
                        style = SlteType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PaymentMethodList(
                        methods = step.paymentMethods,
                        selectedId = step.selectedMethod,
                        onSelect = onSelectPayment,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
            ) {
                SlteButton(
                    text = stringResource(R.string.back),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    style = SlteButtonStyle.Neutral,
                )
                SlteButton(
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
                    style = SlteButtonStyle.Primary,
                    enabled = !step.isPaying && (step.zeroPayable || step.selectedMethod != null),
                    loading = step.isPaying,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.xl))
        }
    }
}
