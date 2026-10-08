// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
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
    /**
     * 发布说明里公布的 APK SHA-256（64 位十六进制），用于下载后校验。
     * 从 changelog 里按「哈希 + 文件名」的固定格式提取；提取不到为 `null`，
     * 此时跳过校验（不阻断安装）。
     */
    val expectedSha256: String? = null,
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
            val changelog = json.optString("body").trim()
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return@withContext ReleaseInfo(
                        version = latest,
                        changelog = changelog,
                        apkUrl = asset.getString("browser_download_url"),
                        apkFileName = name,
                        expectedSha256 = extractSha256(changelog, name),
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
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /**
     * 版本号解析：剥掉构建元数据后按 `.` 切成数字段。
     *
     * 必须先剥后缀再切段：`BuildConfig.VERSION_NAME` 是 `1.6.1-debug`
     * （`app/build.gradle.kts` 里 `versionNameSuffix = "-debug"`），若直接切段，
     * 末段会变成 `"1-debug"` 而 `toIntOrNull()` 返回 null 退化成 0，
     * 于是 `1.6.1-debug` 被解析成 `1.6.0`，补丁位被静默抹掉。
     */
    private fun parseVersion(raw: String): List<Int> {
        val core = raw.trim().substringBefore('+').substringBefore('-')
        return core.split('.').map { it.toIntOrNull() ?: 0 }
    }

    /**
     * 从发布说明里取出该 APK 的 SHA-256。
     *
     * CI 在 release body 的「文件校验」小节按固定格式公布：
     * ```
     * e72c666997a83e2d2128d2826f390bbdde40cebcf21639cfcdd8c01efaaf3ab2  ./Polaris-1.7.4.apk
     * ```
     * 所以只在"同时包含该 APK 文件名和 64 位十六进制串"的行里提取，
     * 避免误取签名证书指纹。取不到返回 `null`，调用方跳过校验。
     */
    private fun extractSha256(
        changelog: String,
        apkFileName: String,
    ): String? {
        val line =
            changelog.lineSequence().firstOrNull { it.contains(apkFileName) && SHA256_PATTERN.containsMatchIn(it) }
                ?: run {
                    // 取不到就静默跳过校验，发布说明格式一旦变动校验会无声失效，
                    // 至少留一条日志便于排查"为什么这次没校验"。
                    AppLog.w("Polaris-Update", "发布说明里没有 ${sanitizeLog(apkFileName)} 的 SHA-256，本次跳过校验")
                    return null
                }
        return SHA256_PATTERN.find(line)?.groupValues?.get(1)?.lowercase()
    }

    private val SHA256_PATTERN = Regex("(?i)\\b([0-9a-f]{64})\\b")
}
