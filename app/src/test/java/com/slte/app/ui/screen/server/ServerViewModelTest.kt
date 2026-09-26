// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.R
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.NodesSectionStore
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.ServerNode
import com.slte.app.domain.model.ServerType
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.User
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.PRIMARY_SECTION_KEY
import com.slte.app.kernel.PrimaryGroupName
import com.slte.app.support.MainDispatcherRule
import com.slte.app.support.stubKernelBridge
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val subscribeRepository = mockk<SubscribeRepository>(relaxed = true)
    private val kernelProxy = mockk<KernelProxy>(relaxed = true)
    private val kernelManager = mockk<KernelManager>(relaxed = true)

    /** 折叠态持久化存储（真实实现 + 内存偏好），用于验证跨冷启动恢复。 */
    private val nodesSectionStore = NodesSectionStore(InMemoryPreferences())

    /**
     * `profileLoaded` 必须桩成真实 `MutableStateFlow`：relaxed mock 返回的 StateFlow
     * 一 collect 就抛 `KotlinNothingValueException`（VM 的 init 里会订阅它）。
     */
    private val profileLoadedFlow = MutableStateFlow(0)

    private fun viewModel(): ServerViewModel {
        kernelProxy.stubKernelBridge()
        every { subscribeRepository.getCachedSubscribeInfo() } returns null
        every { kernelManager.profileLoaded } returns profileLoadedFlow
        return ServerViewModel(serverRepository, subscribeRepository, kernelProxy, kernelManager, nodesSectionStore)
    }

    private fun node(
        name: String,
        id: Int = 1,
    ) = ServerNode(id = id, name = name, type = ServerType.VMESS, host = "h.example.com", port = 443)

    @Test
    fun `加载成功后填充节点并标注国家码`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns
            Result.success(listOf(node("香港01", 1), node("美国01", 2), node("香港01", 3)))
        val vm = viewModel()

        vm.loadNodes(force = true)
        advanceUntilIdle()

        val nodes = vm.data.value.nodes
        assertEquals("同名节点应去重", 2, nodes.size)
        assertEquals("HK", nodes.first { it.name == "香港01" }.countryCode)
        assertEquals("US", nodes.first { it.name == "美国01" }.countryCode)
        assertTrue(!vm.data.value.isLoading)
    }

    @Test
    fun `加载失败提示节点错误`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.retry()
        advanceUntilIdle()

        assertEquals(R.string.error_server_load, vm.errorMessageRes.value)
        assertTrue(!vm.data.value.isLoading)
    }

    @Test
    fun `选中普通节点调用内核主组选择并更新选中项`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.success(listOf(node("香港01", 1)))
        val vm = viewModel()
        vm.loadNodes(force = true)
        advanceUntilIdle()

        val target = vm.data.value.nodes.first()
        vm.selectNode(target.id)
        advanceUntilIdle()

        assertEquals(target.id, vm.data.value.selectedNodeId)
    }

    /**
     * 内核侧选择失败必须给出可见反馈：旧实现丢弃了内核返回的 Boolean，
     * 用户点了没反应。这里用"内核未就绪（clash()=null）"制造失败。
     */
    @Test
    fun `主组选择失败时给出可见提示`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.success(listOf(node("香港01", 1)))
        val vm = viewModel()
        vm.loadNodes(force = true)
        advanceUntilIdle()

        vm.selectPrimary(vm.data.value.nodes.first().name)
        advanceUntilIdle()

        assertEquals(R.string.proxy_group_select_failed, vm.errorMessageRes.value)
    }

    @Test
    fun `选中自动选择走专用内核入口`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.selectNode(0)
        advanceUntilIdle()

        assertEquals(0, vm.data.value.selectedNodeId)
    }

    @Test
    fun `测速中状态标记进行中，重复触发不叠加`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.startSpeedTest()
        assertTrue("测速应标记进行中", vm.isTestingAll.value)

        vm.startSpeedTest()
        assertTrue("重复触发不应改变状态", vm.isTestingAll.value)

        advanceUntilIdle()
        assertTrue("测速结束后应复位", !vm.isTestingAll.value)
    }

    @Test
    fun `全组测速完成后给出完成提示并刷新策略组延迟`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubClashGroups(listOf("香港01" to 42, "香港02" to 88), now = "香港01")

        vm.startSpeedTest()
        advanceUntilIdle()

        assertEquals(R.string.server_speed_test_done, vm.speedTestTipRes.value)
        assertTrue("测速结束应复位", !vm.isTestingAll.value)
        val members = vm.proxyGroups.value.first().members.associate { it.name to it.delay }
        assertEquals("策略组成员应带出内核实时延迟", 42, members["香港01"])

        vm.consumeSpeedTestTip()
        assertNull(vm.speedTestTipRes.value)
    }

    @Test
    fun `测速拿不到延迟时提示失败而非静默`() = runTest(mainRule.dispatcher) {
        // 内核未就绪 → 测速结果为空，此前这种失败是静默的
        val vm = viewModel()

        vm.startSpeedTest()
        advanceUntilIdle()

        assertEquals(R.string.server_speed_test_failed, vm.speedTestTipRes.value)
        assertTrue("测速结束应复位", !vm.isTestingAll.value)
    }

    @Test
    fun `策略组加载时用测速缓存回填未测出的延迟`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubClashGroups(listOf("香港01" to 0, "香港02" to 77), now = "香港01")
        every { kernelProxy.speedResultStore.getSpeedResults() } returns mapOf("香港01" to 32)

        vm.loadProxyGroups()
        advanceUntilIdle()

        val members = vm.proxyGroups.value.first().members.associate { it.name to it.delay }
        assertEquals("内核未测出的成员回填缓存", 32, members["香港01"])
        assertEquals("内核已测出的成员保留实时值", 77, members["香港02"])
    }

    @Test
    fun `无套餐时测速与更新订阅弹提示而非静默`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        every { subscribeRepository.getCachedSubscribeInfo() } returns
            SubscribeInfo(planName = "", transferEnable = 0L, usedTraffic = 0L, expiredAt = 0L)

        vm.startSpeedTest()
        assertEquals(R.string.dashboard_no_plan_tip, vm.errorMessageRes.value)
        assertTrue("无套餐不应进入测速态", !vm.isTestingAll.value)

        vm.dismissError()
        vm.updateSubscription()
        assertEquals(R.string.dashboard_no_plan_tip, vm.errorMessageRes.value)
    }

    @Test
    fun `策略组内切换失败时提示错误`() = runTest(mainRule.dispatcher) {
        // 未桩化内核时 selectInGroup 扩展函数实际执行后 clash==null，safe 返回 false，触发失败路径
        val vm = viewModel()

        vm.selectInGroup(GROUP_NAME, "香港01")
        advanceUntilIdle()

        assertEquals(R.string.proxy_group_select_failed, vm.errorMessageRes.value)
    }

    @Test
    fun `购买后刷新节点成功填充列表`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.success(listOf(node("香港01", 1)))
        val vm = viewModel()

        vm.refreshNodesForPurchase()
        advanceUntilIdle()

        assertEquals(1, vm.data.value.nodes.size)
        assertNull(vm.errorMessageRes.value)
    }

    @Test
    fun `购买后刷新节点失败提示错误`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.refreshNodesForPurchase()
        advanceUntilIdle()

        assertEquals(R.string.error_server_load, vm.errorMessageRes.value)
    }

    // region 节点页分组折叠态（问题1：折叠态留不住 / 问题2：应默认折叠）

    @Test
    fun `首次加载后所有区块默认收起（含主组卡与分流组）`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME))

        vm.loadProxyGroups()
        advanceUntilIdle()

        val collapsed = vm.collapsedSections.value
        assertTrue("主组卡应默认收起", PRIMARY_SECTION_KEY in collapsed)
        assertTrue("分流组应默认收起", GROUP_NAME in collapsed)
    }

    /**
     * `sectionsLoaded` 类的"首次标记"若写在组列表判空之前，首次拿到空快照就会把
     * "默认收起"永久吃掉。这里把顺序钉死。
     */
    @Test
    fun `首次快照为空不会吃掉默认收起`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubGroupNames(emptyList())
        vm.loadProxyGroups()
        advanceUntilIdle()
        assertTrue("空快照不应写入任何折叠键", vm.collapsedSections.value.isEmpty())

        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME))
        vm.loadProxyGroups()
        advanceUntilIdle()

        val collapsed = vm.collapsedSections.value
        assertTrue("内核可用后默认收起仍要生效（主组卡）", PRIMARY_SECTION_KEY in collapsed)
        assertTrue("内核可用后默认收起仍要生效（分流组）", GROUP_NAME in collapsed)
    }

    /**
     * 问题1 的根因回归：`proxyGroups()` 逐组 `runCatching`（KernelProxyGroup.kt），内核重载或
     * 瞬时失败会让某个组从某次快照里消失。旧实现按"当前快照里恰好有哪些组"裁剪折叠集合
     * （`collapsed intersect names`），该组一瞬消失就把用户的折叠态一并抹掉，等它回来又落回
     * 默认态——也就是用户看到的"切走再切回就弹回"（节点页每次进入都会重新拉一次快照）。
     *
     * 断言刻意不写死"应该展开还是收起"：只要求**用户操作后的那个状态**跨瞬时缺组保持不变。
     * 这样它在旧的"默认展开 + 裁剪"语义下同样会红（旧语义里用户操作是收起、回来却变展开），
     * 而不是只针对新语义自证。
     */
    @Test
    fun `瞬时缺组不会抹掉用户的折叠态，切走再切回保持同一状态`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME))
        vm.loadProxyGroups()
        advanceUntilIdle()

        // 用户在主组卡上点了一下，得到他想要的状态
        vm.toggleSection(PRIMARY_SECTION_KEY)
        val userChoice = PRIMARY_SECTION_KEY in vm.collapsedSections.value

        // 再次进入节点页：本次快照里主组瞬时查不到（内核重载中）
        stubGroupNames(listOf(GROUP_NAME))
        vm.loadProxyGroups()
        advanceUntilIdle()

        // 下一次刷新主组回来了
        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME))
        vm.loadProxyGroups()
        advanceUntilIdle()

        assertEquals(
            "用户在主组卡上的折叠选择必须跨瞬时缺组保持（问题1：切走再切回弹回）",
            userChoice,
            PRIMARY_SECTION_KEY in vm.collapsedSections.value,
        )
    }

    /**
     * 内核每次重载配置后必须自动重新拉取策略组。
     *
     * 用户实测的现场：在「分流规则管理」里改开关 → 触发内核重载（历史上还会因广播缺 uuid 把
     * TunService 整个拆掉）→ 节点页分组快照停在空列表，页面显示「27 个节点 · 0 个分组」
     * 「暂无节点，请先更新订阅」，怎么等都不回来，只有切走再切回才恢复。
     *
     * 根因是快照只在**进入节点页**时拉一次（LoggedInPages 的 LaunchedEffect(Unit)），
     * 内核重启后没有任何触发点。现在挂在 KernelManager.profileLoaded 上自愈：
     * 内核 `Clash.load` 成功 → 广播 PROFILE_LOADED → 重新拉一次。
     */
    @Test
    fun `内核重载配置后自动重新拉取分组，空快照能自愈`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        // 前置：进入节点页时内核不可用，快照为空——正是用户看到的「0 个分组」
        stubGroupNames(emptyList())
        vm.loadProxyGroups()
        advanceUntilIdle()
        assertTrue("前置条件：快照应为空", vm.proxyGroups.value.isEmpty())

        // 内核重启并成功载入配置：这次能查到两个组
        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME))
        profileLoadedFlow.value = 1
        advanceUntilIdle()

        assertEquals(
            "内核重载后应自动重新拉取分组，不能停在空列表（用户：一暂停连接节点页东西就消失）",
            listOf(PrimaryGroupName, GROUP_NAME),
            vm.proxyGroups.value.map { it.name },
        )
    }

    @Test
    fun `展开一条分流组不影响主组卡，且其余分流组被手风琴收起`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubGroupNames(listOf(PrimaryGroupName, GROUP_NAME, "🤖 AI"))
        vm.loadProxyGroups()
        advanceUntilIdle()

        vm.toggleSection(GROUP_NAME)

        val collapsed = vm.collapsedSections.value
        assertTrue("被点开的分流组应展开", GROUP_NAME !in collapsed)
        assertTrue("其余分流组应被手风琴收起", "🤖 AI" in collapsed)
        assertTrue("主组卡不受手风琴影响", PRIMARY_SECTION_KEY in collapsed)
    }

    /** 折叠键必须与任何真实组名都不可能相等，否则两张卡会共用键互相踩。 */
    @Test
    fun `主组折叠键不与任何真实组名冲突`() {
        assertTrue("折叠键不得落在内核保留组名里", PRIMARY_SECTION_KEY !in com.slte.app.kernel.RoutingReservedNames)
        assertTrue("折叠键不得等于主组名", PRIMARY_SECTION_KEY != PrimaryGroupName)
    }

    // endregion

    /** 以桩化的内核策略组替换默认的"未就绪"内核，供测速 / 策略组用例使用。 */
    private fun stubClashGroups(
        members: List<Pair<String, Int>>,
        now: String,
    ) {
        // 保留 safe() 直通桩，再把它挂到的 manager 换成可读策略组的内核
        kernelProxy.stubKernelBridge()
        val clash = mockk<IClashManager>(relaxed = true)
        val proxies =
            members.map { (name, delay) ->
                Proxy(name = name, title = name, subtitle = "", type = "vmess", delay = delay, isGroup = false)
            }
        every { clash.queryProxyGroupNames(any()) } returns listOf(GROUP_NAME)
        every { clash.queryProxyGroup(any(), any()) } returns ProxyGroup(type = "Selector", proxies = proxies, now = now)

        val manager = mockk<KernelManager>(relaxed = true)
        every { manager.clash() } returns clash
        every { kernelProxy.manager } returns manager
    }

    /**
     * 桩化多策略组内核：每个组名都返回同一个成员列表的 Selector 组。
     *
     * 可以二次调用来换一份组名列表，从而模拟"某个组从某次快照里瞬时消失"——
     * `proxyGroups()` 是逐组 `runCatching`，这是在单测里复现问题1（折叠态被裁剪）的必要手段。
     */
    private fun stubGroupNames(
        names: List<String>,
        members: List<Pair<String, Int>> = listOf("香港01" to 42),
    ) {
        kernelProxy.stubKernelBridge()
        val clash = mockk<IClashManager>(relaxed = true)
        val proxies =
            members.map { (name, delay) ->
                Proxy(name = name, title = name, subtitle = "", type = "vmess", delay = delay, isGroup = false)
            }
        every { clash.queryProxyGroupNames(any()) } returns names
        names.forEach { groupName ->
            every { clash.queryProxyGroup(groupName, any()) } returns
                ProxyGroup(type = "Selector", proxies = proxies, now = proxies.firstOrNull()?.name.orEmpty())
        }

        val manager = mockk<KernelManager>(relaxed = true)
        every { manager.clash() } returns clash
        every { kernelProxy.manager } returns manager
    }

    /**
     * 回归（第 3 轮 N3）：折叠态必须跨冷启动保住。
     *
     * 真机取证链：95 图（冷启动，全部收起）→ 96 图（用户展开主组）→ **97 图（重启 App 再进
     * 节点页，又变回全部收起）**。"切走再切回"此前已修，唯独进程重启这一维度没保住。
     */
    @Test
    fun `冷启动后从持久化恢复用户收起的分组`() = runTest(mainRule.dispatcher) {
        every { subscribeRepository.getCachedUserInfo() } returns
            User(id = "1", displayName = ACCOUNT, email = ACCOUNT, authData = "auth", subscribeToken = "sub")
        // 上一次会话：用户把主组卡收起了，且这些键都已经"见过"
        nodesSectionStore.save(ACCOUNT, setOf(PRIMARY_SECTION_KEY), setOf(PRIMARY_SECTION_KEY))

        val vm = viewModel()

        assertTrue(
            "重启后应恢复用户收起的主组卡，而不是被首见逻辑重新判成未见过",
            PRIMARY_SECTION_KEY in vm.collapsedSections.value,
        )
    }

    @Test
    fun `折叠态按账号隔离，换账号不串味`() = runTest(mainRule.dispatcher) {
        nodesSectionStore.save(ACCOUNT, setOf(PRIMARY_SECTION_KEY), setOf(PRIMARY_SECTION_KEY))
        // 当前会话没有账号信息 ⇒ 读到的是匿名那份，不该吃到别的账号的收起态
        every { subscribeRepository.getCachedUserInfo() } returns null

        val vm = viewModel()

        assertTrue("另一个账号的收起态不该生效", vm.collapsedSections.value.isEmpty())
    }

    private companion object {

        const val GROUP_NAME = "节点选择"

        const val ACCOUNT = "a@example.com"
    }
}
