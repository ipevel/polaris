// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app

import android.app.Application
import com.github.kr328.clash.common.Global
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.kernel.KernelManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
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
        val clashDir = File(filesDir, "clash").apply { mkdirs() }
        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        listOf("geoip.metadb", "geosite.dat", "ASN.mmdb").forEach { name ->
            val target = File(clashDir, name)
            if (target.exists() && target.lastModified() < updateDate) {
                target.delete()
            }
            if (!target.exists()) {
                assets.open(name).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
