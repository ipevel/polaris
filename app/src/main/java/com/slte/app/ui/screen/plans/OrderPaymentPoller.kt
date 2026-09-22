// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.data.repository.OrderRepository
import com.slte.app.utils.AppLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrderPaymentPoller
@Inject
constructor(
    private val orderRepository: OrderRepository,
) {
    private var pollJob: Job? = null
    private var pollingTradeNo: String? = null

    fun start(
        scope: CoroutineScope,
        tradeNo: String,
        onCompleted: (String) -> Unit,
    ) {
        if (pollJob?.isActive == true && pollingTradeNo == tradeNo) return
        AppLog.d(TAG, "startOrderPolling: tradeNo=$tradeNo")
        pollJob?.cancel()
        pollingTradeNo = tradeNo
        pollJob =
            scope.launch {
                var elapsed = 0L
                while (elapsed < POLL_TIMEOUT_MS) {
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                    val status = orderRepository.getOrderDetail(tradeNo).getOrNull()?.status
                    val outcome = pollOutcome(status)
                    if (outcome != null) {
                        AppLog.i(TAG, "poll 结束: tradeNo=$tradeNo status=$status")

                        if (outcome == PollOutcome.COMPLETED) {
                            onCompleted(tradeNo)
                        }
                        return@launch
                    }
                }
            }
    }

    fun stop() {
        pollJob?.cancel()
        pollingTradeNo = null
    }

    private companion object {
        const val TAG = "Polaris-Purchase"

        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_TIMEOUT_MS = 300_000L
    }
}
