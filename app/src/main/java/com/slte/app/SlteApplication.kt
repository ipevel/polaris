package com.slte.app

import android.app.Application
import com.github.kr328.clash.common.Global
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.remote.config.CrispConfig
import com.slte.app.data.remote.config.CrispManager
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.kernel.KernelManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltAndroidApp
class SlteApplication : Application() {

    @Inject
    lateinit var crispManager: CrispManager

    @Inject
    lateinit var crispConfig: CrispConfig

    @Inject
    lateinit var kernelManager: KernelManager

    @Inject
    lateinit var remoteConfig: RemoteConfig

    override fun attachBaseContext(base: android.content.Context?) {
        // 只包装主进程：后台进程（clash 内核）无界面，且系统实例化其 Receiver 时要求 base 为 ContextImpl
        val wrapped = if (base != null && getProcessName() == base.packageName) {
            LocaleStore.wrapBase(base)
        } else {
            base
        }
        super.attachBaseContext(wrapped)
        // 内核模块的 Global 依赖 Application，后台进程也会走到这里
        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        // GeoIP/GeoSite 解压放后台线程，内核连接前完成
        Thread { extractGeoFiles() }.start()
        // 远程配置只在主进程拉取：后台进程（内核服务）不消费配置，避免双进程各自写缓存不一致
        if (getProcessName() == packageName) {
            remoteConfig.startFetch()
            // 半开恢复探测：故障 API 地址退避期结束后自动探活恢复，不依赖下次配置刷新
            remoteConfig.startProbeLoop()
        }
        crispManager.init(this, crispConfig)
        // 只在主进程绑定内核服务（:background 进程由系统拉起）
        if (getProcessName() == packageName) {
            kernelManager.bind()
        }
    }

    /** 把 GeoIP/GeoSite 数据从 assets 解压到内核 home 目录 */
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
