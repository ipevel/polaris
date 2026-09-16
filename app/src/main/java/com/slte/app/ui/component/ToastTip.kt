package com.slte.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ToastTip(
    message: String?,
    onDismiss: () -> Unit,
) {
    val toast = rememberToast()
    LaunchedEffect(message) {
        if (message != null) {
            toast.show(message)
            onDismiss()
        }
    }
}
