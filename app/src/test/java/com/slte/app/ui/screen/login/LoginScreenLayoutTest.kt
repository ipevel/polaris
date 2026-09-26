// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 认证页压缩判据（第 3 轮 N4/N5）。
 *
 * 真机实测的两个症状：
 * - 360×640dp、标准字体：「记住密码 / 忘记密码？」bounds 高度只剩 **4px**，贴在下沿；
 * - `fontScale = 1.5`：「登录」按钮只剩顶部一条边在屏内。
 *
 * 所以判据必须同时考虑"视口高度"与"字体缩放"：字体放大后等效高度变小，同样要进压缩态。
 */
class LoginScreenLayoutTest {

    @Test
    fun `360x640 标准字体进入压缩态`() {
        assertTrue(needsCompactAuthLayout(availableHeightDp = 640, fontScale = 1f))
    }

    @Test
    fun `字体放大后同样的视口也要压缩`() {
        // 720dp 在 1.0 倍下从容；1.5 倍下等效 480dp，必须压缩
        assertFalse(needsCompactAuthLayout(availableHeightDp = 720, fontScale = 1f))
        assertTrue(needsCompactAuthLayout(availableHeightDp = 720, fontScale = 1.5f))
    }

    @Test
    fun `大屏标准字体不压缩，保持原有留白`() {
        assertFalse(needsCompactAuthLayout(availableHeightDp = 915, fontScale = 1f))
        assertFalse(needsCompactAuthLayout(availableHeightDp = 800, fontScale = 1f))
    }

    @Test
    fun `非法输入不误判`() {
        assertFalse("拿不到视口高度时不该擅自压缩", needsCompactAuthLayout(availableHeightDp = 0, fontScale = 1f))
        assertFalse(needsCompactAuthLayout(availableHeightDp = -1, fontScale = 1f))
        assertTrue("fontScale 异常小时也不能放大到超过阈值", needsCompactAuthLayout(availableHeightDp = 600, fontScale = 0f))
    }
}
