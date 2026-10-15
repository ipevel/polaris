// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
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
}
