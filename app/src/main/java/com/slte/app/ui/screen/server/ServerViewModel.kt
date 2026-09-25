// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.cachedSpeedResults
import com.slte.app.kernel.groupByTypeCurrentNode
import com.slte.app.kernel.proxyGroups
import com.slte.app.kernel.selectAuto
import com.slte.app.kernel.selectFallback
import com.slte.app.kernel.selectInGroup
import com.slte.app.kernel.selectNode
import com.slte.app.kernel.speedTestProgressiveAndCache
import com.slte.app.kernel.testGroup
import com.slte.app.utils.Constants
import com.slte.app.utils.ErrorMessages
import com.slte.app.utils.extractCountryCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ServerViewModel
@Inject
constructor(
    private val serverRepository: ServerRepository,
    private val subscribeRepository: SubscribeRepository,
    private val kernelProxy: KernelProxy,
) : ViewModel() {

    private fun hasPlan(): Boolean = subscribeRepository.getCachedSubscribeInfo()?.hasPlan ?: true

    private val _data = MutableStateFlow(ServerData())
    val data: StateFlow<ServerData> = _data.asStateFlow()

    private val _errorMessageRes = MutableStateFlow<Int?>(null)
    val errorMessageRes: StateFlow<Int?> = _errorMessageRes.asStateFlow()

    private val _proxyGroups = MutableStateFlow<List<KernelProxyGroupInfo>>(emptyList())
    val proxyGroups: StateFlow<List<KernelProxyGroupInfo>> = _proxyGroups.asStateFlow()

    private val _isLoadingGroups = MutableStateFlow(false)
    val isLoadingGroups: StateFlow<Boolean> = _isLoadingGroups.asStateFlow()

    private val _testingGroup = MutableStateFlow<String?>(null)
    val testingGroup: StateFlow<String?> = _testingGroup.asStateFlow()

    private val _isTestingAll = MutableStateFlow(false)

    /** 顶部「测速」触发的全组测速是否进行中（页面只渲染策略组，单独暴露一个布尔）。 */
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()

    private val _speedTestTipRes = MutableStateFlow<Int?>(null)

    /** 全组测速结束提示（完成 / 失败），UI 取用后需 consume。 */
    val speedTestTipRes: StateFlow<Int?> = _speedTestTipRes.asStateFlow()

    init {

        val cachedDelays = kernelProxy.cachedSpeedResults()
        serverRepository.getCachedServers()?.let { applyNodes(it, cachedDelays) }
        refreshSpecialNodes()
    }

    private fun refreshSpecialNodes() {
        viewModelScope.launch {
            val auto = kernelProxy.groupByTypeCurrentNode("URLTest")
            val fallback = kernelProxy.groupByTypeCurrentNode("Fallback")
            _data.update { state ->
                state.copy(
                    autoNode = auto,
                    fallbackNode = fallback,
                    autoNodeCountryCode = countryOf(auto),
                    fallbackNodeCountryCode = countryOf(fallback),
                )
            }
        }
    }

    private fun countryOf(nodeName: String?): String? = nodeName?.let { name ->
        _data.value.nodes
            .firstOrNull { it.name == name }
            ?.countryCode
            ?.takeIf { it != "XX" }
    }

    suspend fun refreshNodesForPurchase() {
        serverRepository.fetchServers(force = true).fold(
            onSuccess = { applyNodes(it) },
            onFailure = { _errorMessageRes.value = ErrorMessages.forServer(it) },
        )
    }

    fun retry() {
        loadNodes()
    }

    fun dismissError() {
        _errorMessageRes.value = null
    }

    fun loadNodes(force: Boolean = false) {
        _errorMessageRes.value = null
        _data.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            serverRepository.fetchServers(force = force).fold(
                onSuccess = ::applyNodes,
                onFailure = { throwable ->
                    _data.update { it.copy(isLoading = false) }
                    _errorMessageRes.value = ErrorMessages.forServer(throwable)
                },
            )
        }
    }

    private fun applyNodes(
        servers: List<com.slte.app.domain.model.ServerNode>,
        delays: Map<String, Int>? = null,
    ) {
        val existing = _data.value.nodes.associate { it.name to it.delay }
        val nodes =
            servers
                .distinctBy { it.name }
                .mapIndexed { index, server ->
                    NodeItem(
                        id = index + 1,
                        name = server.name,
                        countryCode = extractCountryCode(server.name),
                        type = server.type.name,
                        host = server.host,
                        delay = delays?.get(server.name) ?: existing[server.name],
                    )
                }
        _data.update { it.copy(nodes = nodes, isLoading = false) }
        refreshSpecialNodes()
    }

    fun selectNode(nodeId: Int) {
        when (nodeId) {
            0 -> {
                _data.update { it.copy(selectedNodeId = 0) }
                viewModelScope.launch {
                    kernelProxy.selectAuto()
                    refreshSpecialNodes()
                    refreshGroupsIfLoaded()
                }
            }
            -1 -> {
                _data.update { it.copy(selectedNodeId = -1) }
                viewModelScope.launch {
                    kernelProxy.selectFallback()
                    refreshSpecialNodes()
                    refreshGroupsIfLoaded()
                }
            }
            else -> {
                val node = _data.value.nodes.firstOrNull { it.id == nodeId } ?: return
                _data.update { it.copy(selectedNodeId = nodeId) }
                viewModelScope.launch {
                    kernelProxy.selectNode(node.name)
                    // 主选择组当前项变化后同步策略组快照，否则节点页高亮与
                    // 各分流组的「当前出口」显示会停留在旧值
                    refreshGroupsIfLoaded()
                }
            }
        }
    }

    /** 仅在策略组已加载过时刷新，避免首次进入节点页前发起多余的组查询。 */
    private suspend fun refreshGroupsIfLoaded() {
        if (_proxyGroups.value.isEmpty()) return
        _proxyGroups.value = withCachedDelays(kernelProxy.proxyGroups())
    }

    fun startSpeedTest() {
        if (_isTestingAll.value) return
        if (!hasPlan()) {
            _errorMessageRes.value = R.string.dashboard_no_plan_tip
            return
        }
        _errorMessageRes.value = null
        // 先置位再启协程：点击即进入测速态，重复点击直接被上面的守卫吃掉
        _isTestingAll.value = true
        viewModelScope.launch {
            // 页面只剩策略组，测速即"全组测速"：healthCheckAll 覆盖所有组，
            // 结束后把内核的实时延迟回读到策略组卡片上
            val delays = kernelProxy.speedTestProgressiveAndCache { }
            refreshGroups()
            _isTestingAll.value = false
            _speedTestTipRes.value =
                if (delays.values.any { it != Constants.DELAY_TIMEOUT }) {
                    R.string.server_speed_test_done
                } else {
                    R.string.server_speed_test_failed
                }
        }
    }

    fun consumeSpeedTestTip() {
        _speedTestTipRes.value = null
    }

    fun updateSubscription() {
        if (!hasPlan()) {
            _errorMessageRes.value = R.string.dashboard_no_plan_tip
            return
        }
        loadNodes(force = true)
    }

    fun loadProxyGroups() {
        viewModelScope.launch { refreshGroups() }
    }

    private suspend fun refreshGroups() {
        _isLoadingGroups.value = true
        _proxyGroups.value = withCachedDelays(kernelProxy.proxyGroups())
        _isLoadingGroups.value = false
    }

    /**
     * 内核延迟 0 会被归一化成「超时」占位，未经测速的节点在卡片上会一律显示超时。
     * 这里用上次测速的缓存回填这些成员，让策略组在测速前也能显示已知延迟。
     */
    private fun withCachedDelays(groups: List<KernelProxyGroupInfo>): List<KernelProxyGroupInfo> {
        val cached = kernelProxy.cachedSpeedResults()?.takeIf { it.isNotEmpty() } ?: return groups
        return groups.map { group ->
            group.copy(
                members =
                group.members.map { member ->
                    val hit = cached[member.name] ?: return@map member
                    if (member.delay == null || member.delay == Constants.DELAY_TIMEOUT) member.copy(delay = hit) else member
                },
            )
        }
    }

    fun selectInGroup(
        groupName: String,
        proxyName: String,
    ) {
        viewModelScope.launch {
            if (kernelProxy.selectInGroup(groupName, proxyName)) {
                _proxyGroups.value = kernelProxy.proxyGroups()
                refreshSpecialNodes()
            } else {
                _errorMessageRes.value = R.string.proxy_group_select_failed
            }
        }
    }

    fun testGroup(groupName: String) {
        if (_testingGroup.value != null) return
        viewModelScope.launch {
            _testingGroup.value = groupName
            val delays = kernelProxy.testGroup(groupName)
            _proxyGroups.value =
                _proxyGroups.value.map { group ->
                    if (group.name != groupName) {
                        group
                    } else {
                        group.copy(
                            members =
                            group.members.map { member ->
                                delays[member.name]?.let { member.copy(delay = it) } ?: member
                            },
                        )
                    }
                }
            _testingGroup.value = null
        }
    }
}

data class ServerData(
    val nodes: List<NodeItem> = emptyList(),
    val selectedNodeId: Int = 0,
    val isLoading: Boolean = false,

    val kernelFallbackDelay: Int? = null,

    val autoNode: String? = null,

    val fallbackNode: String? = null,

    val autoNodeCountryCode: String? = null,

    val fallbackNodeCountryCode: String? = null,
) {

    val autoDelay: Int?
        get() =
            nodes
                .asSequence()
                .mapNotNull { it.delay }
                .filter { it != Constants.DELAY_TIMEOUT }
                .minOrNull()

    val fallbackDelay: Int?
        get() =
            kernelFallbackDelay ?: nodes
                .asSequence()
                .mapNotNull { it.delay }
                .filter { it != Constants.DELAY_TIMEOUT }
                .sorted()
                .toList()
                .getOrNull(1)
}

data class NodeItem(
    val id: Int = 0,
    val name: String,
    val countryCode: String = "XX",
    val type: String = "",
    val host: String = "",
    val delay: Int? = null,
)
