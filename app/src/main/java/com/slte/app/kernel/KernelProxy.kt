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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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

        val mode =
            clash.queryOverride(Clash.OverrideSlot.Persist).mode
                ?: clash.queryTunnelState().mode
        when (mode) {
            TunnelState.Mode.Global -> Constants.PROXY_MODE_GLOBAL
            TunnelState.Mode.Rule -> Constants.DEFAULT_PROXY_MODE
            TunnelState.Mode.Direct -> Constants.PROXY_MODE_DIRECT
            TunnelState.Mode.Script -> Constants.PROXY_MODE_SCRIPT
        }
    }

    suspend fun setProxyMode(mode: String) = safe(Unit, "setProxyMode") {
        AppLog.d("SLTE-Kernel", "setProxyMode: $mode")

        modePrefs.edit { putString(KEY_PROXY_MODE, mode) }
        val clash = manager.clash()
        if (clash == null) {
            AppLog.d("SLTE-Kernel", "setProxyMode: clash=null，已本地保存，待内核就绪后同步")
            return@safe
        }
        val override =
            clash.queryOverride(Clash.OverrideSlot.Persist).apply {
                this.mode = tunnelModeOf(mode)
            }
        clash.patchOverride(Clash.OverrideSlot.Persist, override)
        AppLog.d("SLTE-Kernel", "setProxyMode: override written, sending broadcast")
        context.sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
    }

    suspend fun ensurePersistedMode() = safe(Unit, "ensurePersistedMode") {
        val clash = manager.clash() ?: return@safe
        val saved = modePrefs.getString(KEY_PROXY_MODE, null) ?: return@safe
        val target = tunnelModeOf(saved)
        val current = clash.queryOverride(Clash.OverrideSlot.Persist).mode
        if (current != target) {
            val override =
                clash.queryOverride(Clash.OverrideSlot.Persist).apply {
                    this.mode = target
                }
            clash.patchOverride(Clash.OverrideSlot.Persist, override)
            context.sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
            AppLog.d("SLTE-Kernel", "ensurePersistedMode: synced $saved")
        }
    }

    private fun tunnelModeOf(mode: String): TunnelState.Mode = when (mode) {
        Constants.PROXY_MODE_GLOBAL -> TunnelState.Mode.Global
        Constants.PROXY_MODE_DIRECT -> TunnelState.Mode.Direct
        Constants.PROXY_MODE_SCRIPT -> TunnelState.Mode.Script
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
            AppLog.w("SLTE-Kernel", "setTunStack: clash=null，已本地保存，待内核就绪后同步")
            return@safe
        }
        clash.setTunStackMode(normalized)
        AppLog.d("SLTE-Kernel", "setTunStack: $normalized")
    }

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
