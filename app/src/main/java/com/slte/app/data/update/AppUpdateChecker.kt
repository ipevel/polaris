// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** GitHub Releases 里的一条版本信息。 */
data class ReleaseInfo(
    /** 去掉 `v` 前缀的版本号，如 `1.6.1` */
    val version: String,
    /** 更新日志（release body，可能为空） */
    val changelog: String,
    /** APK 下载地址 */
    val apkUrl: String,
    /** APK 文件名 */
    val apkFileName: String,
)

/**
 * 应用内更新：从 GitHub Releases 检查新版本。
 *
 * 版本来源固定为 `ipevel/polaris` 的 latest release，APK 取首个 `.apk` 资产。
 * 返回 `null` 表示当前已是最新；抛异常表示检查失败（网络/解析）。
 */
@Singleton
class AppUpdateChecker
@Inject
constructor() {
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    suspend fun checkLatest(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request =
            Request
                .Builder()
                .url("https://api.github.com/repos/ipevel/polaris/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub API HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val latest = json.getString("tag_name").removePrefix("v")
            if (!isNewer(latest, currentVersion)) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return@withContext ReleaseInfo(
                        version = latest,
                        changelog = json.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        apkFileName = name,
                    )
                }
            }
            throw IOException("release 无 APK 资产")
        }
    }

    /** 纯数字段比较：`1.6.1` > `1.6.0`；段数不足补 0。 */
    fun isNewer(
        latest: String,
        current: String,
    ): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
