// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import com.slte.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用级「有新版本」状态（应用单例）。
 *
 * 为什么要独立于关于页的 ViewModel：提示圆点出现在三个互不相邻的位置
 * （底部「我的」Tab、我的页「关于软件」行、关于页「检查更新」行），分属不同页面与
 * ViewModel，必须有一个活到进程结束的单一事实源，否则三处会各查一次、各说各话。
 *
 * 只负责「有没有新版本」这一个问题。检查中/检查失败属于关于页自己的页面态，
 * 由 `UpdateViewModel` 维护，不进这里——圆点不该因为一次网络抖动就灭掉。
 */
@Singleton
class UpdateAvailability
@Inject
constructor(
    private val checker: AppUpdateChecker,
) {
    private val _available = MutableStateFlow<ReleaseInfo?>(null)

    /** 非空表示存在比当前安装版本更新的版本；无更新、或尚未检查成功都是 null。 */
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var launched = false

    /**
     * 启动时自动检查，每个进程只真正发一次请求。
     *
     * 失败静默：拉不到 GitHub 不该影响任何界面，圆点保持上一次的结果（冷启动时即不显示）。
     * 也**不重试**——启动路径上的重试会变成用户看不见的流量消耗。
     */
    suspend fun refreshOnLaunch() {
        if (launched) return
        launched = true
        runCatching { refresh() }
    }

    /**
     * 主动检查（关于页的「检查更新」），不受「每进程一次」限制。
     *
     * @return 新版本信息；`null` 表示已是最新。
     * @throws Exception 网络或解析失败，由调用方决定怎么提示。
     */
    suspend fun refresh(): ReleaseInfo? = mutex.withLock {
        // 失败时异常在赋值之前抛出，_available 保持原值：手动检查失败不该把已有的圆点抹掉。
        val info = checker.checkLatest(BuildConfig.VERSION_NAME)
        _available.value = info
        info
    }
}
