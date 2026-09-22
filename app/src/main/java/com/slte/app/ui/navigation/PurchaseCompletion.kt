// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun PurchaseCompletionHost(
    viewModels: LoggedInViewModels,
    pageStack: SnapshotStateList<Page>,
    onPendingPaymentTradeNo: (String?) -> Unit,
) {
    val createdTradeNo by viewModels.purchase.createdTradeNo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    suspend fun refreshAfterPurchase(tradeNo: String?) {
        viewModels.purchase.goBack()
        viewModels.orders.refresh()
        viewModels.profile.refresh()
        pageStack.clear()
        pageStack.add(Page.Dashboard)
        viewModels.main.refreshAfterPurchase(tradeNo).join()
        viewModels.server.refreshNodesForPurchase()
        viewModels.main.finishPurchaseRefresh()
    }

    LaunchedEffect(createdTradeNo) {
        val tradeNo = createdTradeNo
        if (tradeNo != null) {
            viewModels.orders.refresh()
            if (pageStack.last() != Page.Orders) pageStack.add(Page.Orders)
            viewModels.purchase.clearCreatedTradeNo()
            onPendingPaymentTradeNo(tradeNo)
        }
    }

    LaunchedEffect(Unit) {
        viewModels.purchase.paymentCompleted.collect { tradeNo ->
            viewModels.purchase.ackPaymentCompleted()
            scope.launch { refreshAfterPurchase(tradeNo) }
        }
    }
}
