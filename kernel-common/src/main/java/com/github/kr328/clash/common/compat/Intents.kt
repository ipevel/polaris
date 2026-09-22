// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：PendingIntent flags 版本兼容（FLAG_MUTABLE 等）

package com.github.kr328.clash.common.compat

import android.app.PendingIntent
import android.os.Build

fun pendingIntentFlags(flags: Int, mutable: Boolean = false): Int {
    return if (Build.VERSION.SDK_INT >= 24) {
        if (Build.VERSION.SDK_INT > 30 && mutable) {
            flags or PendingIntent.FLAG_MUTABLE
        } else {
            flags or PendingIntent.FLAG_IMMUTABLE
        }
    } else {
        flags
    }
}
