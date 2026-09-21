package com.slte.app.ui.screen.main

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.remote.FallbackDns
import com.slte.app.data.repository.AuthRepository
import com.slte.app.di.IoDispatcher
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.awaitTunnelReady
import com.slte.app.kernel.ensureGlobalSelection
import com.slte.app.kernel.fetchPublicIp
import com.slte.app.kernel.runAutoSpeedTest
import com.slte.app.kernel.serverInfo
import com.slte.app.kernel.warmUp
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.ErrorMessages
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel
@Inject
constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val kernelManager: KernelManager,
    private val kernelProxy: KernelProxy,
    private val kernelConfig: KernelConfig,
    private val fallbackDns: FallbackDns,
    private val subscriptionUpdater: SubscriptionUpdater,
    private val dataWriter: DashboardDataWriter,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _data = MutableStateFlow(DashboardData())
    val data: StateFlow<DashboardData> = _data.asStateFlow()

    private var autoTested = false

    init {

        dataWriter.applyCached(_data)
        dataWriter.seedServerName(_data)
        refresh()
        dataWriter.loadServers(viewModelScope, _data)
        observeKernelState()
        observeProfileLoaded()
        viewModelScope.launch { subscriptionUpdater.maybeSilentUpdate(_data, viewModelScope) }

        viewModelScope.launch {
            val info = authRepository.fetchSiteInfo()
            val name = info.appName?.takeIf { it.isNotBlank() } ?: ""
            val desc = info.appDescription?.takeIf { it.isNotBlank() } ?: ""
            if (name.isNotEmpty() || desc.isNotEmpty()) {
                _data.update { it.copy(siteName = name, siteDescription = desc) }
            }
        }

        viewModelScope.launch {
            repeat(10) {
                if (kernelProxy.warmUp()) return@launch
                delay(1000)
            }
        }
    }

    private fun observeProfileLoaded() {
        viewModelScope.launch {
            kernelManager.profileLoaded.collect {
                refreshKernelInfo()
            }
        }
    }

    private fun observeKernelState() {
        viewModelScope.launch {
            kernelManager.connected.collect { connected ->
                if (!connected) {
                    _data.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            currentIp = Constants.PLACEHOLDER_DASH,
                        )
                    }
                    return@collect
                }

                // ACTION_CLASH_STARTED 只代表内核进程已启动，TUN 建立与配置装载
                // 是 onCreate 里异步进行的。直接据此置为已连接会造成假连接
                // （界面显示已连接、出口 IP 也变了，但流量并未走代理）。
                if (!kernelProxy.awaitTunnelReady()) {
                    AppLog.w("Polaris-Main", "连接超时：内核未在预期时间内就绪，判定为未连接")
                    _data.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            currentIp = Constants.PLACEHOLDER_DASH,
                            errorMessageRes = R.string.error_vpn_kernel_unavailable,
                        )
                    }
                    return@collect
                }

                _data.update { it.copy(isConnected = true, isConnecting = false) }
                fallbackDns.clearCache()
                if (!autoTested) {
                    autoTested = true
                    viewModelScope.launch {
                        kernelProxy.runAutoSpeedTest()
                        refreshKernelInfo()
                    }
                } else {
                    refreshKernelInfo()
                }
            }
        }
    }

    fun refreshKernelInfo() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                kernelProxy.ensureGlobalSelection()

                kernelProxy.ensurePersistedMode()
                kernelProxy.serverInfo()?.let { info ->
                    _data.update { state ->
                        state.copy(

                            serverName = if (state.hasPlan) info.node ?: state.serverName else state.serverName,
                        )
                    }
                }
                kernelProxy.proxyMode()?.let { mode ->
                    _data.update { it.copy(proxyMode = mode) }
                }
                if (_data.value.hasPlan) {
                    kernelProxy.fetchPublicIp()?.let { info ->
                        _data.update {
                            it.copy(
                                currentIp = info.ip,
                                ipCountryCode = info.countryCode,
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { subscriptionUpdater.refresh(_data) }
    }

    fun updateSubscription() {
        viewModelScope.launch { subscriptionUpdater.updateSubscription(_data, viewModelScope) }
    }

    fun refreshAfterPurchase(tradeNo: String? = null): Job = subscriptionUpdater.refreshAfterPurchase(_data, tradeNo, viewModelScope)

    fun finishPurchaseRefresh() {
        dataWriter.finishPurchaseRefresh(_data)
    }

    fun toggleConnection() {
        val current = _data.value
        if (current.isConnecting) return

        if (!current.hasPlan) return

        AppLog.i("Polaris-Main", "toggleConnection: connected=${current.isConnected} -> ${!current.isConnected}")
        if (current.isConnected) {
            kernelManager.stopVpn()
        } else {
            _data.update { it.copy(isConnecting = true, errorMessageRes = null) }
            viewModelScope.launch {
                try {
                    val profile = kernelConfig.ensureProfile()
                    if (profile == null) {
                        AppLog.w("Polaris-Main", "toggleConnection: ensureProfile 返回 null，内核不可用")
                        _data.update {
                            it.copy(
                                isConnecting = false,
                                errorMessageRes = R.string.error_vpn_kernel_unavailable,
                            )
                        }
                        return@launch
                    }
                    kernelManager.startVpn()
                    watchConnectTimeout()
                } catch (e: Exception) {
                    AppLog.w("Polaris-Main", "toggleConnection: 启动失败 ${sanitizeLog(e.message ?: "Unknown")}")
                    _data.update {
                        it.copy(
                            isConnecting = false,
                            errorMessageRes = ErrorMessages.networkError(),
                        )
                    }
                }
            }
        }
    }

    fun vpnRequestIntent(): Intent? = kernelManager.vpnRequestIntent()

    fun setProxyMode(mode: String) {
        _data.update { it.copy(proxyMode = mode) }
        viewModelScope.launch {
            kernelProxy.setProxyMode(mode)
        }
    }

    fun clearError() {
        _data.update { it.copy(errorMessageRes = null) }
    }

    fun cancelUpdating() {
        _data.update { it.copy(isUpdating = false) }
    }

    fun onVpnPermissionDenied() {
        AppLog.w("Polaris-Main", "VPN 授权被拒绝，连接未建立")
        _data.update {
            it.copy(isConnecting = false, errorMessageRes = R.string.error_vpn_permission_denied)
        }
    }

    /**
     * 连接看门狗：TunService 若始终不发状态广播，界面会永远停在"连接中"。
     */
    private fun watchConnectTimeout() {
        viewModelScope.launch {
            delay(CONNECT_WATCHDOG_MS)
            if (_data.value.isConnecting) {
                AppLog.w("Polaris-Main", "连接看门狗触发：${CONNECT_WATCHDOG_MS}ms 内未完成连接")
                _data.update {
                    it.copy(isConnecting = false, errorMessageRes = R.string.error_vpn_kernel_unavailable)
                }
            }
        }
    }

    private companion object {
        const val CONNECT_WATCHDOG_MS = 20_000L
    }
}
