package com.slte.app.ui.screen.main

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.local.ThemeMode
import com.slte.app.data.local.ThemePreference
import com.slte.app.data.remote.FallbackDns
import com.slte.app.data.repository.AuthRepository
import com.slte.app.di.IoDispatcher
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.SiteInfo
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
    private val themePreference: ThemePreference,
) : ViewModel() {
    private val _data = MutableStateFlow(DashboardData())
    val data: StateFlow<DashboardData> = _data.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    fun toggleDarkMode() {
        themePreference.toggleDarkMode()
    }

    private var autoTested = false
    private var tunnelWatchJob: Job? = null

    init {

        dataWriter.applyCached(_data)
        dataWriter.seedServerName(_data)
        refresh()
        dataWriter.loadServers(viewModelScope, _data)
        observeKernelState()
        observeProfileLoaded()
        viewModelScope.launch { subscriptionUpdater.maybeSilentUpdate(_data, viewModelScope) }

        viewModelScope.launch {
            val info = authRepository.fetchSiteInfo(force = true)
            applySiteInfo(info)
        }

        // 登录成功后面板地址/凭据才可用，此时强制重新拉取站点名称与描述，
        // 保证顶栏与关于页显示的是面板下发的动态站点信息而非缓存/默认值
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.LoggedIn) {
                    val info = authRepository.fetchSiteInfo(force = true)
                    applySiteInfo(info)
                }
            }
        }

        viewModelScope.launch {
            repeat(10) {
                if (kernelProxy.warmUp()) return@launch
                delay(1000)
            }
        }
    }

    private fun applySiteInfo(info: SiteInfo) {
        val name = info.appName?.takeIf { it.isNotBlank() } ?: ""
        val desc = info.appDescription?.takeIf { it.isNotBlank() } ?: ""
        if (name.isNotEmpty() || desc.isNotEmpty()) {
            _data.update { it.copy(siteName = name, siteDescription = desc) }
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
                    tunnelWatchJob?.cancel()
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
                // 是 onCreate 里异步进行的，大订阅时可能远超 12 秒。此前一次性
                // awaitTunnelReady 超时即放弃，而 connected 是 StateFlow（true 期间
                // 不会再发射），隧道真正就绪后 UI 永远不会更新——表现为"连接中"
                // 卡很久、就绪了界面也不变。这里改为：只要内核保持连接就持续等待
                // （上限 TUNNEL_WATCH_MAX_MS），期间界面保持"连接中"，就绪立即点亮。
                tunnelWatchJob?.cancel()
                tunnelWatchJob =
                    viewModelScope.launch {
                        _data.update { it.copy(isConnecting = true, errorMessageRes = null) }
                        var ready = false
                        // 上限 ≈ MAX_POLLS × (就绪探测窗口 + 轮询间隔)；生产环境约 5 分钟。
                        // 与 awaitTunnelReady 一样用 delay 表达超时，虚拟时钟测试不热旋。
                        repeat(TUNNEL_WATCH_MAX_POLLS) {
                            if (!kernelManager.connected.value) return@launch
                            if (kernelProxy.awaitTunnelReady(timeoutMs = TUNNEL_WATCH_POLL_MS)) {
                                ready = true
                                return@repeat
                            }
                            delay(TUNNEL_WATCH_POLL_MS)
                        }
                        if (!ready) {
                            if (kernelManager.connected.value) {
                                AppLog.w("Polaris-Main", "连接看门狗：隧道 ${TUNNEL_WATCH_MAX_POLLS} 轮未就绪")
                                _data.update {
                                    it.copy(
                                        isConnecting = false,
                                        errorMessageRes = R.string.error_vpn_kernel_unavailable,
                                    )
                                }
                            }
                            return@launch
                        }

                        _data.update { it.copy(isConnected = true, isConnecting = false) }
                        fallbackDns.clearCache()
                        if (!autoTested) {
                            autoTested = true
                            kernelProxy.runAutoSpeedTest()
                        }
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

    private companion object {
        /** 隧道就绪等待轮数：30 轮 × 10s ≈ 5 分钟上限（大订阅首连可能耗时数分钟） */
        const val TUNNEL_WATCH_MAX_POLLS = 30

        /** 每轮就绪探测窗口（awaitTunnelReady 内部 300ms 轮询）+ 轮间间隔 */
        const val TUNNEL_WATCH_POLL_MS = 5_000L
    }
}
