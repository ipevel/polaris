// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.geo

import android.content.Context
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一份规则库资源：落地文件名 + 远端地址。 */
data class GeoResource(
    val name: String,
    val url: String,
)

/**
 * 规则库（geodata）资源清单。地址与面板「资源」页一致：`appshubcc/bett-rules`。
 *
 * 三份 geodata 已经随 APK 分发（`app/src/main/assets/`），打包脚本
 * `design/windows/app/scripts/fetch-geo.py` 也用同一份清单，两端不会漂移。
 * 清单在这里还承担第二个职责：某份文件缺失或损坏时（解压失败、被清理工具删掉、
 * 换机迁移丢文件）能按地址补齐，不必等发版。
 *
 * 体积合计约 24 MB，所以只在「本地没有」时才下载，绝不每次启动都拉一遍。
 */
object GeoResources {
    const val SOURCE = "appshubcc/bett-rules"

    val ALL: List<GeoResource> =
        listOf(
            GeoResource("geosite.dat", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geosite.dat"),
            GeoResource("geoip.metadb", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geoip.metadb"),
            GeoResource("ASN.mmdb", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/GeoLite2-ASN.mmdb"),
        )

    /**
     * MRS 规则集打包（mihomo 的 `.mrs` 二进制格式）。
     *
     * 只登记不下载：内核当前用的是远程 RULE-SET providers（`routing_table.go`
     * 的 `Providers`），不消费 `.mrs`；而且这是 7z 包，App 里没有解包器。
     * 等内核真的切到 MRS 规则集时，这里再接上下载与解压。
     */
    const val MRS_BUNDLE_URL =
        "https://github.com/appshubcc/bett-rules/releases/download/latest/BundleMRS.7z"

    private const val TAG = "GeoResource"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 把缺失的规则库补齐。
     *
     * 已有文件一律不碰：geodata 是内核启动的硬依赖，覆盖正在使用的文件会让
     * 下一次连接直接起不来。失败只记日志，不抛——离线时应当照常启动。
     *
     * @return 实际补齐的文件名
     */
    fun syncMissing(context: Context): List<String> {
        val dir = File(context.filesDir, "clash").apply { mkdirs() }
        val fixed = mutableListOf<String>()
        for (res in ALL) {
            val target = File(dir, res.name)
            if (target.exists() && target.length() > 0) continue
            val tmp = File(dir, "${res.name}.part")
            try {
                download(res, tmp)
                if (!tmp.renameTo(target)) throw IllegalStateException("rename failed")
                AppLog.i(TAG, "geo sync: restored ${res.name} (${target.length()} bytes)")
                fixed += res.name
            } catch (e: Exception) {
                AppLog.w(TAG, "geo sync failed: ${res.name}: ${sanitizeLog(e.message ?: e.javaClass.simpleName)}")
                tmp.delete()
            }
        }
        return fixed
    }

    private fun download(res: GeoResource, tmp: File) {
        val request = Request.Builder().url(res.url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("empty body")
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
        }
        if (tmp.length() <= 0) throw IllegalStateException("downloaded 0 bytes")
    }
}
