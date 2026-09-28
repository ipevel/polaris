// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import android.app.Application
import android.content.Intent
import com.slte.app.support.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 「从非 Activity 上下文打开外链」回归护栏。
 *
 * 为什么要有这个测试文件：更新改造后 `UpdateViewModel` 用注入的 `@ApplicationContext` 调
 * `openExternalUrl`，而实现直接 `context.startActivity(...)` 没带 `FLAG_ACTIVITY_NEW_TASK`，
 * 于是系统抛 `AndroidRuntimeException`、被 `runCatching` 吞掉，用户看到的是「点了没反应」
 * （真机 1.5.12 日志：`Polaris-Link: 打开链接失败: AndroidRuntimeException`）。
 *
 * 这类缺陷真机走查很容易漏（更新弹窗正常出现、按钮也有按压反馈），只有断言 Intent 的 flag 才现形，
 * 所以在这里用 Application 上下文把契约钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class ExternalLinksTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `非 Activity 上下文打开 https 链接会带上 NEW_TASK`() {
        val url = "https://github.com/ipevel/polaris/releases"
        assertTrue(openExternalUrl(app, url))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(url, started.data.toString())
        assertTrue(
            "缺少 FLAG_ACTIVITY_NEW_TASK 时非 Activity 上下文会抛 AndroidRuntimeException",
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
    }

    @Test
    fun `拒绝非 http(s) 链接且不启动任何 Activity`() {
        assertFalse(openExternalUrl(app, "intent://evil#Intent;scheme=https;end"))
        assertFalse(openExternalUrl(app, "file:///etc/hosts"))
        assertFalse(openExternalUrl(app, "javascript:alert(1)"))
        assertFalse(openExternalUrl(app, null))
        assertFalse(openExternalUrl(app, "   "))

        assertNull(shadowOf(app).nextStartedActivity)
    }
}
