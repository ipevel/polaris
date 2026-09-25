// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 延迟三态 / 成员分类 / 主组解析 / 面板序重排 / 输入校验的纯函数单测。
 * 这些用例不需要内核、不需要真机。
 */
class DelaySemanticsTest {

    @Test
    fun `真实延迟原样返回且与是否测过无关`() {
        assertEquals(123, resolveDelay(123, hasTestRun = true))
        assertEquals(123, resolveDelay(123, hasTestRun = false))
        assertEquals(65534, resolveDelay(65534, hasTestRun = true))
    }

    @Test
    fun `0xffff 在测过时判超时、未测时判未测`() {
        assertEquals(Constants.DELAY_TIMEOUT, resolveDelay(Constants.DELAY_INVALID_MAX, hasTestRun = true))
        assertEquals(Constants.DELAY_PENDING, resolveDelay(Constants.DELAY_INVALID_MAX, hasTestRun = false))
    }

    @Test
    fun `无数据一律判未测`() {
        assertEquals(Constants.DELAY_PENDING, resolveDelay(0, hasTestRun = true))
        assertEquals(Constants.DELAY_PENDING, resolveDelay(-1, hasTestRun = false))
    }

    @Test
    fun `未测与超时必须是两个不同的值`() {
        assertTrue(Constants.DELAY_PENDING != Constants.DELAY_TIMEOUT)
        assertTrue(Constants.DELAY_PENDING < Constants.DELAY_INVALID_MAX)
    }

    @Test
    fun `成员分类由名字与 isGroup 决定`() {
        assertEquals(KernelProxyMemberKind.DIRECT, memberKindOf("DIRECT", isGroup = false))
        assertEquals(KernelProxyMemberKind.REJECT, memberKindOf("REJECT", isGroup = false))
        assertEquals(KernelProxyMemberKind.GROUP, memberKindOf("自动选择", isGroup = true))
        assertEquals(KernelProxyMemberKind.NODE, memberKindOf("香港 01", isGroup = false))
    }

    @Test
    fun `主组解析优先精确名`() {
        val groups =
            listOf(
                group("自动选择", selectable = false),
                group(PrimaryGroupName, selectable = true),
                group("其他", selectable = true),
            )
        assertEquals(PrimaryGroupName, primaryGroupOf(groups)?.name)
    }

    @Test
    fun `无精确名时回退第一个可选组`() {
        // 本地分流关闭 / 面板自定义组名的场景，N-08：不得出现「没有任何节点被选中」
        val groups =
            listOf(
                group("自动选择", selectable = false),
                group("机场节点", selectable = true),
                group("其他", selectable = true),
            )
        assertEquals("机场节点", primaryGroupOf(groups)?.name)
    }

    @Test
    fun `全无可选组时返回 null`() {
        assertTrue(primaryGroupOf(listOf(group("自动选择", selectable = false))).let { it == null })
    }

    @Test
    fun `面板序重排保持结构项在前`() {
        val members =
            listOf(
                member("日本 01"),
                member("香港 01"),
                member("新加坡 01"),
                member("自动选择", KernelProxyMemberKind.GROUP),
                member("DIRECT", KernelProxyMemberKind.DIRECT),
            )
        val order = mapOf("香港 01" to 0, "日本 01" to 1, "新加坡 01" to 2)
        val result = orderMembers(members, order)
        assertEquals(
            listOf("自动选择", "DIRECT", "香港 01", "日本 01", "新加坡 01"),
            result.map { it.name },
        )
    }

    @Test
    fun `不在面板列表的成员追加到节点段末尾且不丢成员`() {
        val members = listOf(member("provider-only"), member("香港 01"))
        val result = orderMembers(members, mapOf("香港 01" to 0))
        assertEquals(listOf("香港 01", "provider-only"), result.map { it.name })
        assertEquals(2, result.size)
    }

    @Test
    fun `面板索引为空时原样返回内核序`() {
        val members = listOf(member("b"), member("a"))
        assertEquals(members, orderMembers(members, emptyMap()))
    }

    private fun group(
        name: String,
        selectable: Boolean,
    ) = KernelProxyGroupInfo(name = name, type = "Selector", now = null, selectable = selectable, members = emptyList())

    private fun member(
        name: String,
        kind: KernelProxyMemberKind = KernelProxyMemberKind.NODE,
    ) = KernelProxyMember(name = name, isGroup = kind == KernelProxyMemberKind.GROUP, delay = null, kind = kind)
}

class SpeedTestOutcomeTest {

    @Test
    fun `空结果不得判为完成`() {
        // 回归：values.all{} 对空 Map 恒为 true，会让测速"瞬间成功"
        assertEquals(SpeedTestOutcome.EMPTY, speedTestOutcome(emptyMap(), completed = true))
        assertEquals(SpeedTestOutcome.EMPTY, speedTestOutcome(emptyMap(), completed = false))
    }

    @Test
    fun `全部未测或全部超时都算空结果`() {
        assertEquals(
            SpeedTestOutcome.EMPTY,
            speedTestOutcome(mapOf("a" to Constants.DELAY_TIMEOUT, "b" to Constants.DELAY_PENDING), completed = true),
        )
    }

    @Test
    fun `有真实值但探测未完成判部分完成`() {
        assertEquals(SpeedTestOutcome.PARTIAL, speedTestOutcome(mapOf("a" to 100), completed = false))
        assertEquals(SpeedTestOutcome.COMPLETE, speedTestOutcome(mapOf("a" to 100), completed = true))
    }

    @Test
    fun `健康检查预算随成员数增长并有上下限`() {
        assertEquals(HEALTH_CHECK_MIN_BUDGET_MS, healthCheckBudgetMs(0))
        assertEquals(HEALTH_CHECK_MIN_BUDGET_MS, healthCheckBudgetMs(10))
        // ceil(60/10) = 6 批 × 5s × 1.5 = 45s
        assertEquals(45_000L, healthCheckBudgetMs(60))
        assertEquals(HEALTH_CHECK_MAX_BUDGET_MS, healthCheckBudgetMs(1000))
        assertTrue(healthCheckBudgetMs(200) >= healthCheckBudgetMs(100))
    }

    @Test
    fun `落盘只保留真实延迟且不抹掉历史好值`() {
        assertEquals(mapOf("a" to 100), storeableDelays(null, mapOf("a" to 100, "b" to Constants.DELAY_TIMEOUT)))
        // 本轮超时不覆盖上一轮的好值
        assertEquals(mapOf("a" to 100), storeableDelays(mapOf("a" to 100), mapOf("a" to Constants.DELAY_TIMEOUT)))
        // 新值覆盖旧值
        assertEquals(mapOf("a" to 50), storeableDelays(mapOf("a" to 100), mapOf("a" to 50)))
        // 未测与超时都不落盘
        assertEquals(emptyMap<String, Int>(), storeableDelays(mapOf("a" to 0, "b" to 65535), emptyMap()))
        assertEquals(emptyMap<String, Int>(), storeableDelays(null, emptyMap()))
    }
}

/** 节点页区块折叠集合（集合内 = 收起）。 */
class ToggleCollapsedTest {

    @Test
    fun `点击在收起与展开之间往返`() {
        val collapsed = toggleCollapsed(emptySet(), PrimaryGroupName)
        assertTrue(PrimaryGroupName in collapsed)
        val expanded = toggleCollapsed(collapsed, PrimaryGroupName)
        assertTrue(PrimaryGroupName !in expanded)
    }

    @Test
    fun `切换一个组不影响其他组`() {
        val a = toggleCollapsed(emptySet(), "A")
        val ab = toggleCollapsed(a, "B")
        assertEquals(setOf("A", "B"), ab)
        assertEquals(setOf("B"), toggleCollapsed(ab, "A"))
    }

    @Test
    fun `同名不同对象（模拟刷新重建）语义不变——key 是组名而非对象`() {
        // 刷新会重建 KernelProxyGroupInfo（now/members 变化 → data class 不相等），
        // 但折叠集合只存组名，因此状态必须保持不变
        val before = KernelProxyGroupInfo(
            name = PrimaryGroupName,
            type = "Selector",
            now = "香港 01",
            selectable = true,
            members = emptyList(),
        )
        val collapsed = toggleCollapsed(emptySet(), before.name)
        val after = KernelProxyGroupInfo(
            name = PrimaryGroupName,
            type = "Selector",
            now = "日本 01",
            selectable = true,
            members = listOf(KernelProxyMember("日本 01", false, 42)),
        )
        assertTrue("刷新重建后仍应保持收起", after.name in collapsed)
    }

    @Test
    fun `重复切换总是回到集合原状`() {
        val start = setOf("X")
        assertEquals(start, toggleCollapsed(toggleCollapsed(start, "Y"), "Y"))
    }
}

class RoutingInputValidatorTest {
    @Test
    fun `合法组名通过`() {
        assertTrue(RoutingInputValidator.isValidGroupName("我的规则"))
        assertTrue(RoutingInputValidator.isValidGroupName("My Rules 2"))
        assertTrue(RoutingInputValidator.isValidGroupName("日本🇯🇵-01"))
    }

    @Test
    fun `逗号会被规则串字段分隔符错位必须拒绝`() {
        assertFalse(RoutingInputValidator.isValidGroupName("a,b"))
        assertFalse(RoutingInputValidator.isValidGroupName("DIRECT,x"))
    }

    @Test
    fun `控制字符与首尾空格必须拒绝`() {
        assertFalse(RoutingInputValidator.isValidGroupName("a\nb"))
        assertFalse(RoutingInputValidator.isValidGroupName(" a"))
        assertFalse(RoutingInputValidator.isValidGroupName("a "))
        assertFalse(RoutingInputValidator.isValidGroupName(""))
    }

    @Test
    fun `超长与保留名必须拒绝`() {
        assertFalse(RoutingInputValidator.isValidGroupName("x".repeat(33)))
        assertFalse(RoutingInputValidator.isValidGroupName(PrimaryGroupName))
        assertFalse(RoutingInputValidator.isValidGroupName("自动选择"))
        assertFalse(RoutingInputValidator.isValidGroupName("DIRECT"))
        assertFalse(RoutingInputValidator.isValidGroupName("GLOBAL"))
    }

    @Test
    fun `直连域名形态校验`() {
        assertTrue(RoutingInputValidator.isValidRuleDomainShape("example.com"))
        assertTrue(RoutingInputValidator.isValidRuleDomainShape("a.b.example.com"))
        assertFalse(RoutingInputValidator.isValidRuleDomainShape("x,MATCH,DIRECT.example.com"))
        assertFalse(RoutingInputValidator.isValidRuleDomainShape("a..b.com"))
        assertFalse(RoutingInputValidator.isValidRuleDomainShape("-a.com"))
        assertFalse(RoutingInputValidator.isValidRuleDomainShape("a-.com"))
        assertFalse(RoutingInputValidator.isValidRuleDomainShape("http://a.com"))
    }
}

/** 节点页成员可见性：移除「故障转移」入口，但保留 now 命中项（防"鬼选中"）。 */
class VisibleGroupMembersTest {

    private fun auto() = KernelProxyMember(AUTO_GROUP_NAME, true, null, KernelProxyMemberKind.GROUP)

    private fun fallback() = KernelProxyMember(FALLBACK_GROUP_NAME, true, null, KernelProxyMemberKind.GROUP)

    private fun node(name: String) = KernelProxyMember(name, false, 42)

    @Test
    fun `默认入口里没有故障转移、但有自动选择`() {
        val members = listOf(auto(), fallback(), node("香港 01"))
        val visible = visibleGroupMembers(members, now = "香港 01")
        assertEquals(listOf(AUTO_GROUP_NAME, "香港 01"), visible.map { it.name })
    }

    @Test
    fun `当前出口正是故障转移时必须保留该项`() {
        // 否则卡片头部显示「故障转移」，列表里却没有任何勾选行
        val members = listOf(auto(), fallback(), node("香港 01"))
        val visible = visibleGroupMembers(members, now = FALLBACK_GROUP_NAME)
        assertEquals(listOf(AUTO_GROUP_NAME, FALLBACK_GROUP_NAME, "香港 01"), visible.map { it.name })
    }

    @Test
    fun `now 为空或不匹配时判定为移除`() {
        val members = listOf(fallback(), node("香港 01"))
        assertEquals(listOf("香港 01"), visibleGroupMembers(members, now = null).map { it.name })
        assertEquals(listOf("香港 01"), visibleGroupMembers(members, now = "日本 01").map { it.name })
    }

    @Test
    fun `不改动其余成员的相对顺序与身份`() {
        val members = listOf(node("A"), fallback(), node("B"))
        val visible = visibleGroupMembers(members, now = "B")
        assertEquals(listOf("A", "B"), visible.map { it.name })
        assertTrue("必须是原对象而不是重建的副本", visible[0] === members[0])
    }
}
