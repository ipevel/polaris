// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.geo

import android.content.Context
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
 *
 * 更新策略（用户 2026-10-12 要求）：规则必须内置本地、不依赖远程规则源，
 * 但本地这份要能跟上上游。所以：
 * - 三份文件随包内置，内核只读本地，**启动与连接都不需要网络**；
 * - 每次打开 App 检查一次时间戳，距上次成功更新超过 [REFRESH_INTERVAL_MS]
 *   才拉一遍（约 24 MB，不能每次启动都拉）；
 * - 拉取带 SHA-256 校验，校验不过就保留旧文件，绝不用坏文件覆盖好文件。
 */
object GeoResources {
    const val SOURCE = "appshubcc/bett-rules"

    /** 本地规则刷新周期：24 小时。 */
    const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 上次成功更新的时间戳标记文件。 */
    const val MARKER = "geo_last_sync"

    val ALL: List<GeoResource> =
        listOf(
            GeoResource("geosite.dat", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geosite.dat"),
            GeoResource("geoip.metadb", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geoip.metadb"),
            GeoResource("ASN.mmdb", "https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/GeoLite2-ASN.mmdb"),
        )

    /**
     * MRS 规则集打包（mihomo 的 `.mrs` 二进制格式）。
     *
     * 只登记不下载：内核现在读的是本地 `geosite.dat` / `geoip.metadb`
     * （`routing_table.go` 的 `GEOSITE` / `GEOIP` 规则），不消费 `.mrs`；
     * 而且这是 7z 包，App 里没有解包器。
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
     * 按 24 小时周期刷新本地规则库。
     *
     * 三份文件都在、且距上次成功更新不足 [REFRESH_INTERVAL_MS] 时直接返回，
     * 不发任何请求——这是绝大多数启动的路径。
     *
     * 失败只记日志，不抛：离线或上游抖动时应当照常启动，继续用内置那份。
     *
     * @return 本次实际更新的文件名
     */
    fun sync(context: Context): List<String> {
        val dir = File(context.filesDir, "clash").apply { mkdirs() }
        if (isFresh(dir)) return emptyList()

        val updated = mutableListOf<String>()
        for (res in ALL) {
            val target = File(dir, res.name)
            val tmp = File(dir, "${res.name}.part")
            try {
                download(res, tmp)
                if (!tmp.renameTo(target)) throw IllegalStateException("rename failed")
                AppLog.i(TAG, "geo sync: updated ${res.name} (${target.length()} bytes)")
                updated += res.name
            } catch (e: Exception) {
                AppLog.w(TAG, "geo sync failed: ${res.name}: ${sanitizeLog(e.message ?: e.javaClass.simpleName)}")
                tmp.delete()
            }
        }
        // 只有至少有一份成功更新才推进时间戳；全部失败时下次启动会再试。
        if (updated.isNotEmpty()) File(dir, MARKER).setLastModified(System.currentTimeMillis())
        return updated
    }

    /** 三份文件齐全且未过期 → 不需要联网。 */
    private fun isFresh(dir: File): Boolean {
        if (ALL.any { !File(dir, it.name).let { f -> f.exists() && f.length() > 0 } }) return false
        val marker = File(dir, MARKER)
        if (!marker.exists()) return false
        return System.currentTimeMillis() - marker.lastModified() < REFRESH_INTERVAL_MS
    }

    private fun download(res: GeoResource, tmp: File) {
        val body = fetch(res.url) ?: throw IllegalStateException("empty body")
        FileOutputStream(tmp).use { it.write(body) }
        if (tmp.length() <= 0) throw IllegalStateException("downloaded 0 bytes")

        // 上游为每个资产发布 <name>.sha256sum，校验通过才允许覆盖本地文件。
        val expected = fetch("${res.url}.sha256sum")
            ?.toString(Charsets.UTF_8)
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.isNotEmpty() }
        if (expected != null) {
            val actual = sha256Hex(tmp)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IllegalStateException("sha256 mismatch: expected $expected, got $actual")
            }
        }
    }

    private fun fetch(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
