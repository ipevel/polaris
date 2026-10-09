// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.model

/**
 * 注册邮箱后缀白名单，来自后端注册配置里的 `email_whitelist_suffix`。
 *
 * [suffixes] 为空表示后端未启用白名单，此时不做任何限制（注册页表现与从前一致）。
 * 启用时注册页只让用户填 `@` 前面的部分，后缀由用户在列表里选——不给"输入了才发现不允许"的机会。
 */
data class EmailWhitelist(
    val suffixes: List<String> = emptyList(),
) {
    val isEnabled: Boolean
        get() = suffixes.isNotEmpty()

    /** 默认后缀：进入页面时先选中的那个。 */
    val defaultSuffix: String?
        get() = suffixes.firstOrNull()

    companion object {
        val None = EmailWhitelist()
    }
}
