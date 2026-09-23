// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

/**
 * 设备侧环境信息源：app 进程内存占用与本机内网 IPv4。
 * 独立成类便于纯 JVM 单测注入（避免 ViewModel 直接依赖 Context）。
 */
class DeviceEnvironmentSource
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    /** @return 当前应用进程占用内存（PSS，MB），读取失败返回 0 */
    fun appMemoryUsageMb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        return try {
            val infos = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            val totalPssKb = infos?.firstOrNull()?.totalPss ?: 0
            (totalPssKb / KB_PER_MB).coerceAtLeast(0)
        } catch (e: Exception) {
            AppLog.w(TAG, "appMemoryUsageMb: ${sanitizeLog(e.message ?: "Unknown")}")
            0
        }
    }

    /**
     * @return 第一个已启用、非回环的 IPv4 地址（通常为 Wi-Fi 内网 IP），无则 null。
     * 隧道开启时内核会创建 tun 虚拟网卡，其地址并非真实局域网 IP，这里显式跳过。
     */
    fun lanIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val name = ni.name?.lowercase() ?: continue
                if (ni.isLoopback || !ni.isUp) continue
                if (TUNNEL_IFACE_PREFIXES.any { name.startsWith(it) }) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
            null
        } catch (e: Exception) {
            AppLog.w(TAG, "lanIpv4: ${sanitizeLog(e.message ?: "Unknown")}")
            null
        }
    }

    private companion object {
        const val TAG = "Polaris-Main"

        const val KB_PER_MB = 1024

        /** 虚拟隧道网卡前缀：这些接口上的 IPv4 不是局域网地址 */
        val TUNNEL_IFACE_PREFIXES = listOf("tun", "tap", "ppp")
    }
}
