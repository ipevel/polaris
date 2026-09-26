// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.*

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
    data class LoadException(val message: String)

    private val store = ServiceStore(service)
    private val reload = Channel<Unit>(Channel.CONFLATED)

    /**
     * 解析 PROFILE_CHANGED 广播携带的 profile UUID。
     *
     * **这里绝对不能抛异常**：本方法在 `select {}` 的 clause 内被调用，异常会穿过 `run()`
     * 的 `try` → `enqueueEvent(LoadException)` → **整个 TunService 销毁重建**，用户侧
     * 表现为"VPN 无声断开"（历史缺陷：分流规则开关发的广播不带 EXTRA_UUID，
     * `UUID.fromString(null)` 抛 NPE，改一次分流规则就掉一次线，节点页分组同时清空）。
     *
     * EXTRA_UUID 缺失/空白/非法一律返回 null，语义为"重载当前激活配置"——与
     * ACTION_OVERRIDE_CHANGED 的处理一致，即忽略广播里那个"要切到哪个配置"的意图，
     * 只当作一次普通的配置重载请求。
     */
    private fun parseProfileChangeUuid(intent: Intent): UUID? {
        val raw = intent.getStringExtra(Intents.EXTRA_UUID) ?: return null
        if (raw.isBlank()) return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_PROFILE_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
        }
        Log.d("ConfigurationModule: listening")

        var loaded: UUID? = null

        reload.trySend(Unit)

        while (true) {
            val changed: UUID? = select {
                broadcasts.onReceive {
                    if (it.action == Intents.ACTION_PROFILE_CHANGED) parseProfileChangeUuid(it) else null
                }
                reload.onReceive {
                        null
                }
            }
            Log.d("ConfigurationModule: event received, changed=$changed")

            try {
                val current = store.activeProfile
                if (current == null) {
                    // 尚未选择配置（首次启动、登出后）：保持等待，别让服务因"还没就绪"直接退出
                    Log.w("ConfigurationModule: no active profile, skip reload")
                    continue
                }

                if (current == loaded && changed != null && changed != loaded)
                    continue

                loaded = current

                val active = ImportedDao().queryByUUID(current)
                if (active == null) {
                    // 激活的配置记录已不存在（账号登出/切换 API 地址时的清理，或删除与重载竞态）。
                    // 这里**不能**抛异常：抛出会走 LoadException → TunService 退出 → 用户侧表现为
                    // VPN 无声断开。跳过本次重载，等 App 选定新配置后再广播即可。
                    Log.w("ConfigurationModule: active profile $current not found, skip reload")
                    // 允许同名 uuid 之后被重新创建时再次触发加载
                    loaded = null
                    continue
                }

                Clash.setAgeSecretKey(active.ageSecretKey?.takeIf { it.isNotBlank() })

                Clash.load(service.importedDir.resolve(active.uuid.toString())).await()

                val remove = SelectionDao().querySelections(active.uuid)
                    .filterNot { Clash.patchSelector(it.proxy, it.selected) }
                    .map { it.proxy }

                SelectionDao().removeSelections(active.uuid, remove)

                StatusProvider.currentProfile = active.name

                service.sendProfileLoaded(current)

                Log.d("ConfigurationModule: reload done")
                Log.d("Profile ${active.name} loaded")
            } catch (e: Exception) {
                return enqueueEvent(LoadException(e.message ?: "Unknown"))
            }
        }
    }
}
