// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.BuildConfig
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.update.AppUpdateChecker
import com.slte.app.data.update.AppUpdateDownloader
import com.slte.app.data.update.ReleaseInfo
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
 * 应用内更新曾整体移除，现按 GitHub Releases 方案加回（版本检测、更新弹窗、DownloadManager
 * 下载、FileProvider 跳转安装），本类重新承担更新职责：
 * - [kernelVersion]：内核版本行（关于页展示 + 日志导出上下文）
 * - [siteInfo]：面板下发的应用名/描述，用于应用标识卡
 * - [diagnosticsExtra]：导出诊断日志时附带的内核版本
 * - [updateState] / [checkForUpdate] / [startDownload] / [dismissUpdate]：应用内更新
 */
@HiltViewModel
class UpdateViewModel
@Inject
constructor(
    private val kernelProxy: KernelProxy,
    private val siteInfoStore: SiteInfoStore,
    private val diagnostics: Diagnostics,
    private val updateChecker: AppUpdateChecker,
    private val downloader: AppUpdateDownloader,
) : ViewModel() {
    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    /** 导出诊断时附带的上下文；内核版本由本页负责查询，所以在基础字段上补一条。 */
    fun diagnosticsExtra(): Map<String, String> = diagnostics.extra() + mapOf("内核版本" to (_kernelVersion.value ?: "-"))

    /** 检查新版本：有新版 -> Available，无 -> UpToDate，失败 -> Failed。 */
    fun checkForUpdate() {
        if (_updateState.value == AppUpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking
            _updateState.value =
                try {
                    val info = updateChecker.checkLatest(BuildConfig.VERSION_NAME)
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
     * 经 DownloadManager 下载 APK，完成后自动跳安装界面。
     *
     * 下载逻辑在 [AppUpdateDownloader]（应用单例）里，不跟关于页的 ViewModel 走，
     * 离开关于页也不会丢下载完成的广播。
     */
    fun startDownload(info: ReleaseInfo) {
        downloader.startDownload(info)
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
    }
}
