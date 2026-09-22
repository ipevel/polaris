// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：Android Log 固定 TAG 封装，内核各模块共用

package com.github.kr328.clash.common.log

object Log {
    private const val TAG = "ClashMetaForAndroid"

    fun i(message: String, throwable: Throwable? = null) =
        android.util.Log.i(TAG, message, throwable)

    fun w(message: String, throwable: Throwable? = null) =
        android.util.Log.w(TAG, message, throwable)

    fun e(message: String, throwable: Throwable? = null) =
        android.util.Log.e(TAG, message, throwable)

    fun d(message: String, throwable: Throwable? = null) =
        android.util.Log.d(TAG, message, throwable)

    fun v(message: String, throwable: Throwable? = null) =
        android.util.Log.v(TAG, message, throwable)

    fun f(message: String, throwable: Throwable) =
        android.util.Log.wtf(message, throwable)
}
