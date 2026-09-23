// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

/**
 * 读取内核累计流量（上传, 下载），字节为单位。
 * 原始 Long 的高 32 位为上传、低 32 位为下载，每段为「2bit 指数 + 30bit 数据」，
 * 解码规则与 core 模块 `com.github.kr328.clash.core.util.Traffic` 保持一致。
 * AIDL 同步调用需在 ioDispatcher 上执行，由调用方负责切换。
 */
suspend fun KernelProxy.trafficTotal(): Pair<Long, Long>? = safe(null, "trafficTotal") {
    val clash = manager.clash() ?: return@safe null
    val raw = clash.queryTrafficTotal()
    decodeTraffic(raw ushr 32) to decodeTraffic(raw and 0xFFFFFFFF)
}

private fun decodeTraffic(value: Long): Long {
    val type = (value ushr 30) and 0x3
    val data = value and 0x3FFFFFFF
    return when (type) {
        0L -> data
        1L -> data * 1024
        2L -> data * 1024 * 1024
        3L -> data * 1024 * 1024 * 1024
        else -> 0L
    }
}
