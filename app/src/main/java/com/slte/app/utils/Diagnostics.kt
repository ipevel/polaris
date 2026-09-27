// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import android.os.Build
import com.slte.app.BuildConfig
import com.slte.app.data.local.ApiUrlStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 诊断上下文：导出日志时附在文件头部，回答「这条日志是在什么环境里产生的」。
 *
 * 为什么单独做：此前导出文件只有 应用版本 / Android / 设备 三项。遇到「只有某个面板
 * 才复现」的问题（例如流量明细接口），缺的正是**后端类型**这个第一分诊字段——而让用户
 * 手动复述面板地址既麻烦又容易出错。后端类型是运行时按登录时的识别结果路由的
 * （见 `DualBackendAuthApi.active()`），所以只能从 [ApiUrlStore] 实时读，不能取编译期常量。
 *
 * 取值会经 [AppLog.sanitize] 二次脱敏（面板主机等打码），可安全外发。
 */
@Singleton
class Diagnostics
@Inject
constructor(
    private val apiUrlStore: ApiUrlStore,
) {
    /** 附加诊断字段；顺序稳定，便于人工比对两份导出文件。 */
    fun extra(): Map<String, String> = buildMap {
        put("后端类型", apiUrlStore.backendType.ifBlank { "(未识别)" })
        apiUrlStore.currentUrl?.let { put("面板地址", it) }
        put("构建类型", if (BuildConfig.DEBUG) "debug" else "release")
        put("ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "(未知)")
    }
}
