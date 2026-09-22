// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.util.sendBroadcastSelf
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

enum class SelectionType { AUTO, FALLBACK, MANUAL }

data class KernelServerInfo(
    val selection: SelectionType?,
    val node: String?,
)

data class IpGeoInfo(
    val ip: String,
    val ipv6: String? = null,
    val countryCode: String?,
)

@Singleton
class KernelProxy
@Inject
constructor(
    internal val faultReporter: KernelFaultReporter,
    internal val manager: KernelManager,
    internal val config: KernelConfig,
    internal val speedResultStore: SpeedResultStore,
    internal val geoIpResolver: GeoIpResolver,
    @ApplicationContext internal val context: Context,
) {
    internal val modePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal suspend fun <T> safe(
        default: T,
        operation: String,
        tag: String = DEFAULT_FAULT_TAG,
        block: suspend () -> T,
    ): T = faultReporter.guard(default, operation, tag, block)

    suspend fun coreVersion(): String? = safe(null, "coreVersion") {
        manager.clash()?.coreVersion()
    }

    suspend fun proxyMode(): String? = safe(null, "proxyMode") {
        val clash = manager.clash() ?: return@safe null

        // 优先读内核真实隧道模式：override 只是"期望值"，reload 竞态/失败时
        // 两者会不一致——若 override 优先，UI 显示用户所选而内核仍是旧模式，
        // 表现为"全局不生效"却无任何感知
        val mode =
            runCatching { clash.queryTunnelState().mode }.getOrNull()
                ?: clash.queryOverride(Clash.OverrideSlot.Persist).mode
        when (mode) {
            TunnelState.Mode.Global -> Constants.PROXY_MODE_GLOBAL
            TunnelState.Mode.Rule -> Constants.DEFAULT_PROXY_MODE
            TunnelState.Mode.Direct -> Constants.PROXY_MODE_DIRECT
            TunnelState.Mode.Script -> Constants.PROXY_MODE_SCRIPT
            null -> null
        }
    }

    suspend fun setProxyMode(mode: String) = safe(Unit, "setProxyMode") {
        AppLog.d("Polaris-Kernel", "setProxyMode: $mode")

        modePrefs.edit { putString(KEY_PROXY_MODE, mode) }
        val clash = manager.clash()
        if (clash == null) {
            // 内核未运行时直接写 override.json 到磁盘，
            // 保证 VPN 连接后首次 Clash.load() 即能读到用户选择的模式
            writePersistOverrideModeToDisk(tunnelModeOf(mode))
            AppLog.d("Polaris-Kernel", "setProxyMode: clash=null，override.json 已写磁盘，待内核就绪后生效")
            return@safe
        }
        val override =
            clash.queryOverride(Clash.OverrideSlot.Persist).apply {
                this.mode = tunnelModeOf(mode)
            }
        clash.patchOverride(Clash.OverrideSlot.Persist, override)
        AppLog.d("Polaris-Kernel", "setProxyMode: override written, sending broadcast")
    }

    suspend fun ensurePersistedMode() = safe(Unit, "ensurePersistedMode") {
        val clash = manager.clash() ?: return@safe
        val saved = modePrefs.getString(KEY_PROXY_MODE, null) ?: return@safe
        val target = tunnelModeOf(saved)
        val overrideNow = clash.queryOverride(Clash.OverrideSlot.Persist)
        if (overrideNow.mode != target) {
            // override 落后于用户选择（首连/竞态）：补写并触发重载
            clash.patchOverride(Clash.OverrideSlot.Persist, overrideNow.apply { mode = target })
            AppLog.d("Polaris-Kernel", "ensurePersistedMode: override synced $saved")
        }
        // 核验真实隧道模式：override 已正确但隧道未跟上（reload 失败/竞态）
        // 时重发一次变更广播触发重载。没有这一步，用户切到全局后内核可能
        // 永远停在规则模式且 UI 无感知（历史"全局代理不生效"）。
        val tunnel = runCatching { clash.queryTunnelState().mode }.getOrNull()
        if (tunnel != null && tunnel != target) {
            AppLog.w(
                "Polaris-Kernel",
                "ensurePersistedMode: tunnel=$tunnel != target=$target，重发 override 变更触发重载",
            )
            context.sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
        }
    }

    private fun tunnelModeOf(mode: String): TunnelState.Mode = when (mode) {
        Constants.PROXY_MODE_GLOBAL -> TunnelState.Mode.Global
        Constants.PROXY_MODE_DIRECT -> TunnelState.Mode.Direct
        Constants.PROXY_MODE_SCRIPT -> {
            // Go 内核 ModeMapping 不含 "script"，若写入会导致 patchOverride
            // 解析失败并静默丢弃整个 override 文件；降级为 Rule
            AppLog.w("Polaris-Kernel", "tunnelModeOf: Go 内核不支持 script 模式，降级为 rule")
            TunnelState.Mode.Rule
        }
        else -> TunnelState.Mode.Rule
    }

    suspend fun tunStackMode(): String = safe(DEFAULT_TUN_STACK, "tunStackMode") {
        val clash = manager.clash()
        if (clash != null) {
            val current = clash.tunStackMode()
            modePrefs.edit { putString(KEY_TUN_STACK, current) }
            return@safe current
        }
        modePrefs.getString(KEY_TUN_STACK, null) ?: DEFAULT_TUN_STACK
    }

    suspend fun setTunStack(mode: String) = safe(Unit, "setTunStack") {
        val normalized = if (mode in TUN_STACK_VALUES) mode else DEFAULT_TUN_STACK
        modePrefs.edit { putString(KEY_TUN_STACK, normalized) }
        val clash = manager.clash()
        if (clash == null) {
            AppLog.w("Polaris-Kernel", "setTunStack: clash=null，已本地保存，待内核就绪后同步")
            return@safe
        }
        clash.setTunStackMode(normalized)
        AppLog.d("Polaris-Kernel", "setTunStack: $normalized")
    }

    /**
     * 将 Persist override 的 mode 字段直接写入磁盘文件。
     * 用于内核未运行时（clash==null）也能确保 VPN 连接后首次 Clash.load()
     * 读到用户选择的代理模式，避免回退到 Rule。
     */
    private fun writePersistOverrideModeToDisk(mode: TunnelState.Mode) {
        try {
            val file = overrideJsonFile
            val existing = if (file.exists()) {
                runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(file.readText()).jsonObject
                }.getOrNull() ?: JsonObject(emptyMap())
            } else {
                JsonObject(emptyMap())
            }
            val modeSerialName = when (mode) {
                TunnelState.Mode.Global -> "global"
                TunnelState.Mode.Rule -> "rule"
                TunnelState.Mode.Direct -> "direct"
                // Script 已在 tunnelModeOf 中降级，此处不会出现
                else -> "rule"
            }
            val updated = JsonObject(existing + ("mode" to JsonPrimitive(modeSerialName)))
            file.parentFile?.mkdirs()
            file.writeText(updated.toString())
        } catch (e: Exception) {
            AppLog.w("Polaris-Kernel", "writePersistOverrideModeToDisk: 写入失败: ${sanitizeLog(e.message ?: "Unknown")}")
        }
    }

    private val overrideJsonFile: File
        get() = File(context.filesDir, "clash/override.json")

    internal val ipClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "slte_kernel_mode"
        private const val KEY_PROXY_MODE = "proxy_mode"
        private const val KEY_TUN_STACK = "tun_stack"

        private const val DEFAULT_TUN_STACK = "system"
        private val TUN_STACK_VALUES = setOf("system", "gvisor", "mixed")

        internal const val IPIFY_V4_URL = "https://api.ipify.org"
        internal const val IPIFY_V6_URL = "https://api6.ipify.org"
    }
}
