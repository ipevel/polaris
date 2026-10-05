// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

/**
 * 把 GitHub release body（markdown）转成手机上可读的纯文本。
 *
 * 背景：更新弹窗之前直接显示 body 源码，表格/代码块/标题符号全堆在一起，
 * 用户反馈"乱码"。这里做轻量清洗（非完整 markdown 渲染，够 changelog 用）：
 * - 代码块：去掉围栏，保留内容
 * - 表格：转成"项：值"逐行
 * - 标题/引用/列表：去掉标记符号，保留文字
 * - 链接 `[文字](url)`：只留文字；行内代码/加粗/斜体：只留文字
 */
internal fun markdownToPlainText(markdown: String): String {
    val out = StringBuilder()
    var inCodeBlock = false
    var inTable = false

    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        // 代码块围栏
        if (line.trimStart().startsWith("```")) {
            inCodeBlock = !inCodeBlock
            continue
        }
        if (inCodeBlock) {
            out.appendLine(line.trim())
            continue
        }
        val t = line.trim()
        if (t.isEmpty()) {
            inTable = false
            out.appendLine()
            continue
        }
        // 表格行
        if (t.startsWith("|") && t.endsWith("|")) {
            val cells = t.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            // 分隔行 | --- | 跳过
            if (cells.all { it.all { ch -> ch == '-' || ch == ':' } }) continue
            inTable = true
            out.appendLine(
                if (cells.size >= 2) {
                    cells[0].cleanInline() + "：" + cells.drop(1).joinToString(" / ") { it.cleanInline() }
                } else {
                    cells.joinToString(" ") { it.cleanInline() }
                },
            )
            continue
        }
        inTable = false
        // 标题
        val header = Regex("^#{1,6}\\s+").replace(t, "")
        // 引用
        val quote = Regex("^>\\s?").replace(header, "")
        // 列表
        val list = Regex("^(\\d+[.)]|[-*+])\\s+").replace(quote, "• ")
        // 分隔线
        if (Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(list)) continue
        out.appendLine(list.cleanInline())
    }

    // 连续 3+ 空行压成 2 个
    return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
}

/** 行内标记清洗：链接/行内代码/加粗/斜体只留文字。 */
private fun String.cleanInline(): String {
    var s = this
    s = Regex("\\[([^\\]]+)]\\([^)]+\\)").replace(s, "$1")
    s = Regex("`([^`]+)`").replace(s, "$1")
    s = Regex("(\\*\\*|__)(.+?)\\1").replace(s, "$2")
    s = Regex("(\\*|_)(.+?)\\1").replace(s, "$2")
    return s.trim()
}
