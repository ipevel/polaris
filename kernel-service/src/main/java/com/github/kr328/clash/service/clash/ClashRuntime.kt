// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
//
// 模块故障隔离与关键模块停机：移植自上游 shgnx/slte c73c4af6。

package com.github.kr328.clash.service.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.OverrideSlot
import com.github.kr328.clash.service.clash.module.Module
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val globalLock = Mutex()

interface ClashRuntimeScope {
    fun <E, T : Module<E>> install(
        module: T,
        critical: Boolean = false,
    ): T
}

interface ClashRuntime {
    fun launch()
    fun requestGc()
}

/**
 * [critical] 模块执行失败时回调 [onCriticalFailure] 并停止整个运行时：
 * 关键模块（TUN / 配置）挂了不该静默吞掉继续跑，否则表现为「界面连接着但内核残缺」。
 */
fun CoroutineScope.clashRuntime(
    onCriticalFailure: (String) -> Unit = {},
    block: suspend ClashRuntimeScope.() -> Unit,
): ClashRuntime {
    return object : ClashRuntime {
        override fun launch() {
            // 模块故障隔离：每个模块独立协程，非关键模块失败只记日志，不拖垮运行时。
            // SupervisorJob：单个模块失败不再连坐整个 runtime；关键模块失败由 install 显式停机
            launch(Dispatchers.IO + SupervisorJob()) {
                val runtimeScope = this
                globalLock.withLock {
                    Log.d("ClashRuntime: initialize")

                    try {
                        Clash.reset()
                        Clash.clearOverride(OverrideSlot.Session)

                        val scope = object : ClashRuntimeScope {
                            override fun <E, T : Module<E>> install(
                                module: T,
                                critical: Boolean,
                            ): T {
                                launch {
                                    try {
                                        module.execute()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        val message = "${module.javaClass.simpleName}: ${e.message ?: e.javaClass.simpleName}"
                                        Log.e(message, e)
                                        if (critical) {
                                            onCriticalFailure(message)
                                            runtimeScope.cancel()
                                        }
                                    }
                                }

                                return module
                            }
                        }

                        scope.block()

                        cancel()
                    } finally {
                        withContext(NonCancellable) {
                            Clash.reset()
                            Clash.clearOverride(OverrideSlot.Session)

                            Log.d("ClashRuntime: destroyed")
                        }
                    }
                }
            }
        }

        override fun requestGc() {
            Clash.forceGc()
        }
    }
}
