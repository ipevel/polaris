// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：HTML 解析版本兼容扩展（Html.fromHtml）

@file:Suppress("DEPRECATION")

package com.github.kr328.clash.common.compat

import android.os.Build
import android.text.Html
import android.text.Spanned

fun fromHtmlCompat(content: String): Spanned {
    return if (Build.VERSION.SDK_INT >= 24) {
        Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(content)
    }
}
