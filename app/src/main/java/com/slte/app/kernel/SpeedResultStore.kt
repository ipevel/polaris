// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

/**
 * 测速结果与离线名单的持久化。
 *
 * 两者都按节点名（= 内核里的 proxy 名）存，因为节点页渲染的就是内核名。
 */
interface SpeedResultStore {
    fun saveSpeedResults(results: Map<String, Int>)

    fun getSpeedResults(): Map<String, Int>?

    fun clearSpeedResults()

    /**
     * 保存"探测确认不可达"的节点名。
     *
     * 与 [getSpeedResults] 的延迟值分开存：延迟是数字（可以缓存给 UI 先显示），
     * 离线是判定结论（必须来自一次真实的 urlTest 失败分类，不能由延迟反推）。
     */
    fun saveOfflineNodes(names: Set<String>)

    fun getOfflineNodes(): Set<String>?
}
