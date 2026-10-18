// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.github.kr328.clash.service.KernelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 内核 VPN 状态机单测（对齐 kernel-service 的 KernelState）。
 * 核心语义：TUN 与配置都就绪才进 Running；失败后晚到的就绪事件不能覆盖 Failed。
 */
class KernelStateTest {

    @Before
    fun setUp() {
        KernelState.starting()
    }

    @Test
    fun `TUN 与配置都就绪才进入 Running`() {
        KernelState.tunAttached()
        assertEquals(KernelState.State.Starting, KernelState.state)
        assertFalse(KernelState.isRunning())

        KernelState.profileLoaded()
        assertEquals(KernelState.State.Running, KernelState.state)
        assertTrue(KernelState.isRunning())
    }

    @Test
    fun `配置先于 TUN 就绪也能进入 Running`() {
        KernelState.profileLoaded()
        assertEquals(KernelState.State.Starting, KernelState.state)

        KernelState.tunAttached()
        assertTrue(KernelState.isRunning())
    }

    @Test
    fun `TUN 重建期间回到 Starting`() {
        KernelState.tunAttached()
        KernelState.profileLoaded()

        KernelState.restartingTun()
        assertEquals(KernelState.State.Starting, KernelState.state)
        assertFalse(KernelState.isRunning())

        KernelState.tunAttached()
        assertTrue(KernelState.isRunning())
    }

    @Test
    fun `失败后晚到的就绪事件不会覆盖 Failed`() {
        KernelState.failed("startTun failed")

        KernelState.tunAttached()
        KernelState.profileLoaded()

        assertEquals(KernelState.State.Failed, KernelState.state)
        assertEquals("startTun failed", KernelState.reason)
        assertFalse(KernelState.isRunning())
    }

    @Test
    fun `销毁时保留失败原因 正常停止清空状态`() {
        KernelState.failed("boom")
        KernelState.destroyed("boom")
        assertEquals(KernelState.State.Failed, KernelState.state)
        assertEquals("boom", KernelState.reason)

        KernelState.starting()
        KernelState.destroyed(null)
        assertEquals(KernelState.State.Stopped, KernelState.state)
        assertNull(KernelState.reason)
    }
}
