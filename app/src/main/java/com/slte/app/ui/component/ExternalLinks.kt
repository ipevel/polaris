// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.slte.app.utils.AppLog

/**
 * 远端（面板）返回的 Telegram 链接允许的域名后缀。
 *
 * 面板是被信任度最低的数据来源之一：它可以是自建站，也可能被劫持。若把面板下发的字符串
 * 直接交给隐式 Intent，攻击者可以下发 `intent://…#Intent;component=…;end`、`file://`、
 * `content://` 来做任意组件启动或本地文件外泄（CWE-939）。除了协议白名单，域名白名单还能
 * 阻止"把用户静默重定向到任意站点"这种钓鱼面。
 */
private val TELEGRAM_HOST_SUFFIXES = listOf("t.me", "telegram.me", "telegram.dog")

/** [url] 是否是允许打开的 Telegram 链接：必须 http(s) 且命中域名白名单。 */
internal fun isSupportedTelegramUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (!isSupportedLinkUrl(trimmed)) return false
    val host = runCatching { Uri.parse(trimmed).host?.lowercase() }.getOrNull() ?: return false
    return TELEGRAM_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

/**
 * 打开外部链接。
 *
 * 两条硬约束：
 * 1. **只接受 http/https**（[isSupportedLinkUrl]）。`intent://` / `file://` / `content://` /
 *    `javascript:` 一律拒绝，否则被劫持的远端可以任意启动组件或读取本地文件；
 * 2. 不抛异常、不崩。这里**不用** `Intent.resolveActivity` 预判：API 30+ 的包可见性会让它在
 *    明明有浏览器时也返回 null（需要 `<queries>` 声明），从而把正常的 Telegram 入口变成
 *    "点了没反应"。改用 try/catch 捕获 `ActivityNotFoundException` 等，行为更可靠。
 */
internal fun openExternalUrl(context: Context, url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (!isSupportedLinkUrl(trimmed)) {
        AppLog.w("Polaris-Link", "拒绝打开非 http(s) 链接: ${trimmed.take(32)}")
        return false
    }
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
        true
    }.getOrElse { e ->
        AppLog.w("Polaris-Link", "打开链接失败: ${e.javaClass.simpleName}")
        false
    }
}
