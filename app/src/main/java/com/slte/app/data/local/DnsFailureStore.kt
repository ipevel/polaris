// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** DNS 失败记忆专用偏好（非敏感且需跨进程复用，普通 SharedPreferences 即可） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DnsFailurePrefs

/**
 * "某个面板主机的某一族地址连不上"这一判断的**跨进程**持久化。
 *
 * **为什么需要**（2026-09-26 真机实测）：面板域名解析出 2 个 Cloudflare IPv4 + IPv6，
 * 网络把 IPv4 全部黑洞、只有 IPv6 通。进程内的坏地址记忆只能让**本次运行**之后的请求变快；
 * 冷启动记忆为空，要重新学一遍（2 个坏地址 × 5 秒连接超时 ≈ 11 秒），
 * 用户感受就是"每次打开 App 第一个操作都要等十几秒"。
 *
 * **三条安全约束**（否则"记住坏地址"会变成新的坑）：
 * 1. 只影响**排序**，永不丢弃地址——万一记错，候选地址仍会被尝试；
 * 2. 有[TTL_MS]到期时间，网络恢复后不会永久误判；
 * 3. 一旦某族**连接成功**就立刻清除该族的记忆（[markSucceeded]），换网络后一次请求即可自愈。
 *
 * key 里的主机名做 SHA-256（明文偏好里不落用户的面板域名）。
 */
@Singleton
class DnsFailureStore
@Inject
constructor(
    @DnsFailurePrefs private val prefs: SharedPreferences,
) {
    /** 返回未过期的"主机|族" → 失效时间戳；顺手清掉过期项，避免偏好越积越多。 */
    fun loadFamilies(now: Long = System.currentTimeMillis()): Map<String, Long> {
        val raw = prefs.getStringSet(KEY_FAMILIES, null)?.toSet().orEmpty()
        val alive = mutableMapOf<String, Long>()
        val expired = mutableListOf<String>()
        raw.forEach { entry ->
            val split = entry.lastIndexOf('=')
            val key = if (split > 0) entry.substring(0, split) else entry
            val expiry = if (split > 0) entry.substring(split + 1).toLongOrNull() ?: 0L else 0L
            if (expiry > now) alive[key] = expiry else expired += entry
        }
        if (expired.isNotEmpty()) write(raw - expired.toSet())
        return alive
    }

    /** 记录"该主机这一族连不上"，[ttlMs] 后自动失效。 */
    fun markFailed(
        host: String,
        familySuffix: String,
        ttlMs: Long = TTL_MS,
        now: Long = System.currentTimeMillis(),
    ) {
        val key = familyKey(host, familySuffix)
        val kept =
            prefs.getStringSet(KEY_FAMILIES, null).orEmpty()
                .filterNot { it.substringBeforeLast('=') == key }
                .filter { it.substringAfterLast('=').toLongOrNull()?.let { t -> t > now } == true }
        write(kept.toSet() + "$key=${now + ttlMs}")
    }

    /** 连接成功 ⇒ 该主机这一族的记忆立刻作废（网络恢复/换网后自愈）。 */
    fun markSucceeded(host: String, familySuffix: String) {
        val key = familyKey(host, familySuffix)
        val raw = prefs.getStringSet(KEY_FAMILIES, null)?.toSet().orEmpty()
        val kept = raw.filterNot { it.substringBeforeLast('=') == key }
        if (kept.size != raw.size) write(kept.toSet())
    }

    private fun write(value: Set<String>) {
        prefs.edit { putStringSet(KEY_FAMILIES, value) }
    }

    /** 与内存态一致的 key 形式：`<hostHash>|v4` / `<hostHash>|v6`。 */
    internal fun familyKey(
        host: String,
        familySuffix: String,
    ): String = "${hostHash(host)}|$familySuffix"

    /** 明文偏好里不落用户面板域名：不可逆哈希即可（不需要抗碰撞强度之外的属性）。 */
    internal fun hostHash(host: String): String {
        val raw = host.trim().lowercase()
        if (raw.isEmpty()) return "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        const val PREFS_NAME = "dns_failure"

        private const val KEY_FAMILIES = "families"

        /** 记忆有效期：够覆盖"下次打开 App"，又不至于在换网后长期误判。 */
        const val TTL_MS = 10 * 60_000L
    }
}
