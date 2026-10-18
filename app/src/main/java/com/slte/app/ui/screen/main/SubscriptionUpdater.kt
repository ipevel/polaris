// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import com.slte.app.R
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.repository.AuthRepository
import com.slte.app.data.repository.OrderRepository
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.isOrderActivated
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.ProfileUpdateResult
import com.slte.app.kernel.speedTestUntilReady
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.ErrorMessages
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionUpdater
@Inject
constructor(
    private val subscribeRepository: SubscribeRepository,
    private val authRepository: AuthRepository,
    private val siteInfoStore: SiteInfoStore,
    private val kernelConfig: KernelConfig,
    private val serverRepository: ServerRepository,
    private val kernelProxy: KernelProxy,
    private val kernelManager: KernelManager,
    private val orderRepository: OrderRepository,
    private val dataWriter: DashboardDataWriter,
) {

    private val updateMutex = Mutex()

    internal var nowMillis: () -> Long = System::currentTimeMillis

    private suspend fun tryAcquireUpdateSlot(): Boolean = updateMutex.tryLock()

    suspend fun updateSubscription(
        data: MutableStateFlow<DashboardData>,
        scope: CoroutineScope,
    ) {
        if (!tryAcquireUpdateSlot()) return
        try {
            if (!data.value.hasPlan) return
            data.update { it.copy(isUpdating = true) }
            subscribeRepository.fetchSubscribeInfo(force = true).fold(
                onSuccess = {
                    val kernelResult = updateProfileWithFreshLink()
                    val kernelOk = kernelResult != ProfileUpdateResult.FAILED
                    if (kernelOk) {
                        refreshSiteInfoFromPanel()
                        // 不清缓存，强制拉新节点原地覆盖：刷新期间旧列表保持可见
                        dataWriter.loadServers(scope, data, force = true)

                        scope.launch { autoSpeedTestAfterUpdate(configChanged = profileReloaded(kernelResult)) }
                    }
                    dataWriter.applySubscribeInfo(
                        data,
                        it,
                        errorMessageRes =
                        if (kernelOk) {
                            R.string.dashboard_refresh_done
                        } else {
                            R.string.api_error_subscribe_info
                        },
                    )
                },
                onFailure = { e ->
                    val resId =
                        ErrorMessages.forSubscribe(e)
                    data.update { it.copy(isUpdating = false, errorMessageRes = resId) }
                },
            )
        } finally {
            updateMutex.unlock()
        }
    }

    private suspend fun updateProfileWithFreshLink(): ProfileUpdateResult {
        val first = kernelConfig.updateProfile()
        if (first != ProfileUpdateResult.FAILED) return first
        subscribeRepository.fetchSubscribeInfo(force = true)
        return kernelConfig.updateProfile()
    }

    suspend fun maybeSilentUpdate(
        data: MutableStateFlow<DashboardData>,
        scope: CoroutineScope,
    ) {
        if (!tryAcquireUpdateSlot()) return
        try {
            subscribeRepository.fetchSubscribeInfo(force = true).fold(
                onSuccess = { info ->

                    if (!info.hasPlan) return@fold
                    val result = kernelConfig.updateProfile()
                    if (result != ProfileUpdateResult.FAILED) {
                        refreshSiteInfoFromPanel()
                        dataWriter.loadServers(scope, data, force = true)
                        scope.launch { autoSpeedTestAfterUpdate(configChanged = profileReloaded(result)) }
                        dataWriter.applySubscribeInfo(data, info, errorMessageRes = null)
                    }
                },
                onFailure = { e ->

                    AppLog.w("Polaris-Main", "silent subscription update failed: ${sanitizeLog(e.message ?: "Unknown")}")
                },
            )
        } finally {
            updateMutex.unlock()
        }
    }

    fun refreshAfterPurchase(
        data: MutableStateFlow<DashboardData>,
        tradeNo: String? = null,
        scope: CoroutineScope,
    ): Job = scope.launch {
        updateMutex.withLock {
            data.update { it.copy(isUpdating = true) }
            val deadline = nowMillis() + PURCHASE_REFRESH_TIMEOUT_MS
            var info = subscribeRepository.fetchSubscribeInfo(force = true).getOrNull()
            var activated = true
            if (tradeNo != null) {
                activated = false
                while (nowMillis() < deadline && !activated) {
                    activated =
                        orderRepository
                            .getOrderDetail(tradeNo)
                            .getOrNull()
                            ?.status
                            ?.let(::isOrderActivated) == true
                    if (!activated) {
                        delay(3000)
                        info = subscribeRepository.fetchSubscribeInfo(force = true).getOrNull()
                    }
                }
            } else {
                while (nowMillis() < deadline && info?.hasPlan != true) {
                    delay(3000)
                    info = subscribeRepository.fetchSubscribeInfo(force = true).getOrNull()
                }
            }
            subscribeRepository.fetchUserInfo()
            if (activated) {
                info = subscribeRepository.fetchSubscribeInfo(force = true).getOrNull()
            }
            val hasPlan = info?.hasPlan == true
            val kernelResult = if (hasPlan) kernelConfig.updateProfile() else ProfileUpdateResult.FAILED
            var servers = serverRepository.fetchServers(force = true).getOrNull().orEmpty()
            if (servers.isEmpty() && hasPlan) {
                delay(3000)
                servers = serverRepository.fetchServers(force = true).getOrNull().orEmpty()
            }
            if (servers.isNotEmpty()) {
                data.update { it.copy(serverName = servers.first().name) }
            } else {
                data.update {
                    it.copy(
                        serverName = Constants.PLACEHOLDER_DASH,
                        currentIp = Constants.PLACEHOLDER_DASH,
                    )
                }
            }
            dataWriter.applySubscribeInfo(
                data,
                info ?: subscribeRepository.getCachedSubscribeInfo(),
                errorMessageRes =
                if (!activated && tradeNo != null) {
                    R.string.purchase_activation_timeout
                } else {
                    null
                },
            )
            if (hasPlan && kernelResult != ProfileUpdateResult.FAILED) {
                refreshSiteInfoFromPanel()
                scope.launch { autoSpeedTestAfterUpdate(configChanged = profileReloaded(kernelResult)) }
            }
        }
    }

    suspend fun refresh(data: MutableStateFlow<DashboardData>) {
        data.update { it.copy(isRefreshing = true) }
        subscribeRepository.fetchSubscribeInfo().fold(
            onSuccess = { dataWriter.applySubscribeInfo(data, it, errorMessageRes = null) },
            onFailure = { e ->
                val resId =
                    ErrorMessages.forSubscribe(e)
                data.update {
                    it.copy(
                        isRefreshing = false,
                        dataLoaded = true,
                        errorMessageRes = resId,
                    )
                }
            },
        )
    }

    /**
     * 订阅更新成功后从面板 guest/comm/config 刷新站点名称/描述/官网，
     * 写入 SiteInfoStore 持久化（名称/描述以面板后台设置为准）。
     */
    private suspend fun refreshSiteInfoFromPanel() {
        val info = authRepository.fetchSiteInfo(force = true)
        siteInfoStore.updateFromPanelConfig(
            name = info.appName,
            description = info.appDescription,
            url = info.appUrl,
        )
    }

    /**
     * 订阅更新后的后台测速。
     *
     * @param configChanged 内核是否真的重载了 profile（见 [profileReloaded]）：
     *   只有重载过才会广播 `profileLoaded`，也只有那时才需要等它就位；
     *   节点集合变了也必须真测（[com.slte.app.kernel.speedTestUntilReady] 的 force）。
     */
    private suspend fun autoSpeedTestAfterUpdate(configChanged: Boolean) {
        if (configChanged && kernelManager.connected.value) {
            val before = kernelManager.profileLoaded.value
            withTimeoutOrNull(SPEED_TEST_WAIT_MS) {
                kernelManager.profileLoaded.first { it > before }
            }
        }
        kernelProxy.speedTestUntilReady(force = configChanged)
    }

    companion object {

        private const val SPEED_TEST_WAIT_MS = 10_000L

        private const val PURCHASE_REFRESH_TIMEOUT_MS = 60_000L
    }
}

/**
 * 订阅更新后内核是否真的重载了配置文件（纯函数，可单测）。
 *
 * 只有 [ProfileUpdateResult.UPDATED] 会触发内核重载并广播 `profileLoaded`；
 * [ProfileUpdateResult.UNCHANGED]（订阅内容一致、内核跳过重载）时等它永远等不到，
 * 只会白等满 [SubscriptionUpdater] 的 `SPEED_TEST_WAIT_MS`——运行日志里
 * 08:24:41.371 与 08:24:44.751 两次刷新各平白多等 10.65s / 10.60s。
 */
internal fun profileReloaded(kernelResult: ProfileUpdateResult): Boolean = kernelResult == ProfileUpdateResult.UPDATED
