// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** 节点页折叠态专用偏好（非敏感：只存分组名字符串，普通 SharedPreferences 即可） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NodesSectionPrefs

/**
 * 节点页「哪些分组被收起 / 见过哪些分组」的持久化存储（按账号隔离）。
 *
 * **背景**（第 3 轮 N3 残留缺口）：`collapsedSections` 与 `seenSections` 原先都只是
 * `ServerViewModel` 的内存态，进程重启即归零。真机取证：95 图（冷启动，全部收起）→
 * 96 图（用户展开主组）→ **97 图（重启 App 再进节点页，又变回全部收起）**。
 * 即"切走再切回"已修好，但"重启后"这一维度没保住。
 *
 * **为什么要存两份**：
 * - `collapsed`：用户当前收起的分组键；
 * - `seen`：见过的分组键（只增不减）。
 *
 * 只存 `collapsed` 而不存 `seen` 是无效的：重启后 [com.slte.app.ui.screen.server.ServerViewModel]
 * 的 `syncCollapsedSections` 会把所有分组都当"首见"，一律补进收起集合，
 * 用户展开过的分组照样被重新收起。
 *
 * **账号隔离**：用邮箱的 SHA-256 前 16 位做 key 前缀。偏好文件是明文（这点数据不值得上
 * 加密存储），所以不把邮箱这类个人信息直接写进 key。
 */
@Singleton
class NodesSectionStore
@Inject
constructor(
    @NodesSectionPrefs private val prefs: SharedPreferences,
) {
    fun collapsedFor(account: String?): Set<String> = readSet(account, KEY_COLLAPSED)

    fun seenFor(account: String?): Set<String> = readSet(account, KEY_SEEN)

    fun save(
        account: String?,
        collapsed: Set<String>,
        seen: Set<String>,
    ) {
        prefs.edit {
            putStringSet(key(account, KEY_COLLAPSED), collapsed.toSet())
            putStringSet(key(account, KEY_SEEN), seen.toSet())
        }
    }

    /** 返回副本：SharedPreferences 明确要求调用方不得修改 `getStringSet` 返回的集合。 */
    private fun readSet(
        account: String?,
        suffix: String,
    ): Set<String> = prefs.getStringSet(key(account, suffix), null)?.toSet().orEmpty()

    private fun key(
        account: String?,
        suffix: String,
    ): String = "${accountKeyOf(account)}_$suffix"

    /** 账号标识：邮箱哈希（明文存储下不落 PII）；无账号时用固定串，保证未登录态也能工作。 */
    internal fun accountKeyOf(account: String?): String {
        val raw = account?.trim()?.lowercase().orEmpty()
        if (raw.isEmpty()) return ANONYMOUS
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        const val PREFS_NAME = "nodes_sections"

        private const val KEY_COLLAPSED = "collapsed"

        private const val KEY_SEEN = "seen"

        private const val ANONYMOUS = "anon"
    }
}
