// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.local.NodesSectionStore
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.SpeedTestOutcome
import com.slte.app.kernel.cachedSpeedResults
import com.slte.app.kernel.groupByTypeCurrentNode
import com.slte.app.kernel.primaryGroupOf
import com.slte.app.kernel.proxyGroups
import com.slte.app.kernel.selectAuto
import com.slte.app.kernel.selectFallback
import com.slte.app.kernel.selectInGroup
import com.slte.app.kernel.selectPrimary
import com.slte.app.kernel.speedTestOutcome
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
    private val kernelManager: KernelManager,
    private val nodesSectionStore: NodesSectionStore,
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

    /** 节点页「进入即自动测速」是否已跑过：VM 存活期 = 一个 App 会话，故只自动跑一次。 */
    private var autoSpeedTestOnEnter = false

    /** 全组测速结束提示（完成 / 失败），UI 取用后需 consume。 */
    val speedTestTipRes: StateFlow<Int?> = _speedTestTipRes.asStateFlow()

    /**
     * 节点页已收起的区块（**按组名**，集合内 = 收起）。
     *
     * 两点是刻意的：
     * 1. 放在 ViewModel 而不是屏幕内 `remember`：Tab 内容是
     *    `AnimatedContent(contentKey = { tab to leaf })`，切 Tab 会销毁整棵子树，
     *    局部状态（含 `rememberSaveable`，本仓没有 SaveableStateHolder）必丢，
     *    表现为"收起后切走再回来又全展开"。
     * 2. 用组名做 key 而不是组对象/下标：每次测速或订阅更新都会整体重建
     *    `KernelProxyGroupInfo` 列表，而同名对象的 now/members 已变化故不相等，
     *    用对象做 key 会导致折叠态在刷新后静默失效。
     *
     * 3. **跨冷启动持久化**（第 3 轮 N3）：存进 [NodesSectionStore]，按账号隔离。
     *    此前只在内存里、重启复位为默认折叠，用户"展开过"的分组下次启动又全收起
     *    （真机取证：95 冷启动全收起 → 96 用户展开 → 97 重启后又全收起）。
     *
     * 集合内 = 已收起。初始值从持久化存储读回，由 [syncCollapsedSections] 在区块**首次出现**时
     * 补写（首见即收起），因此"默认折叠"不需要在 UI 里散落 if-else。
     */
    private val _collapsedSections = MutableStateFlow<Set<String>>(emptySet())
    val collapsedSections: StateFlow<Set<String>> = _collapsedSections.asStateFlow()

    /**
     * 已出现过的区块键（只增不减）。
     *
     * "只增不减"是刻意的：折叠集合是**用户偏好记忆**，而 [KernelProxy.proxyGroups] 是逐组
     * `runCatching`，内核重载 / 瞬时查询失败都会让某个组从某次快照里消失。若按"当前快照里
     * 有哪些组"去裁剪折叠集合，这个组消失一次就会把用户刚收起的键抹掉，等它回来又变成
     * 默认态——现象正是"节点选择的折叠态留不住、切走再切回就弹回"。
     * 容器大小只受"见过的组名个数"约束，不构成泄漏。
     */
    private val seenSections = mutableSetOf<String>()

    /** 折叠态按账号隔离（同一台设备换账号登录时不互相污染）。必须声明在 init 之前。 */
    private val account: String = subscribeRepository.getCachedUserInfo()?.email.orEmpty()

    init {

        // 折叠态跨冷启动持久化（第 3 轮 N3）：先读回该账号上次的收起集合与"已见过"集合。
        // 只读 collapsed 不够——seen 若不恢复，syncCollapsedSections 会把所有分组当"首见"
        // 重新收起，用户展开过的分组照样被抹掉。
        seenSections += nodesSectionStore.seenFor(account)
        _collapsedSections.value = nodesSectionStore.collapsedFor(account)

        val cachedDelays = kernelProxy.cachedSpeedResults()
        serverRepository.getCachedServers()?.let { applyNodes(it, cachedDelays) }
        refreshSpecialNodes()
        observeKernelReloads()
    }

    private fun persistSections() {
        nodesSectionStore.save(account, _collapsedSections.value, seenSections)
    }

    /**
     * 内核每次(重)载配置成功后重新拉取策略组。
     *
     * 此前分组快照只在**进入节点页**时拉一次（LoggedInPages 的 `LaunchedEffect(Unit)`）。
     * 于是只要内核在用户停留在节点页期间重启（改分流规则触发重载、切模式、服务被系统重建……），
     * `_proxyGroups` 就会永久停在空列表：页面显示「27 个节点 · 0 个分组」「暂无节点，请先更新订阅」，
     * 怎么等都不回来，只有切走再切回（重建组合→重新拉一次）才恢复——用户实测到的
     * "一暂停连接，节点页里的东西就消失"正是这个。
     *
     * 挂钩 [KernelManager.profileLoaded]（内核 `Clash.load` 成功后才发出）而不是连接状态：
     * 它保证"内核确实已有一份可查询的新配置"，此刻拉取才有意义。
     */
    private fun observeKernelReloads() {
        viewModelScope.launch {
            kernelManager.profileLoaded.collect {
                val groups = kernelProxy.proxyGroups()
                // 内核刚起来的瞬间可能查不到组，此时不覆盖已有列表——保留旧数据等下一次，
                // 免得把本来正常的页面刷成空。列表本来就是空时照常写入，否则永远自愈不了。
                if (groups.isNotEmpty() || _proxyGroups.value.isEmpty()) {
                    refreshPrimarySectionState(groups)
                }
            }
        }
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
                // selectedNodeId 是 v5 之前的遗留字段（v5 节点页的选中态完全由
                // 内核回读的主组 now 决定），保留写入只为兼容既有调用方/用例。
                _data.update { it.copy(selectedNodeId = nodeId) }
                viewModelScope.launch {
                    selectPrimaryMember(node.name)
                    // 主选择组当前项变化后同步策略组快照，否则节点页高亮与
                    // 各分流组的「当前出口」显示会停留在旧值
                    refreshGroupsIfLoaded()
                }
            }
        }
    }

    /**
     * 节点页「节点选择」卡的主组切换（成员可以是组：自动选择 / 故障转移）。
     * 失败必须有可见反馈——旧路径丢弃了内核返回的 Boolean，用户点了没反应。
     *
     * **组名不硬编码**：`primaryGroupOf` 在主组缺失（本地分流关闭 / 面板改名）时会
     * 回退到第一个 Selectable 组，若这里写死 `PrimaryGroupName` 就会 patch 到用户
     * 没在看的那一组，表现为"点了没用"。成功判据由 [selectInGroup] 的回读确认给出。
     */
    suspend fun selectPrimaryMember(memberName: String) {
        val primaryName = primaryGroupOf(_proxyGroups.value)?.name ?: com.slte.app.kernel.PrimaryGroupName
        if (!kernelProxy.selectPrimary(primaryName, memberName)) {
            _errorMessageRes.value = R.string.proxy_group_select_failed
            return
        }
        refreshSpecialNodes()
        refreshGroupsIfLoaded()
    }

    /** 保留给旧调用点的入口（后台线程外的 UI 层统一走 [selectPrimaryMember]）。 */
    fun selectPrimary(name: String) {
        viewModelScope.launch { selectPrimaryMember(name) }
    }

    /** 仅在策略组已加载过时刷新，避免首次进入节点页前发起多余的组查询。 */
    private suspend fun refreshGroupsIfLoaded() {
        if (_proxyGroups.value.isEmpty()) return
        refreshPrimarySectionState(kernelProxy.proxyGroups())
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
            // 本地分流下结构组已 include-all，测速只 await 结构组（见 KernelProxySpeed）
            val delays = kernelProxy.speedTestProgressiveAndCache { }
            refreshGroups()
            // 同一份 delays 回填节点列表：此前只刷新策略组，导致「全部节点」
            // 列表停留在旧值、与分组卡片显示两个不同数字
            if (delays.isNotEmpty()) {
                _data.update { state -> state.copy(nodes = mergeDelays(state.nodes, delays)) }
            }
            _isTestingAll.value = false
            _speedTestTipRes.value =
                when (speedTestOutcome(delays, completed = true)) {
                    SpeedTestOutcome.COMPLETE -> R.string.server_speed_test_done
                    SpeedTestOutcome.PARTIAL -> R.string.server_speed_test_done
                    SpeedTestOutcome.EMPTY -> R.string.server_speed_test_failed
                }
        }
    }

    fun consumeSpeedTestTip() {
        _speedTestTipRes.value = null
    }

    /**
     * 进入节点页时自动跑一次测速（整改要求 3）。
     *
     * **每个 App 会话只自动跑一次**：切 Tab 会重建节点页组合，若不节流，用户每切一次 Tab
     * 就触发一轮全量测速（结构组测速预算 22.5s），既浪费流量又频繁打断操作。
     * 需要新数据可点顶栏「测速」；日常延迟也由内核按 interval=300 自行拨测维护
     * （见 kernel-core native/config/routing/routing_table.go）。
     */
    fun autoSpeedTestOnNodesPage() {
        if (autoSpeedTestOnEnter) return
        autoSpeedTestOnEnter = true
        // 无套餐时不打网络：也不让 startSpeedTest 顺势把"无套餐"写进 errorMessageRes，
        // 那会在用户刚进页面时就弹一条与本次操作无关的提示。
        if (!hasPlan()) return
        startSpeedTest()
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

    /**
     * 切换某个区块的展开/收起。集合内 = 收起。
     *
     * [com.slte.app.kernel.PRIMARY_SECTION_KEY]（主组卡）与内核保留组
     * （自动选择/故障转移/漏网之鱼）走普通开关；其余分流组走**单开手风琴**：
     * 每条分流组都 include-all 了全部节点，而节点页滚动体是非懒加载的
     * `Column+verticalScroll`，同时展开多组会组合出「组数 × 节点数」行
     * （默认 10 组 × N 节点，全开 27 组）；所以展开一个分流组时把其余分流组收起。
     * 主组卡不受手风琴影响（它本是页面主任务）。
     */
    fun toggleSection(name: String) {
        val routingNames =
            _proxyGroups.value
                .map { it.name }
                .filterNot { it in com.slte.app.kernel.RoutingReservedNames }
        _collapsedSections.value =
            if (name in routingNames) {
                val collapsed = com.slte.app.kernel.toggleCollapsed(_collapsedSections.value, name)
                if (name in collapsed) collapsed else collapsed + (routingNames - name)
            } else {
                com.slte.app.kernel.toggleCollapsed(_collapsedSections.value, name)
            }
        persistSections()
    }

    /**
     * 组列表变化后校正折叠集合：**首见即收起**（默认折叠），已见过的键一律不动。
     *
     * 两个保护：
     * 1. 组列表为空时直接返回——首次进节点页 / 内核未就绪时 `_proxyGroups` 为空，此时没有
     *    "新出现的区块"，不该做任何事；[seenSections] 也不会被污染，内核可用后的首次非空
     *    同步照常补上默认收起（所以"首次拿到空列表"不会让默认折叠永久失效）；
     * 2. **不做**"丢弃已不存在的组名"的裁剪。`proxyGroups()` 逐组 `runCatching`，单个组瞬时
     *    查询失败就会从快照里消失；裁剪会把用户刚收起的键一并抹掉，那个组回来后又变回默认态
     *    ——这正是"节点选择的折叠态留不住、切走再切回就弹回"的根因。
     */
    private fun syncCollapsedSections(groupNames: List<String>) {
        if (groupNames.isEmpty()) return
        val firstSeen =
            (groupNames + com.slte.app.kernel.PRIMARY_SECTION_KEY).filterNot { it in seenSections }
        if (firstSeen.isEmpty()) return
        seenSections += firstSeen
        _collapsedSections.value = _collapsedSections.value + firstSeen
        // 只在真的新增了"首见"时落盘，避免每次分组刷新都写偏好
        persistSections()
    }

    private suspend fun refreshGroups() {
        _isLoadingGroups.value = true
        refreshPrimarySectionState(kernelProxy.proxyGroups())
        _isLoadingGroups.value = false
    }

    /**
     * 拉取组列表并把结果同步给折叠集合（避免各调用点各写一份）。
     *
     * 先写折叠集合再写 `_proxyGroups`：否则首帧会先按"全展开"组合出一份骨架，下一帧才收起，
     * 出现可见抖动（本页滚动体非懒加载，重组的代价也更高）。
     */
    private suspend fun refreshPrimarySectionState(next: List<KernelProxyGroupInfo>) {
        syncCollapsedSections(next.map { it.name })
        _proxyGroups.value = withCachedDelays(next)
    }

    /**
     * 未经测速的成员延迟是「未测」（[Constants.DELAY_PENDING]）而不是「超时」，
     * 用上次测速的缓存回填这些成员，让策略组在测速前也能显示已知延迟。
     * 已测过且失败（[Constants.DELAY_TIMEOUT]）的成员同样用缓存覆盖——
     * 缓存里存的是历史有效值（见 KernelProxySpeed.storeableDelays）。
     */
    private fun withCachedDelays(groups: List<KernelProxyGroupInfo>): List<KernelProxyGroupInfo> {
        val cached = kernelProxy.cachedSpeedResults()?.takeIf { it.isNotEmpty() } ?: return groups
        return groups.map { group ->
            group.copy(
                members =
                group.members.map { member ->
                    val hit = cached[member.name] ?: return@map member
                    val unmeasured =
                        member.delay == null ||
                            member.delay <= Constants.DELAY_PENDING ||
                            member.delay >= Constants.DELAY_TIMEOUT
                    if (unmeasured) member.copy(delay = hit) else member
                },
            )
        }
    }

    /** 把测速结果合并进节点列表（纯函数，可单测）。 */
    internal fun mergeDelays(
        nodes: List<NodeItem>,
        delays: Map<String, Int>,
    ): List<NodeItem> = nodes.map { node ->
        val hit = delays[node.name] ?: return@map node
        node.copy(delay = if (hit >= Constants.DELAY_TIMEOUT) Constants.DELAY_TIMEOUT else hit)
    }

    fun selectInGroup(
        groupName: String,
        proxyName: String,
    ) {
        viewModelScope.launch {
            if (kernelProxy.selectInGroup(groupName, proxyName)) {
                // 就地更新（不重新查询内核，避免丢掉刚写回的延迟）；
                // 组集合本身不变，但同步一次折叠集合以覆盖"组曾缺失"的情形。
                val next = kernelProxy.proxyGroups()
                _proxyGroups.value = next
                syncCollapsedSections(next.map { it.name })
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
                // > DELAY_PENDING：未测（0）不能被当成 0ms 参与取最小
                // < DELAY_TIMEOUT：超时（999）不是有效延迟
                .filter { it > Constants.DELAY_PENDING && it < Constants.DELAY_TIMEOUT }
                .minOrNull()

    val fallbackDelay: Int?
        get() =
            kernelFallbackDelay ?: nodes
                .asSequence()
                .mapNotNull { it.delay }
                .filter { it > Constants.DELAY_PENDING && it < Constants.DELAY_TIMEOUT }
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
