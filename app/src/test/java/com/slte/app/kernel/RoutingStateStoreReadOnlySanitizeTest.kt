// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 1.12.1 止血：非法条目只读隐藏、**不回写磁盘**。
 *
 * 背景：`loadSanitized` 曾经在丢掉非法条目时 `write(clean)`，而磁盘是用户数据的唯一副本
 * （私有 filesDir + allowBackup=false + 无 .bak）——「与内置组同名的自定义组」升级后被
 * 不可逆删除就是这么来的。这里用真实文件 IO 断言**字节级不变**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class RoutingStateStoreReadOnlySanitizeTest {
    private lateinit var store: RoutingStateStore

    @Before
    fun setUp() {
        store = RoutingStateStore(RuntimeEnvironment.getApplication())
        store.stateFile().delete()
        store.stateFile().parentFile?.mkdirs()
    }

    private fun writeRaw(text: String) {
        store.stateFile().writeText(text)
    }

    private fun sha256(): String = MessageDigest.getInstance("SHA-256").digest(store.stateFile().readBytes())
        .joinToString("") { "%02x".format(it) }

    @Test
    fun `隐藏非法条目不回写磁盘：文件字节逐字节不变`() {
        writeRaw(
            """{"version":1,"enabled":true,"custom":[""" +
                """{"name":"我的组","url":"https://x.example.com/a.yaml"},""" +
                """{"name":"Ⓜ️ 微软云盘","url":"https://x.example.com/b.yaml"}],""" +
                """"direct_domains":["panel.example.cn","bad domain"]}""",
        )
        val before = sha256()

        val sanitized = store.loadSanitized()

        assertEquals("磁盘必须逐字节不变（隐藏不等于删除）", before, sha256())
        assertEquals(listOf("我的组"), sanitized.state.custom.map { it.name })
        assertEquals(listOf("panel.example.cn"), sanitized.state.directDomains)
        assertTrue(sanitized.hasDropped)
        assertEquals(listOf("Ⓜ️ 微软云盘"), sanitized.hiddenCustom.map { it.name })
        assertEquals(listOf("bad domain"), sanitized.hiddenDomains)

        // 再读一次仍然看得见（不是「本次读丢了几个」，而是文件里确实还有）
        val again = store.loadSanitized()
        assertTrue(again.hasDropped)
        assertEquals(listOf("Ⓜ️ 微软云盘"), again.hiddenCustom.map { it.name })
        assertEquals("重复读也不能改盘", before, sha256())
    }

    @Test
    fun `迁移历史组名时只写名字，被隐藏的自定义组仍在磁盘上`() {
        writeRaw(
            """{"version":1,"enabled":true,"groups":{"📲 X":true},"order":["📲 X"],""" +
                """"custom":[{"name":"Ⓜ️ 微软云盘","url":"https://x.example.com/b.yaml"}]}""",
        )

        val sanitized = store.loadSanitized()

        // 迁移照旧生效（名字归一）…
        assertTrue(sanitized.state.groups.containsKey("📲 社交媒体"))
        // …但迁移**不能**顺手删掉被隐藏项（这就是 write(clean) 的陷阱）
        assertTrue("迁移后磁盘上那条自定义组必须还在", store.stateFile().readText().contains("Ⓜ️ 微软云盘"))
        assertEquals(listOf("Ⓜ️ 微软云盘"), store.loadSanitized().hiddenCustom.map { it.name })
    }

    @Test
    fun `用户编辑保存后磁盘上被隐藏的自定义组仍在`() {
        writeRaw("""{"version":1,"enabled":true,"custom":[{"name":"Ⓜ️ 微软云盘","url":"https://x.example.com/b.yaml"}]}""")
        store.loadSanitized()

        store.setEnabled(false)
        assertTrue("开关保存不能抹掉被隐藏项", store.stateFile().readText().contains("Ⓜ️ 微软云盘"))
        store.setGroupOrder(listOf("📺 流媒体"))
        assertTrue("拖动顺序保存不能抹掉被隐藏项", store.stateFile().readText().contains("Ⓜ️ 微软云盘"))
        assertTrue(store.addCustomGroup(RoutingCustomGroup(name = "另一条", url = "https://x.example.com/c.yaml")))
        assertTrue("新增自定义组不能抹掉被隐藏项", store.stateFile().readText().contains("Ⓜ️ 微软云盘"))
        store.removeCustomGroup("另一条")
        assertTrue("删除另一条不能抹掉被隐藏项", store.stateFile().readText().contains("Ⓜ️ 微软云盘"))
    }
}
