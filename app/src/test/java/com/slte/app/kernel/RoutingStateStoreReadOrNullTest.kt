// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 复核项 #2：显示映射必须区分「没有自建组」与「读不出来」。
 *
 * `load()` 把解析失败也吞成空状态，于是两种情况都变成空集，而
 * `groupDisplayName(name, emptySet())` 是**照常映射**——一个叫「Ⓜ️ 微软」的自建组在
 * 文件损坏时会被误改名。[RoutingStateStore.readOrNull] 把「读不出来」表达成 null，
 * 渲染层据此保守不映射。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class RoutingStateStoreReadOrNullTest {

    private lateinit var store: RoutingStateStore

    @Before
    fun setUp() {
        store = RoutingStateStore(RuntimeEnvironment.getApplication())
        store.stateFile().delete()
    }

    @Test
    fun `文件缺失时返回空状态而不是 null`() {
        val state = store.readOrNull()

        assertNotNull("文件缺失 = 确实没有自建组，不能当成读不出来", state)
        assertTrue(state!!.custom.isEmpty())
    }

    @Test
    fun `解析失败时返回 null 以便调用方保守不映射`() {
        store.stateFile().parentFile?.mkdirs()
        store.stateFile().writeText("{ this is not json")

        assertNull("解析失败必须与「没有自建组」区分开", store.readOrNull())
    }

    @Test
    fun `正常文件按原样读出含自建组`() {
        store.stateFile().parentFile?.mkdirs()
        store.stateFile().writeText(
            "{\"version\":1,\"custom\":[{\"name\":\"Ⓜ️ 微软\",\"url\":\"https://x.example.com/a.yaml\"}]}",
        )

        assertEquals(listOf("Ⓜ️ 微软"), store.readOrNull()?.custom?.map { it.name })
    }

    /** 磁盘内容摘要：断言「读入口不改盘」。 */
    private fun sha256(): String = MessageDigest.getInstance("SHA-256").digest(store.stateFile().readBytes())
        .joinToString("") { "%02x".format(it) }

    /**
     * 语义交叉检查（PR #14「清洗只读不回写」合入 main 之后）：
     * [RoutingStateStore.readOrNull] 读的是**原始未清洗**内容——保留名冲突那条自定义组照样读得到；
     * 清洗只影响 `loadSanitized` 的返回态与 hidden 列表，两个入口都**不写盘**。
     */
    @Test
    fun `readOrNull 读原始未清洗内容，与 loadSanitized 的 hidden 语义互不影响`() {
        store.stateFile().parentFile?.mkdirs()
        store.stateFile().writeText(
            """{"version":1,"enabled":true,"custom":[""" +
                """{"name":"我的组","url":"https://x.example.com/a.yaml"},""" +
                """{"name":"Ⓜ️ 微软云盘","url":"https://x.example.com/b.yaml"}],""" +
                """"direct_domains":["panel.example.cn","bad domain"]}""",
        )
        val before = sha256()

        // readOrNull 不过滤：保留名冲突那条与非法域名都原样读得到
        val raw = store.readOrNull()
        assertEquals(listOf("我的组", "Ⓜ️ 微软云盘"), raw?.custom?.map { it.name })
        assertEquals(listOf("panel.example.cn", "bad domain"), raw?.directDomains)

        // loadSanitized 只在返回态里隐藏，并把被隐藏条目带进 hidden 列表
        val sanitized = store.loadSanitized()
        assertEquals(listOf("我的组"), sanitized.state.custom.map { it.name })
        assertEquals(listOf("Ⓜ️ 微软云盘"), sanitized.hiddenCustom.map { it.name })
        assertEquals(listOf("bad domain"), sanitized.hiddenDomains)

        assertEquals("两个读入口都不得改盘", before, sha256())
        assertEquals(listOf("我的组", "Ⓜ️ 微软云盘"), store.readOrNull()?.custom?.map { it.name })
    }
}
