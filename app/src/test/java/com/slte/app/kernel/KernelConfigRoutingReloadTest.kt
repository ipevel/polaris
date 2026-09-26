// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.constants.Intents
import com.slte.app.support.MainDispatcherRule
import com.slte.app.support.RobolectricTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 分流配置变更触发的内核重载广播**必须**是 ACTION_OVERRIDE_CHANGED（真机栈回溯驱动的回归测试）。
 *
 * **为什么必须跑在 Robolectric 下**：
 * 1. 纯单测里 android.jar 是桩（app 模块开了 `isReturnDefaultValues = true`），
 *    `Intent.getAction()` 恒为 null——而"广播带了什么动作"正是本缺陷唯一的观测点；
 * 2. `Intents.ACTION_*` 依赖 `Global.application`（生产上由 `SlteApplication.onCreate` 初始化），
 *    纯单测里没初始化，读一次就 `UninitializedPropertyAccessException`。
 *
 * **缺陷背景（真机栈回溯）**：这 5 个分流开关——本地分流总开关、单组开关、复位、
 * 自定义组增删——此前发的是 `ACTION_PROFILE_CHANGED` 且**不带 EXTRA_UUID**，内核
 * `ConfigurationModule` 会把该动作当"切换到 EXTRA_UUID 指定的配置"解析：
 *
 * ```
 * UUID.fromString(getStringExtra(EXTRA_UUID))   // EXTRA_UUID 缺失 → NPE
 * → 异常穿出 select{} → enqueueEvent(LoadException)
 * → ConfigurationModule/TunModule/ClashRuntime destroyed → TunService destroyed
 * ```
 * * 用户侧表现：**每改一次分流规则就掉一次线**，节点页分组同时被清空（只剩「0 个分组」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class KernelConfigRoutingReloadTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<KernelManager>(relaxed = true)
    private val subscribeSource = mockk<SubscribeSource>(relaxed = true)
    private val remoteConfig = mockk<AppRemoteConfig>(relaxed = true)
    private val routingStateStore = mockk<RoutingStateStore>(relaxed = true)
    private val reporter = KernelFaultReporter(mainRule.dispatcher)

    @Before
    fun setUp() {
        // Robolectric 用测试 Application，不会走 SlteApplication.onCreate → 这里补 Global 初始化，
        // 否则 Intents.ACTION_* 与 kernel-common 的 packageName 都会抛未初始化异常。
        Global.init(RuntimeEnvironment.getApplication())
        // 写盘一律成功，才能走到广播那一步
        every { routingStateStore.setEnabled(any()) } returns true
        every { routingStateStore.setGroupEnabled(any(), any()) } returns true
        every { routingStateStore.resetGroups() } returns true
        every { routingStateStore.addCustomGroup(any()) } returns true
        every { routingStateStore.removeCustomGroup(any()) } returns true
    }

    private fun config(): KernelConfig = KernelConfig(
        reporter,
        manager,
        subscribeSource,
        remoteConfig,
        routingStateStore,
        mainRule.dispatcher,
        context,
    )

    @Test
    fun `五个分流开关都发 OVERRIDE_CHANGED，且不发任何无 uuid 的 PROFILE_CHANGED`() = runTest(mainRule.dispatcher) {
        val cfg = config()

        cfg.setLocalRoutingEnabled(false)
        cfg.applyRoutingGroup("广告拦截", false)
        cfg.resetRoutingGroups()
        cfg.addRoutingCustomGroup(
            RoutingCustomGroup(name = "自定义", url = "https://e.example.com/r.yaml", behavior = "classical"),
        )
        cfg.removeRoutingCustomGroup("自定义")

        verify(exactly = 5) {
            context.sendBroadcast(
                match { it.action == Intents.ACTION_OVERRIDE_CHANGED },
                any(),
            )
        }
        // 这是本测试的核心断言：一旦有人把它改回 ACTION_PROFILE_CHANGED，
        // 上面的 exactly = 5 会先红，这里会再红一次并指出后果。
        verify(exactly = 0) {
            context.sendBroadcast(
                match { it.action == Intents.ACTION_PROFILE_CHANGED },
                any(),
            )
        }
    }
}
