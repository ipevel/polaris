// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.update.AppUpdateDownloader
import com.slte.app.data.update.ReleaseInfo
import com.slte.app.data.update.UpdateAvailability
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelProxy
import com.slte.app.utils.Diagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 应用内更新的 UI 状态。 */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data object UpToDate : AppUpdateState

    data class Available(val info: ReleaseInfo) : AppUpdateState

    data object Failed : AppUpdateState
}

/**
 * 关于页的 ViewModel。
 *
 * 类名沿用 `UpdateViewModel` 是历史原因：它原本同时承担「应用内更新」与关于页自身的展示职责。
 * 应用内更新曾整体移除，现按 GitHub Releases 方案加回（版本检测、更新弹窗、应用内流式下载、
 * FileProvider 跳转安装），本类重新承担更新职责：
 * - [kernelVersion]：内核版本行（关于页展示 + 日志导出上下文）
 * - [siteInfo]：面板下发的应用名/描述，用于应用标识卡
 * - [diagnosticsExtra]：导出诊断日志时附带的内核版本
 * - [updateState] / [checkForUpdate] / [startDownload] / [cancelDownload] / [dismissUpdate]：应用内更新
 */
@HiltViewModel
class UpdateViewModel
@Inject
constructor(
    private val kernelProxy: KernelProxy,
    private val siteInfoStore: SiteInfoStore,
    private val diagnostics: Diagnostics,
    private val updateAvailability: UpdateAvailability,
    private val downloader: AppUpdateDownloader,
) : ViewModel() {
    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val _downloadResult = MutableStateFlow<AppUpdateDownloader.DownloadResult?>(null)
    val downloadResult: StateFlow<AppUpdateDownloader.DownloadResult?> = _downloadResult.asStateFlow()

    /**
     * 下载进度；没有下载在进行时为 `null`。
     *
     * 直接转发单例的 StateFlow，本类不做缓存：进度是高频状态，多一层缓存就多一次
     * "退出关于页再进来进度对不上"的机会，而下载本身活在单例里，不随页面走。
     */
    val progress: StateFlow<AppUpdateDownloader.Progress?> = downloader.progress

    /** 一次性消费下载终态，避免重组时重复弹提示。 */
    fun consumeDownloadResult() {
        _downloadResult.value = null
    }

    /** 导出诊断时附带的上下文；内核版本由本页负责查询，所以在基础字段上补一条。 */
    fun diagnosticsExtra(): Map<String, String> = diagnostics.extra() + mapOf("内核版本" to (_kernelVersion.value ?: "-"))

    /**
     * 检查新版本：有新版 -> Available，无 -> UpToDate，失败 -> Failed。
     *
     * 走 [UpdateAvailability] 而不是直接问 [AppUpdateChecker]：三处提示圆点读的是那份
     * 进程级状态，手动检查的结果必须写回去，否则会出现"查到新版本但圆点不亮"或
     * "已经是最新了圆点还亮着"。失败时它保持原值不动，圆点不会因为一次网络抖动灭掉。
     */
    fun checkForUpdate() {
        if (_updateState.value == AppUpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking
            _updateState.value =
                try {
                    val info = updateAvailability.refresh()
                    if (info != null) AppUpdateState.Available(info) else AppUpdateState.UpToDate
                } catch (e: Exception) {
                    AppUpdateState.Failed
                }
        }
    }

    fun dismissUpdate() {
        _updateState.value = AppUpdateState.Idle
    }

    /**
     * 应用内流式下载 APK，完成后自动跳安装界面。
     *
     * 下载逻辑在 [AppUpdateDownloader]（应用单例）里，不跟关于页的 ViewModel 走，
     * 离开关于页、把 App 切后台都不会中断下载，进度也照常回传。
     */
    fun startDownload(info: ReleaseInfo) {
        downloader.startDownload(info)
    }

    /** 取消下载：中断请求并删除半成品文件。没有下载在进行时是空操作。 */
    fun cancelDownload() {
        downloader.cancelDownload()
    }

    /**
     * 用户从"安装未知应用"设置页回来后重试安装。
     * 由关于页的 onResume 触发。
     */
    fun retryPendingInstall() {
        downloader.tryInstallPending()
    }

    init {
        viewModelScope.launch {
            repeat(10) {
                _kernelVersion.value = kernelProxy.coreVersion()
                if (_kernelVersion.value != null) return@launch
                delay(1000)
            }
        }

        // 站点名/描述由订阅生命周期维护（订阅头 + comm/config 写入 SiteInfoStore），
        // 关于页只观察回放
        viewModelScope.launch {
            siteInfoStore.siteInfo.collect { _siteInfo.value = it }
        }

        // 下载在单例里跑，结果回传到这里才能让用户看见失败原因。
        // 收集随 viewModelScope 取消而结束，单例不持有本 ViewModel；重新进入关于页时
        // SharedFlow 会重放最近一次终态，离开期间发生的失败/缺权限不会丢。
        viewModelScope.launch {
            downloader.results.collect { result -> _downloadResult.value = result }
        }
    }
}
