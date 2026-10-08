// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较：`BuildConfig.VERSION_NAME` 带 `-debug` 后缀，解析必须先剥后缀再切段。
 *
 * 回归背景：直接切段会让末段变成 `"1-debug"`、`toIntOrNull()` 退化成 0，
 * 于是 `1.6.1-debug` 被解析成 `1.6.0`——补丁位被静默抹掉，
 * `1.6.1` 的更新提示再也不会出现（debug 包上「检查更新」永远说已是最新）。
 */
class AppUpdateCheckerTest {

    private val checker = AppUpdateChecker()

    @Test
    fun `带 debug 后缀的当前版本不吃掉补丁位`() {
        assertFalse("1.6.1 不比 1.6.1-debug 新", checker.isNewer("1.6.1", "1.6.1-debug"))
        assertFalse("1.6.0 不比 1.6.1-debug 新", checker.isNewer("1.6.0", "1.6.1-debug"))
        assertTrue("1.6.2 比 1.6.1-debug 新", checker.isNewer("1.6.2", "1.6.1-debug"))
        assertTrue("1.7.0 比 1.6.1-debug 新", checker.isNewer("1.7.0", "1.6.1-debug"))
    }

    @Test
    fun `构建元数据后缀不参与比较`() {
        assertFalse(checker.isNewer("1.7.4+build.5", "1.7.4"))
        assertFalse(checker.isNewer("1.7.4", "1.7.4+ci"))
    }

    @Test
    fun `段数不足按 0 补齐`() {
        assertTrue(checker.isNewer("1.7", "1.6.9"))
        assertFalse(checker.isNewer("1.7.0", "1.7"))
        assertTrue(checker.isNewer("1.7.1", "1.7"))
    }

    @Test
    fun `相同版本不算新版本`() {
        assertFalse(checker.isNewer("1.7.4", "1.7.4"))
    }
}
