// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.core.bridge

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.log.Log as AppLog
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import java.io.File

@Keep
object Bridge {
    external fun nativeReset()
    external fun nativeForceGc()
    external fun nativeSuspend(suspend: Boolean)
    external fun nativeQueryTunnelState(): String
    external fun nativeQueryTrafficNow(): Long
    external fun nativeQueryTrafficTotal(): Long
    external fun nativeNotifyDnsChanged(dnsList: String)
    external fun nativeNotifyTimeZoneChanged(name: String, offset: Int)
    external fun nativeNotifyInstalledAppChanged(uidList: String)
    external fun nativeStartTun(fd: Int, stack: String, gateway: String, portal: String, dns: String, cb: TunInterface): Int
    external fun nativeStopTun()
    external fun nativeStartHttp(listenAt: String): String?
    external fun nativeStopHttp()
    external fun nativeQueryGroupNames(excludeNotSelectable: Boolean): String
    external fun nativeQueryGroup(name: String, sort: String): String?
    external fun nativeHealthCheck(completable: CompletableDeferred<Unit>, name: String)
    external fun nativeHealthCheckAll()
    external fun nativeUrlTest(name: String, timeoutMs: Int): String?
    external fun nativePatchSelector(selector: String, name: String): Boolean
    external fun nativeFetchAndValid(
        completable: FetchCallback,
        path: String,
        url: String,
        force: Boolean
    )

    external fun nativeLoad(completable: CompletableDeferred<Unit>, path: String)
    external fun nativeQueryProviders(): String
    external fun nativeUpdateProvider(
        completable: CompletableDeferred<Unit>,
        type: String,
        name: String
    )

    external fun nativeReadOverride(slot: Int): String
    external fun nativeWriteOverride(slot: Int, content: String)
    external fun nativeClearOverride(slot: Int)
    external fun nativeQueryConfiguration(): String
    external fun nativeSubscribeLogcat(callback: LogcatInterface)
    external fun nativeCoreVersion(): String

    external fun nativeSetAgeSecretKey(key: String?)
    external fun nativeGenX25519KeyPair(): String?
    external fun nativeGenHybridKeyPair(): String?
    external fun nativeVeritySecretKeys(secretKeys: String): Boolean
    external fun nativeToPublicKeys(secretKeys: String): String?
    external fun nativeVerityPublicKeys(publicKeys: String): Boolean

    private external fun nativeInit(home: String, versionName: String, sdkVersion: Int)

    /** 选中的内核实现；必须在 Application.onCreate 最早处设置。 */
    @Volatile
    var kernelVariant: String = KERNEL_STANDARD
        private set

    private var loaded = false

    /** 选择内核：加载对应的原生库。切换内核后需重启进程。 */
    fun selectKernel(variant: String) {
        if (loaded) return
        AppLog.i("Bridge.selectKernel: $variant")
        kernelVariant = variant
        val smart = variant == KERNEL_SMART
        if (smart) {
            val ok =
                runCatching {
                    System.loadLibrary("clash_smart")
                    System.loadLibrary("bridge_smart")
                    true
                }.getOrDefault(false)
            if (!ok) {
                AppLog.w("Bridge: smart libs unavailable, fallback to standard")
                kernelVariant = KERNEL_STANDARD
                System.loadLibrary("bridge")
            } else {
                AppLog.i("Bridge: smart kernel loaded")
            }
        } else {
            System.loadLibrary("bridge")
        }
        loaded = true
    }

    /** 实际加载的内核变体（可能与用户选择不同：smart 库缺失时回退标准版）。 */
    fun activeVariant(): String = kernelVariant

    private fun ensureLoaded() {
        if (!loaded) selectKernel(kernelVariant)
    }

    /** 初始化原生内核。Application 在选定内核后调用；重复调用无副作用。 */
    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            ensureLoaded()
            val ctx = Global.application

            ParcelFileDescriptor.open(File(ctx.packageCodePath), ParcelFileDescriptor.MODE_READ_ONLY)
                .detachFd()

            val home = ctx.filesDir.resolve("clash").apply { mkdirs() }.absolutePath
            val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
            val sdkVersion = Build.VERSION.SDK_INT

            AppLog.d("Home = $home")

            nativeInit(home, versionName, sdkVersion)
            initialized = true
        }
    }

    @Volatile
    private var initialized = false
}

internal const val KERNEL_STANDARD = "mihomo"
internal const val KERNEL_SMART = "mihomo_smart"
