// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 节点页组名显示映射（只影响渲染，不影响任何键）。
 *
 * 背景：1.12.0 把内置组「Ⓜ️ 微软」改名「Ⓜ️ 微软云盘」，Go 表改了但随包 libclash.so
 * 没重建，节点页拿到的仍是内核返回的旧名 → 设置页显示新名、节点页显示旧名。
 */
class RoutingGroupsDisplayNameTest {

    private val legacyName = "Ⓜ️ 微软"
    private val currentName = "Ⓜ️ 微软云盘"

    @Test
    fun `内核还叫旧名的内置组按当前名显示`() {
        assertEquals(currentName, groupDisplayName(legacyName, emptySet()))
    }

    @Test
    fun `用户自建组与旧内置组同名时显示原名`() {
        assertEquals(
            "自建组是用户自己的名字，不能替他改名",
            legacyName,
            groupDisplayName(legacyName, setOf(legacyName)),
        )
    }

    @Test
    fun `拿不到自建组集合时保守不映射`() {
        assertEquals(legacyName, groupDisplayName(legacyName, null))
    }

    @Test
    fun `当前名 结构组名与未知名一律恒等`() {
        assertEquals(currentName, groupDisplayName(currentName, emptySet()))
        assertEquals(PrimaryGroupName, groupDisplayName(PrimaryGroupName, emptySet()))
        assertEquals("🌏 国外穿墙", groupDisplayName("🌏 国外穿墙", emptySet()))
        assertEquals("我的自建组", groupDisplayName("我的自建组", emptySet()))
    }

    @Test
    fun `节点页显示名与设置页内置名一致`() {
        val settingsName = RoutingGroups.map { it.name }.single { it.contains("微软") }
        assertEquals("设置页与节点页必须显示同一个名字", settingsName, groupDisplayName(legacyName, emptySet()))
    }

    @Test
    fun `映射没有链式：别名值不会再次被映射`() {
        LegacyGroupAliases.forEach { (_, current) ->
            assertEquals("别名值必须是终态：$current", current, groupDisplayName(current, emptySet()))
        }
    }
}
