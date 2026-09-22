// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：协程周期 ticker 通道工具，内核定时刷新用

package com.github.kr328.clash.common.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun CoroutineScope.ticker(period: Long): Channel<Long> {
    val channel = Channel<Long>(Channel.RENDEZVOUS)

    launch {
        try {
            while (isActive) {
                channel.send(System.currentTimeMillis())

                delay(period)
            }
        } catch (ignored: Exception) {

        }
    }

    return channel
}