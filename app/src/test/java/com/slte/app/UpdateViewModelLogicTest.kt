// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app

import com.slte.app.ui.screen.about.compareVersions
import com.slte.app.ui.screen.about.isUpdatePayloadConsistent
import com.slte.app.ui.screen.about.shouldShowUpdateDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID_SHA = "5d4ed73a87310ed8766dce451602db8af5205aba8f66866301111464a23f46be"

private const val RELEASE_BASE = "https://github.com/ipevel/polaris/releases/download"

private fun apkUrlOf(version: String) = "$RELEASE_BASE/v$version/Polaris-$version.apk"

/** 默认给一份自洽的载荷（直链与校验和都对应 updateVersion），只测「是否提示」的既有分支。 */
private fun shown(
    updateVersion: String,
    currentVersion: String,
    force: Boolean = false,
    dismissedInSession: Boolean = false,
    manual: Boolean = false,
    apkUrl: String? = apkUrlOf(updateVersion),
    apkSha256: String? = VALID_SHA,
) = shouldShowUpdateDialog(updateVersion, currentVersion, force, dismissedInSession, manual, apkUrl, apkSha256)

class UpdateViewModelLogicTest {
    @Test
    fun manualCheckAlwaysResponds() {
        assertTrue(shown("1.1.0", "1.0.0", dismissedInSession = true, manual = true))
        assertFalse(shown("1.1.0", "1.0.0", dismissedInSession = true))
        assertTrue(shown("1.1.0", "1.0.0"))
    }

    @Test
    fun forceAlwaysShows() {
        assertTrue(shown("1.1.0", "1.0.0", force = true, dismissedInSession = true))
    }

    @Test
    fun noUpdateMeansNoDialog() {
        assertFalse(shown("1.0.0", "1.0.0", manual = true))
        assertFalse(shown("", "1.0.0", manual = true))
        assertTrue(compareVersions("1.0.0-debug", "1.0.0") == 0)
    }

    @Test
    fun 载荷不自洽时一律不提示更新() {
        // 版本号已提前改成 1.1.0，但直链与校验和仍是 1.0.0 的 —— 曾经出现过的发布中间态。
        // 此时若提示更新，用户下载到的是旧包，而旧包哈希恰好等于旧校验和，校验反而通过，装完又提示，无限循环。
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkUrl = apkUrlOf("1.0.0")))
        // 校验和缺失、为空、长度或字符不合法
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkSha256 = null))
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkSha256 = ""))
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkSha256 = "abc123"))
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkSha256 = "z".repeat(64)))
        // 直链缺失或非 https
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkUrl = null))
        assertFalse(shown("1.1.0", "1.0.0", manual = true, apkUrl = "http://example.com/v1.1.0/a.apk"))
        // force 不能绕过自洽性，否则用户会被锁在不可关闭的更新提示里
        assertFalse(shown("1.1.0", "1.0.0", force = true, apkUrl = apkUrlOf("1.0.0")))
        assertFalse(shown("1.1.0", "1.0.0", force = true, apkSha256 = ""))
    }

    @Test
    fun 直链中的版本号须有边界() {
        assertFalse(shown("1.4.14", "1.0.0", manual = true, apkUrl = apkUrlOf("1.4.140")))
        assertTrue(shown("1.4.14", "1.0.0", manual = true, apkUrl = apkUrlOf("1.4.14")))
    }

    @Test
    fun 自洽判定接受大小写十六进制与大写V前缀() {
        assertTrue(isUpdatePayloadConsistent("1.4.14", apkUrlOf("1.4.14"), VALID_SHA.uppercase()))
        assertTrue(isUpdatePayloadConsistent("v1.4.14", apkUrlOf("1.4.14"), VALID_SHA))
        assertFalse(isUpdatePayloadConsistent("1.4.14", apkUrlOf("1.4.14"), VALID_SHA.dropLast(1)))
    }

    @Test
    fun compareVersions_大小与各段差异() {
        assertTrue(compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.0.1", "1.0.0") > 0)
        assertEquals(0, compareVersions("1.0.0", "1.0.0"))

        assertEquals(0, compareVersions("v1.2.3", "1.2.3"))
        assertEquals(0, compareVersions("1.2.3-rc1", "1.2.3"))

        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertTrue(compareVersions("1.0.1", "1.0") > 0)

        assertTrue(compareVersions("abc", "1.0.0") < 0)
        assertTrue(compareVersions("", "0.0.1") < 0)
    }
}
