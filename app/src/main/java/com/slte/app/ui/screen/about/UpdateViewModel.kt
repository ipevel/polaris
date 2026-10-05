// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.update.AppUpdateChecker
import com.slte.app.data.update.ReleaseInfo
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelProxy
import com.slte.app.utils.Diagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    @ApplicationContext private val appContext: Context,
    private val kernelProxy: KernelProxy,
    private val siteInfoStore: SiteInfoStore,
    private val diagnostics: Diagnostics,
    private val updateChecker: AppUpdateChecker,
) : ViewModel() {
    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private var downloadReceiver: BroadcastReceiver? = null

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
     * 目标为 FileProvider 已覆盖的 app 专属下载目录，无需存储权限。
     */
    fun startDownload(info: ReleaseInfo) {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val destFile = File(destDir, info.apkFileName)
        if (destFile.exists()) destFile.delete()

        val request =
            DownloadManager
                .Request(Uri.parse(info.apkUrl))
                .setTitle(appContext.getString(R.string.update_download_title, info.version))
                .setDescription(info.apkFileName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destFile))
        val downloadId = dm.enqueue(request)

        downloadReceiver?.let { appContext.unregisterReceiver(it) }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    appContext.unregisterReceiver(this)
                    downloadReceiver = null
                    if (querySuccess(dm, downloadId)) installApk(destFile)
                }
            }
        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun querySuccess(
        dm: DownloadManager,
        downloadId: Long,
    ): Boolean {
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return status == DownloadManager.STATUS_SUCCESSFUL
            }
        }
        return false
    }

    private fun installApk(file: File) {
        val uri =
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        appContext.startActivity(intent)
    }

    override fun onCleared() {
        downloadReceiver?.let {
            runCatching { appContext.unregisterReceiver(it) }
            downloadReceiver = null
        }
        super.onCleared()
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
