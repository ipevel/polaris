// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.slte.app.R
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK 下载器（应用单例）。
 *
 * 为什么必须是单例：之前下载逻辑写在 `UpdateViewModel` 里，而它是
 * `hiltViewModel(key = "update")`（跟着关于页走）。用户点"立即更新"后弹窗关闭，
 * 一旦离开关于页 ViewModel 就被清掉，`onCleared` 把下载完成广播接收器注销——
 * DownloadManager 在系统里默默下完，但没人弹安装界面。放到单例里，接收器与
 * App 同寿命，离开关于页也不影响。
 */
@Singleton
class AppUpdateDownloader
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
) {
    private var downloadReceiver: BroadcastReceiver? = null

    /** 已下载待安装的 APK（用户未授予"安装未知应用"权限时暂存，授权后重试）。 */
    @Volatile
    private var pendingApk: File? = null

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
        AppLog.i("Polaris-Update", "下载已入队: ${sanitizeLog(info.apkFileName)} id=$downloadId")

        downloadReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    runCatching { appContext.unregisterReceiver(this) }
                    downloadReceiver = null
                    if (querySuccess(dm, downloadId)) {
                        AppLog.i("Polaris-Update", "下载完成，跳转安装")
                        installApk(destFile)
                    } else {
                        AppLog.w("Polaris-Update", "下载未成功，id=$downloadId")
                    }
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

    /**
     * 有待安装包且已获"安装未知应用"权限时触发安装，返回是否触发。
     * 供用户从系统设置回来后重试。
     */
    fun tryInstallPending(): Boolean {
        val file = pendingApk ?: return false
        if (!appContext.packageManager.canRequestPackageInstalls()) return false
        pendingApk = null
        fireInstallIntent(file)
        return true
    }

    private fun installApk(file: File) {
        // Android 8+：光有 REQUEST_INSTALL_PACKAGES 还不够，用户必须在系统设置里
        // 给本应用打开"允许安装未知应用"，否则 startActivity 直接失败。
        // 之前这里没检查，是"下载完不弹安装"的另一个原因。
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            AppLog.w("Polaris-Update", "未授予安装未知应用权限，暂存安装包并引导去设置")
            pendingApk = file
            val settingsIntent =
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            runCatching { appContext.startActivity(settingsIntent) }
                .onFailure { AppLog.w("Polaris-Update", "打开安装权限设置失败: ${sanitizeLog(it.message ?: "Unknown")}") }
            return
        }
        fireInstallIntent(file)
    }

    private fun fireInstallIntent(file: File) {
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
        runCatching { appContext.startActivity(intent) }
            .onFailure { AppLog.w("Polaris-Update", "跳转安装失败: ${sanitizeLog(it.message ?: "Unknown")}") }
    }
}
