package com.slte.app.ui.screen.plans

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

@Composable
fun PurchaseFlow(
    step: PurchaseStep,
    onSelectPeriod: (String) -> Unit,
    onUpdateCoupon: (String) -> Unit,
    onVerifyCoupon: () -> Unit,
    onConfirmOrder: () -> Unit,
    onCancelWarning: () -> Unit,
    onConfirmWarning: () -> Unit,
    onSelectPayment: (Int) -> Unit,
    onConfirmPayment: () -> Unit,
    onPaymentReturn: () -> Unit,
    onDismiss: () -> Unit,
    onGoToOrders: () -> Unit = onDismiss,
) {
    val context = LocalContext.current

    when (step) {
        is PurchaseStep.SelectPeriod -> {
            SelectPeriodSheet(
                step = step,
                onSelectPeriod = onSelectPeriod,
                onUpdateCoupon = onUpdateCoupon,
                onVerifyCoupon = onVerifyCoupon,
                onConfirmOrder = onConfirmOrder,
                onDismiss = onDismiss,
            )
            if (step.showWarning) {
                ConfirmWarningDialog(
                    onConfirm = onConfirmWarning,
                    onCancel = onCancelWarning,
                )
            }
        }
        is PurchaseStep.OrderPayment -> {
            OrderPaymentSheet(
                step = step,
                onSelectPayment = onSelectPayment,
                onConfirmPayment = onConfirmPayment,
                onDismiss = onDismiss,
            )
        }
        is PurchaseStep.Paying -> {
            val browserLaunched = remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, step.redirectUrl) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> browserLaunched.value = true
                            Lifecycle.Event.ON_RESUME -> {
                                if (browserLaunched.value) onPaymentReturn()
                            }
                            else -> {}
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            androidx.compose.runtime.LaunchedEffect(step.redirectUrl) {
                try {
                    val uri = step.redirectUrl.toUri()
                    if (uri.scheme == "https" || uri.scheme == "http") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    browserLaunched.value = true
                } catch (e: Exception) {
                    AppLog.w("Polaris-Purchase", "打开支付页面失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    onPaymentReturn()
                }
            }
        }
        is PurchaseStep.ExistingOrderError -> {
            ExistingOrderErrorDialog(
                errorMessageRes = step.errorMessageRes,
                onGoToOrders = onGoToOrders,
                onDismiss = onDismiss,
            )
        }
        is PurchaseStep.OrderCreateError -> {
            OrderCreateErrorDialog(
                errorMessageRes = step.errorMessageRes,
                onDismiss = onDismiss,
            )
        }
        is PurchaseStep.Idle -> {}
    }
}
