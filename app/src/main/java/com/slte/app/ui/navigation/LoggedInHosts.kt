// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.slte.app.ui.component.rememberToast

@Composable
internal fun GlobalToastHosts(
    purchaseToast: Int?,
    onPurchaseToastShown: () -> Unit,
    mainErrorRes: Int?,
    onMainErrorShown: () -> Unit,
) {
    val toast = rememberToast()

    purchaseToast?.let { resId ->
        LaunchedEffect(resId) {
            toast.show(resId)
            onPurchaseToastShown()
        }
    }
    LaunchedEffect(mainErrorRes) {
        if (mainErrorRes != null) {
            toast.show(mainErrorRes)
            onMainErrorShown()
        }
    }
}
