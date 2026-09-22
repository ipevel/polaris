// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log

/** 监听 TUN 重启广播，通知运行时原地重建 TUN；与 CloseModule 不同，可重复触发。 */
class TunRestartModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_TUN_RESTART)
        }

        for (intent in broadcasts) {
            Log.d("User request tun restart")

            enqueueEvent(Unit)
        }
    }
}
