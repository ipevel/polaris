// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.remote.FallbackDns
import com.slte.app.data.repository.AuthRepository
import com.slte.app.di.IoDispatcher
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.awaitTunnelReady
import com.slte.app.kernel.ensureGlobalSelection
import com.slte.app.kernel.fetchPublicIp
import com.slte.app.kernel.runAutoSpeedTest
import com.slte.app.kernel.serverInfo
import com.slte.app.kernel.trafficNow
import com.slte.app.kernel.trafficTotal
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
import kotlinx.coroutines.isActive
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
    private val siteInfoStore: SiteInfoStore,
    private val deviceEnvironment: DeviceEnvironmentSource,
    private val routingStateStore: com.slte.app.kernel.RoutingStateStore,
) : ViewModel() {
    private val _data = MutableStateFlow(DashboardData())
    val data: StateFlow<DashboardData> = _data.asStateFlow()

    private var autoTested = false
    private var tunnelWatchJob: Job? = null
    private var speedWatchJob: Job? = null

    init {

        dataWriter.applyCached(_data)
        dataWriter.seedServerName(_data)
        refresh()
        dataWriter.loadServers(viewModelScope, _data)
        observeKernelState()
        observeProfileLoaded()
        viewModelScope.launch { sampleDeviceEnvironment() }
        viewModelScope.launch { subscriptionUpdater.maybeSilentUpdate(_data, viewModelScope) }

        // 站点名/描述的独立兜底拉取。
        //
        // 此前它只由订阅更新链路写入（SubscriptionUpdater.refreshSiteInfoFromPanel），
        // 且要求 hasPlan 为 true 且 updateProfile() 未失败；登录路径完全不碰站点信息，
        // 订阅头 profile-title 多数面板也不返回。结果是：**首次登录后站点名长期为空**，
        // 面板被网络策略拦截（例如 Cloudflare 拦大陆出口）时更是永远空。
        // 这里在 VM 初始化时直接拉一次（非强制，命中内存缓存则零网络开销），
        // 让顶栏/关于页在不依赖订阅更新的情况下也能拿到站点名。
        viewModelScope.launch {
            applySiteInfo(authRepository.fetchSiteInfo())
        }

        // 站点名称/描述与订阅生命周期挂钩：订阅头（profile-title）+ 面板
        // comm/config 在每次订阅拉取时更新 SiteInfoStore，这里只观察回放
        viewModelScope.launch {
            siteInfoStore.siteInfo.collect { applySiteInfo(it) }
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

    private fun startSpeedWatch() {
        speedWatchJob?.cancel()
        speedWatchJob =
            viewModelScope.launch {
                // 会话流量基线：连接就绪后的第一次采样记录，之后以差值累加；
                // 断开（observeKernelState）负责清零会话字段与历史缓冲
                var baseline: Pair<Long, Long>? = null
                val history = ArrayDeque<Pair<Long, Long>>()
                var tick = 0
                while (isActive) {
                    val total = withContext(ioDispatcher) { kernelProxy.trafficTotal() }
                    // 网速取内核 ticker 维护的秒级 blip，而非本端差分：
                    // delay(1000) 的真实间隔含 AIDL 往返耗时（常为 1.0~1.2s），
                    // 差分会系统性低估速度；blip 由内核 1s ticker 产生，与轮询相位无关
                    val now = withContext(ioDispatcher) { kernelProxy.trafficNow() }
                    if (total != null && now != null) {
                        val base = baseline ?: total.also { baseline = it }
                        history.addLast(now.first to now.second)
                        while (history.size > SPEED_HISTORY_MAX_POINTS) history.removeFirst()
                        _data.update {
                            it.copy(
                                uploadSpeedBps = now.first,
                                downloadSpeedBps = now.second,
                                speedHistory = history.toList(),
                                sessionUploadBytes = (total.first - base.first).coerceAtLeast(0L),
                                sessionDownloadBytes = (total.second - base.second).coerceAtLeast(0L),
                            )
                        }
                        // 顺带周期刷新内存，避免为设备信息常驻第二条采样协程
                        tick++
                        if (tick % MEMORY_SAMPLE_EVERY == 0) {
                            val appMemory = withContext(ioDispatcher) { deviceEnvironment.appMemoryUsageMb() }
                            _data.update {
                                it.copy(appMemoryUsedMb = appMemory)
                            }
                        }
                    }
                    delay(SPEED_WATCH_INTERVAL_MS)
                }
            }
    }

    /** 采样设备内存与本机内网 IP（初始化与连接状态切换时调用）。 */
    private suspend fun sampleDeviceEnvironment() {
        val appMemory = withContext(ioDispatcher) { deviceEnvironment.appMemoryUsageMb() }
        val lan = withContext(ioDispatcher) { deviceEnvironment.lanIpv4() }
        _data.update {
            it.copy(
                appMemoryUsedMb = appMemory,
                lanIp = lan?.takeIf { v -> v.isNotBlank() } ?: it.lanIp,
            )
        }
    }

    private fun observeKernelState() {
        viewModelScope.launch {
            kernelManager.connected.collect { connected ->
                if (!connected) {
                    tunnelWatchJob?.cancel()
                    speedWatchJob?.cancel()
                    _data.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            currentIp = Constants.PLACEHOLDER_DASH,
                            uploadSpeedBps = 0L,
                            downloadSpeedBps = 0L,
                            speedHistory = emptyList(),
                            sessionUploadBytes = 0L,
                            sessionDownloadBytes = 0L,
                            connectedSinceElapsedMs = 0L,
                        )
                    }
                    // 隧道关闭后网络接口集合变化（tun 消失），重查内网 IP
                    sampleDeviceEnvironment()
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

                        _data.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                            )
                        }
                        fallbackDns.clearCache()
                        if (!autoTested) {
                            autoTested = true
                            kernelProxy.runAutoSpeedTest()
                        }
                        refreshKernelInfo()
                        sampleDeviceEnvironment()
                        startSpeedWatch()
                        checkRoutingDegraded()
                    }
            }
        }
    }

    /**
     * 本地分流被内核降级（生成失败 / 面板节点名与保留组名冲突）时提示用户。
     * 内核会写 `routing-degraded.json`，成功应用时删除，所以「文件在」= 当前
     * 处于降级状态（分流开关看起来开着、实际走面板分流）。不做提示的话用户
     * 只会认为「分流开关坏了」。
     */
    private fun checkRoutingDegraded() {
        val degraded = routingStateStore.readDegraded() ?: return
        AppLog.w("Polaris-Main", "本地分流已降级: reason=${degraded.reason}")
        routingStateStore.clearDegraded()
        _data.update { it.copy(errorMessageRes = R.string.routing_degraded_notice) }
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
                // IP 刷新不依赖套餐状态：内核配置装载往往先于订阅数据返回，
                // 若以 hasPlan 为条件会错过唯一的刷新时机，导致 IP/国旗停留在占位符
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
        if (current.isConnecting) {
            // 连接中再点 = 取消连接。否则按钮在整个等待窗口内是死的，
            // 隧道起不来时用户只能杀 App（历史反馈："连不上也关不掉"）
            AppLog.i("Polaris-Main", "toggleConnection: 用户取消连接中")
            tunnelWatchJob?.cancel()
            kernelManager.stopVpn()
            _data.update {
                it.copy(isConnecting = false, isConnected = false, errorMessageRes = null)
            }
            return
        }

        if (!current.hasPlan) return

        AppLog.i("Polaris-Main", "toggleConnection: connected=${current.isConnected} -> ${!current.isConnected}")
        if (current.isConnected) {
            kernelManager.stopVpn()
        } else {
            // 计时起点取点开开关的时刻（而非隧道就绪时刻）：
            // 用户一开代理就开始算运行时长，直到关闭清零
            _data.update {
                it.copy(
                    isConnecting = true,
                    errorMessageRes = null,
                    connectedSinceElapsedMs = SystemClock.elapsedRealtime(),
                )
            }
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
            if (mode == Constants.PROXY_MODE_GLOBAL) {
                // reload 完成后确保 GLOBAL 组选到业务策略组（否则全局模式下
                // GLOBAL 可能停在 DIRECT，表现同"全局不生效"）
                kernelProxy.ensureGlobalSelection()
            }
            // 稍等重载窗口后跑一次自愈核验（ensurePersistedMode 会比对真实
            // 隧道模式，不一致时自动重发变更）；profileLoaded 也会再触发
            delay(RELOAD_SETTLE_MS)
            refreshKernelInfo()
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

        /** 代理模式切换后等待内核 reload 完成的窗口，过后跑自愈核验 */
        const val RELOAD_SETTLE_MS = 3_000L

        /** 实时网速采样间隔：每 1s 读一次内核 blip（秒级字节数）与累计流量 */
        const val SPEED_WATCH_INTERVAL_MS = 1_000L

        /** 速度历史缓冲上限：波形图采样点数（1 点/秒，约 1 分钟滚动窗口） */
        const val SPEED_HISTORY_MAX_POINTS = 60

        /** 连接期间内存采样周期：每 N 个网速采样轮刷新一次（= 5s） */
        const val MEMORY_SAMPLE_EVERY = 5
    }
}
