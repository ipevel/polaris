// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizationGuardTest {
    @Test
    fun 日志中的异常消息必须脱敏() {
        val root = File("src/main/java")
        assertTrue("源码目录不存在: ${root.absolutePath}", root.isDirectory)

        // .message 后接词边界，避免误伤 .messages / .messageCount 等成员访问
        val exceptionMessageUsage = Regex("""\.message\b""")
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) ->
                            line.contains("AppLog.") &&
                                exceptionMessageUsage.containsMatchIn(line) &&
                                !line.contains("sanitizeLog(") &&
                                !line.contains("sanitize(")
                        }.map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
                }.toList()

        assertTrue(
            "日志里的异常消息必须经过 sanitizeLog；未处理的位置：\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
