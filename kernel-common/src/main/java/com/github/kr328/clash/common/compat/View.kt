// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

@file:Suppress("DEPRECATION")

package com.github.kr328.clash.common.compat

import android.os.Build
import android.widget.TextView
import androidx.annotation.StyleRes

var TextView.textAppearance: Int
    get() = throw UnsupportedOperationException("set value only")
    set(@StyleRes value) {
        if (Build.VERSION.SDK_INT >= 23) {
            setTextAppearance(value)
        } else {
            setTextAppearance(context, value)
        }
    }