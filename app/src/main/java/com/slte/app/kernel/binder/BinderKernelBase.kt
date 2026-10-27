package com.slte.app.kernel.binder

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.TunService
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.util.sendBroadcastSelf
import com.slte.app.kernel.BinderKernelClash
import com.slte.app.kernel.BinderKernelProfiles
import com.slte.app.kernel.api.KernelApi
import com.slte.app.kernel.api.KernelBindingState
import com.slte.app.kernel.api.KernelCapability
import com.slte.app.kernel.api.KernelClash
import com.slte.app.kernel.api.KernelProfiles
import com.slte.app.kernel.api.KernelServerInfo
import com.slte.app.kernel.api.KernelVpnState
import com.slte.app.kernel.api.KernelVpnStatus
import com.slte.app.kernel.api.SelectionGroupType
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Binder 内核基类：生命周期在 [BinderKernelConnection]，本类只做门面/广播/编排。
 * 具体内核由子类声明 type 与 capabilities，共用同一套 kaidl Binder。
 */
abstract class BinderKernelBase(
    protected val context: Context,
) : KernelApi {
    override val capabilities: Set<KernelCapability> =
        setOf(
            KernelCapability.PROXY_GROUPS,
            KernelCapability.URL_TEST,
            KernelCapability.SELECTOR_PATCH,
            KernelCapability.HEALTH_CHECK,
            KernelCapability.PROFILE_MANAGEMENT,
            KernelCapability.TUN_STACK_CONFIG,
        )

    protected val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _vpnConnected = MutableStateFlow(false)
    override val vpnConnected: StateFlow<Boolean> = _vpnConnected.asStateFlow()

    private val _vpnStatus = MutableStateFlow(KernelVpnStatus())
    override val vpnStatus: StateFlow<KernelVpnStatus> = _vpnStatus.asStateFlow()

    private val _profileLoaded = MutableStateFlow(0)
    override val profileLoaded: StateFlow<Int> = _profileLoaded.asStateFlow()

    private val _bindingState = MutableStateFlow(KernelBindingState.DISCONNECTED)
    override val bindingState: StateFlow<KernelBindingState> = _bindingState.asStateFlow()

    private val logObserver =
        object : ILogObserver {
            override fun newItem(log: LogMessage) {
                AppLog.kernel(log)
            }
        }

    private val connection =
        BinderKernelConnection(
            context = context,
            mainScope = mainScope,
            ioScope = ioScope,
            onBindingState = { _bindingState.value = it },
            onVpnStatus = ::updateVpnStatus,
            onProfileLoaded = { _profileLoaded.value += 1 },
        )

    override fun bind() {
        connection.bind()
        ioScope.launch {
            // 绑定是异步的，等 remote 就绪后再订阅日志；失败不影响连接
            val remote = connection.awaitRemote() ?: return@launch
            runCatching { remote.clash()?.setLogObserver(logObserver) }
                .onFailure { AppLog.w("SLTE-Kernel", "setLogObserver failed: ${sanitizeLog(it.message ?: "Unknown")}") }
        }
    }

    override fun vpnRequestIntent(): Intent? = VpnService.prepare(context)

    override fun startVpn() {
        AppLog.i("SLTE-Kernel", "startVpn: 请求启动 TUN (kernel=${type.storageKey}, impl=${this::class.simpleName})")
        // 内核类型随 Intent 传递，避免跨进程偏好同步问题
        context.startForegroundService(
            Intent(context, TunService::class.java).putExtra(EXTRA_KERNEL_TYPE, type.storageKey),
        )
    }

    override fun stopVpn() {
        AppLog.i("SLTE-Kernel", "stopVpn: 请求停止 TUN")
        context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
    }

    override fun notifyProfileChanged(uuid: UUID) {
        context.sendBroadcastSelf(
            Intent(Intents.ACTION_PROFILE_CHANGED)
                .putExtra(Intents.EXTRA_UUID, uuid.toString()),
        )
    }

    override fun notifyOverrideChanged() {
        context.sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
    }

    override suspend fun awaitClash(): KernelClash? = connection.awaitRemote()?.clash()?.let(::BinderKernelClash)

    override suspend fun awaitProfile(): KernelProfiles? = connection.awaitRemote()?.profile()?.let(::BinderKernelProfiles)

    private val selection = SelectionOrchestrator(::awaitClash)

    override suspend fun selectorGroup(): String? {
        val clash = awaitClash() ?: return null
        return runCatching { selection.primaryGroup(clash) }.getOrNull()
    }

    override suspend fun waitForSelectorGroup(): String? = selection.waitForPrimaryGroup()

    override suspend fun selectNode(name: String): Boolean = selection.selectNode(name)

    override suspend fun nodeNames(): List<String> = selection.nodeNames()

    override suspend fun selectionInfo(): KernelServerInfo? = selection.selectionInfo()

    override suspend fun applyAutoSelection(): Boolean = selection.applyGroupOfType("URLTest", "自动", "auto", "url")

    override suspend fun applyFallbackSelection(): Boolean = selection.applyGroupOfType("Fallback", "故障", "fallback")

    override suspend fun ensureGlobalSelection() = selection.ensureGlobal()

    override suspend fun currentNodeOf(groupType: SelectionGroupType): String? = selection.currentOf(groupType)

    override suspend fun triggerHealthCheckAll() = selection.healthCheckAll()

    private fun updateVpnStatus(status: KernelVpnStatus) {
        _vpnStatus.value = status
        _vpnConnected.value = status.state == KernelVpnState.RUNNING
    }

    private companion object {
        const val EXTRA_KERNEL_TYPE = "slte.kernel.type"
        const val VERIFY_ATTEMPTS = 2
        const val VERIFY_DELAY_MS = 120L
    }
}
