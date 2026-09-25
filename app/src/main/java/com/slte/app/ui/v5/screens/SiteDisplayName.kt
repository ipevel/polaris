// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

/**
 * 顶栏/关于页的站点显示名（纯函数，可 JVM 单测）。
 *
 * 站点名的来源链已在数据层完成合并并写进 [com.slte.app.ui.screen.main.DashboardData.siteName]：
 * 面板 `guest/comm/config` 的 `app_name ?: title ?: site_name`（见 XiaoV2bDto/XboardDto），
 * 面板未配置时退化为面板域名的首段（`parseHostnameForSiteName`），再兜底为订阅响应头
 * `profile-title`。**UI 层不得重复实现这条链**，否则会出现第二个真源；这里只做
 * 「空值回退 + 显示安全清洗」。
 *
 * 清洗是必要的：站点名是面板可控字符串（DTO 侧无长度/字符集约束），而它会被渲染到
 * 顶栏最显眼的位置——换行会把单行标题撑成两行，双向控制符（U+202A–U+202E / U+2066–U+2069）
 * 会造成视觉反转与品牌伪装。这里是显示边界上的最后一道防线。
 */
internal fun siteDisplayName(
    raw: String?,
    fallback: String,
    maxLength: Int = MAX_SITE_NAME_LENGTH,
): String {
    val cleaned =
        raw
            .orEmpty()
            .filterNot { it.isWhitespaceBrandUnsafe() }
            .replace(WHITESPACE_RUN, " ")
            .trim()
    if (cleaned.isEmpty()) return fallback
    // 按 code point 截断，避免把代理对（emoji）截断成乱码
    val capped =
        if (cleaned.codePointCount(0, cleaned.length) <= maxLength) {
            cleaned
        } else {
            val end = cleaned.offsetByCodePoints(0, maxLength)
            cleaned.substring(0, end)
        }
    return capped.ifBlank { fallback }
}

/** 换行/制表/双向控制符/BOM：既影响单行排版，也可用于视觉伪装。 */
private fun Char.isWhitespaceBrandUnsafe(): Boolean = this in UNSAFE_BRAND_CHARS

/**
 * 需要从站点名里剔除的字符：
 * 换行/回车/制表（会把单行标题撑成两行）、零宽字符与 BOM（U+200B/U+FEFF，伪造"看不见"的差异）、
 * 双向标记与隔离符（U+200E/U+200F、U+202A–U+202E、U+2066–U+2069，可让文字视觉反转做品牌伪装）。
 */
private val UNSAFE_BRAND_CHARS: Set<Char> =
    buildSet {
        add('\n')
        add('\r')
        add('\t')
        add('\u200B') // 零宽空格
        add('\u200E') // LRM
        add('\u200F') // RLM
        add('\uFEFF') // BOM / 零宽不换行空格
        for (code in 0x202A..0x202E) add(code.toChar()) // 双向嵌入/覆盖
        for (code in 0x2066..0x2069) add(code.toChar()) // 双向隔离
    }

private val WHITESPACE_RUN = Regex(" {2,}")

/** 站点名显示上限（字符数，按 code point 计）。超长由顶栏 ellipsis 收尾。 */
internal const val MAX_SITE_NAME_LENGTH = 32
