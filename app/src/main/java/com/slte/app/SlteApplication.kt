// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app

import android.app.Application
import android.content.Intent
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.util.sendBroadcastSelf
import com.slte.app.data.geo.RuleResources
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.kernel.KernelManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class SlteApplication : Application() {
    @Inject
    lateinit var kernelManager: KernelManager

    @Inject
    lateinit var remoteConfig: RemoteConfig

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(base: android.content.Context?) {
        val wrapped =
            if (base != null && getProcessName() == base.packageName) {
                LocaleStore.wrapBase(base)
            } else {
                base
            }
        super.attachBaseContext(wrapped)
        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        Thread { extractGeoFiles() }.start()
        if (getProcessName() == packageName) {
            remoteConfig.startFetch()
            remoteConfig.startProbeLoop()
            kernelManager.bind()
        }
    }

    private fun extractGeoFiles() {
        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        // 随包 geodata 换了就重铺，并把同步标记一并清掉（文件与标记同生共死）。
        RuleResources.seedGeoFiles(File(filesDir, "clash"), updateDate) { name ->
            runCatching { assets.open(name) }.getOrNull()
        }
        // 缺失的已从 assets 补齐；这里再按 12 小时节拍从上游刷新规则清单与 geodata，失败不影响启动。
        val result = RuleResources.refresh(this)
        // 规则清单变了要热加载（内核会重读本地 provider），不必断线重连；
        // 只有 geodata 变了则不需要，它的读取路径本身就在下次 load 时生效。
        if (result.updated.any { it.endsWith(".yaml") }) requestKernelReload()
    }

    private fun requestKernelReload() {
        sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
    }
}
