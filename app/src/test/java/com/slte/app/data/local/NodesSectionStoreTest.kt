// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点页折叠态持久化存储（第 3 轮 N3）。
 *
 * 关键点：**必须同时存"收起集合"与"已见集合"**——只存收起集合是无效的，
 * 重启后 ServerViewModel 的首见逻辑会把所有分组再收起一遍，用户展开过的照样丢。
 */
class NodesSectionStoreTest {
    private val store = NodesSectionStore(InMemoryPreferences())

    @Test
    fun `收起集合与已见集合能原样往返`() {
        val collapsed = setOf("🚀 节点选择", "广告拦截")
        val seen = setOf("🚀 节点选择", "广告拦截", "应用净化")

        store.save(ACCOUNT, collapsed, seen)

        assertEquals(collapsed, store.collapsedFor(ACCOUNT))
        assertEquals(seen, store.seenFor(ACCOUNT))
    }

    @Test
    fun `未写入过的账号读到空集合而不是崩溃`() {
        assertTrue(store.collapsedFor("nobody@example.com").isEmpty())
        assertTrue(store.seenFor("nobody@example.com").isEmpty())
        assertTrue(store.collapsedFor(null).isEmpty())
    }

    @Test
    fun `不同账号互不串味`() {
        store.save("a@example.com", setOf("主组"), setOf("主组"))
        store.save("b@example.com", setOf("广告拦截"), setOf("广告拦截"))

        assertEquals(setOf("主组"), store.collapsedFor("a@example.com"))
        assertEquals(setOf("广告拦截"), store.collapsedFor("b@example.com"))
    }

    @Test
    fun `返回的是副本，调用方修改不会污染存储`() {
        // 用两个元素：`toSet()` 对单元素集合返回不可变单例，测不出副本语义
        val original = setOf("主组", "广告拦截")
        store.save(ACCOUNT, original, original)

        val read = store.collapsedFor(ACCOUNT) as MutableSet<String>
        read.add("偷偷加一个")

        assertEquals("存储不该被外部改动影响", original, store.collapsedFor(ACCOUNT))
        assertFalse("偷偷加一个" in store.collapsedFor(ACCOUNT))
    }

    @Test
    fun `账号 key 不落明文邮箱且对同一账号稳定`() {
        val key = store.accountKeyOf("a@example.com")

        assertFalse("明文偏好文件里不该出现邮箱", key.contains("@") || key.contains("example"))
        assertEquals("大小写与空格不应产生两份数据", key, store.accountKeyOf("  A@Example.COM  "))
        assertTrue("无账号时也要有稳定 key，保证未登录态可用", store.accountKeyOf(null).isNotBlank())
    }

    private companion object {
        const val ACCOUNT = "user@example.com"
    }
}
