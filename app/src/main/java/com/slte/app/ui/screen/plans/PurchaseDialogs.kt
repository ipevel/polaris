package com.slte.app.ui.screen.plans

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.component.LocaleAwareAlertDialog


/** 优惠券错误提示弹窗 */
@Composable
internal fun CouponErrorDialog(
    errorMessageRes: Int,
    onDismiss: () -> Unit
) {
    LocaleAwareAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        title = { Text(stringResource(R.string.purchase_coupon_hint)) },
        text = { Text(stringResource(errorMessageRes)) }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmWarningDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    LocaleAwareAlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.purchase_warning_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(text = stringResource(R.string.purchase_warning_message))
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.purchase_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.purchase_confirm))
            }
        }
    )
}



@Composable
internal fun ExistingOrderErrorDialog(
    errorMessageRes: Int,
    onGoToOrders: () -> Unit,
    onDismiss: () -> Unit
) {
    LocaleAwareAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.purchase_existing_order_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(text = stringResource(R.string.purchase_existing_order_message))
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.purchase_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onGoToOrders) {
                Text(stringResource(R.string.order_pay))
            }
        }
    )
}


@Composable
internal fun OrderCreateErrorDialog(
    errorMessageRes: Int,
    onDismiss: () -> Unit
) {
    LocaleAwareAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.purchase_error_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(text = stringResource(errorMessageRes))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.purchase_confirm))
            }
        }
    )
}
