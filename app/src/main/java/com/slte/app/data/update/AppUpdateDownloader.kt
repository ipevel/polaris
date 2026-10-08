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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * APK 下载器（应用单例）。
 *
 * 为什么是单例：下载逻辑不能绑在任何页面/ViewModel 的生命周期上。用户点"立即更新"
 * 后弹窗关闭、离开关于页甚至杀掉 App 再回来，DownloadManager 都可能还在系统里下载，
 * 接收器必须活得比那更久，所以放进 `@Singleton`。
 *
 * 注意：单例只是**必要**条件，不是充分条件。真正让 Android 13+ 上"下载完成却不弹安装"
 * 的原因是广播注册方式，见 [startDownload] 里 `RECEIVER_EXPORTED` 的注释。
 */
@Singleton
class AppUpdateDownloader
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
) {
    /**
     * 按 `DownloadManager` 的下载 id 索引接收器。
     *
     * 曾经这里是单个字段，于是第二次点更新会把第一次的接收器注销掉，
     * 第一次的完成回调永久丢失。改成按 id 存，互不干扰。
     */
    private val receivers = ConcurrentHashMap<Long, BroadcastReceiver>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 下载终态事件流（`replay = 1`）。
     *
     * 曾经这里是单个 `callback` 字段，由关于页 ViewModel 注册、随其销毁而置 null——
     * 下载进行中离开关于页，失败/缺权限的提示就永远收不到。改成 SharedFlow 后：
     * 单例不再持有 ViewModel（无泄漏），且重新进入关于页时能拿到最近一次终态。
     */
    private val _results = MutableSharedFlow<DownloadResult>(replay = 1, extraBufferCapacity = 8)

    /** 下载终态事件流；关于页 ViewModel 收集它来弹提示。 */
    val results: SharedFlow<DownloadResult> = _results

    /** 已下载待安装的 APK（用户未授予"安装未知应用"权限时暂存，授权后重试）。 */
    @Volatile
    private var pendingApk: File? = null

    /** 当前进行中的下载 id；用于拦截重复入队（连续点两次"立即更新"）。 */
    @Volatile
    private var activeDownloadId: Long? = null

    /**
     * 经 DownloadManager 下载 APK，完成后自动跳安装界面。
     * 目标为 FileProvider 已覆盖的 app 专属下载目录，无需存储权限。
     */
    fun startDownload(info: ReleaseInfo) {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // 已有下载在跑：只提示、不重下。此前这里一律"取消旧的再入队新的"，于是每点一次
        // "立即更新"就从头重下一遍（系统通知里的进度也从 0 重来），用户看到的就是
        // "点一次下载一次"。正在下载时保持原下载不动，交给调用方提示"下载中"。
        activeDownloadId?.let { oldId ->
            if (queryStatus(dm, oldId) == DownloadStatus.RUNNING) {
                AppLog.i("Polaris-Update", "已有下载在进行中，忽略重复请求: id=$oldId")
                report(DownloadResult.AlreadyDownloading)
                return
            }
            // 旧下载已到终态（完成广播可能已丢），清掉它的接收器与记录再开新的。
            receivers.remove(oldId)?.let { unregisterQuietly(it) }
            activeDownloadId = null
            runCatching { dm.remove(oldId) }
                .onFailure { AppLog.w("Polaris-Update", "清理已结束的下载失败: id=$oldId") }
        }
        // 新一轮下载让上一轮暂存的待安装包作废：它可能马上被同名文件覆盖或删除，
        // 留着会让 onResume 的 tryInstallPending() 拿一个不存在的包去发起安装。
        pendingApk = null
        val destDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (destDir == null) {
            report(DownloadResult.Failed(appContext.getString(R.string.update_download_dir_unavailable)))
            return
        }
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
        activeDownloadId = downloadId
        AppLog.i("Polaris-Update", "下载已入队: ${sanitizeLog(info.apkFileName)} id=$downloadId")
        report(DownloadResult.Started)

        receivers.remove(downloadId)?.let { unregisterQuietly(it) }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    // 这里**不能**注销接收器：本广播可能只是"看起来像"本次下载的终态
                    // （ACTION_DOWNLOAD_COMPLETE 是导出广播，任何应用都能伪造，id 也可猜），
                    // 而注销后真正的完成广播就再也没人接了。注销时机统一交给 finish()：
                    // 只有查到终态才结账，运行中的 id 原样保留接收器。
                    scope.launch { finish(dm, id, destFile, info.expectedSha256) }
                }
            }
        receivers[downloadId] = receiver
        // 必须是 RECEIVER_EXPORTED：ACTION_DOWNLOAD_COMPLETE 由 DownloadProvider
        // 所在的**独立进程**发出，不是 system_server。用 RECEIVER_NOT_EXPORTED 时
        // 系统根本不投递给本进程，实测表现为"下载 100% 完成但安装界面永不出现"。
        // EXPORTED 带来的被伪造风险由 onReceive 里的四重校验挡住：
        // id 必须匹配、必须能在 DownloadManager 数据库里查到该 id、状态必须是终态
        // （运行中/暂停中的 id 一律忽略，伪造广播打不断真实下载）、
        // 落地文件必须通过可选的 SHA-256 比对。
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /**
     * 下载完成后的落地处理：状态确认 → 哈希校验 → 拉起安装。跑在 IO 线程。
     *
     * 广播本身不可信（导出广播 + 可猜的 id），所以这里**先查库再结账**：
     * 只有 [DownloadStatus.SUCCESS]/[DownloadStatus.FAILED] 才算这个 id 的终态，
     * 运行中/暂停中一律当噪声丢弃，并且**不注销接收器**，继续等真正的完成广播。
     */
    private suspend fun finish(
        dm: DownloadManager,
        downloadId: Long,
        destFile: File,
        expectedSha256: String?,
    ) {
        when (queryStatus(dm, downloadId)) {
            DownloadStatus.RUNNING -> {
                AppLog.d("Polaris-Update", "下载未到终态，忽略该广播: id=$downloadId")
                return
            }
            DownloadStatus.SUCCESS -> Unit
            DownloadStatus.FAILED -> {
                AppLog.w("Polaris-Update", "下载失败: id=$downloadId")
                settle(downloadId, DownloadResult.Failed(appContext.getString(R.string.update_download_incomplete)))
                return
            }
            DownloadStatus.UNKNOWN -> {
                AppLog.w("Polaris-Update", "查不到下载记录: id=$downloadId")
                settle(downloadId, DownloadResult.Failed(appContext.getString(R.string.update_download_incomplete)))
                return
            }
        }
        if (!destFile.exists()) {
            settle(downloadId, DownloadResult.Failed(appContext.getString(R.string.update_download_file_missing)))
            return
        }
        val actual = sha256Of(destFile)
        if (expectedSha256 != null && actual != expectedSha256) {
            AppLog.w("Polaris-Update", "SHA-256 校验不通过: 期望 ${sanitizeLog(expectedSha256)}")
            destFile.delete()
            settle(downloadId, DownloadResult.Failed(appContext.getString(R.string.update_download_checksum_failed)))
            return
        }
        AppLog.i("Polaris-Update", "下载完成，跳转安装")
        val needsPermission = installApk(destFile)
        settle(downloadId, if (needsPermission) DownloadResult.NeedsPermission(destFile) else DownloadResult.Installed(destFile))
    }

    /**
     * 终态结账：注销该 id 的接收器并回调，保证每个下载恰好结账一次。
     *
     * `remove` 返回 `null` 说明这个 id 已经结过账（重复/并发广播），直接丢弃，
     * 避免重复弹提示、重复跳安装；接收器也由此不会泄漏。
     */
    private fun settle(
        downloadId: Long,
        result: DownloadResult,
    ) {
        val receiver = receivers.remove(downloadId) ?: return
        if (activeDownloadId == downloadId) activeDownloadId = null
        unregisterQuietly(receiver)
        report(result)
    }

    /** 下载状态：只有 [SUCCESS]/[FAILED] 是终态，[RUNNING] 含运行中与暂停中。 */
    private enum class DownloadStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        UNKNOWN,
    }

    /** 查 DownloadManager 数据库里该 id 的状态；查不到或查询抛异常返回 [DownloadStatus.UNKNOWN]。 */
    private fun queryStatus(
        dm: DownloadManager,
        downloadId: Long,
    ): DownloadStatus = runCatching {
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use DownloadStatus.UNKNOWN
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESS
                DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                else -> DownloadStatus.RUNNING
            }
        }
    }.getOrElse {
        AppLog.w("Polaris-Update", "查询下载状态失败: ${sanitizeLog(it.message ?: "Unknown")}")
        DownloadStatus.UNKNOWN
    }

    /** 返回 APK 的小写十六进制 SHA-256；文件读失败返回 `null`。 */
    private fun sha256Of(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.onFailure {
        AppLog.w("Polaris-Update", "计算 SHA-256 失败: ${sanitizeLog(it.message ?: "Unknown")}")
    }.getOrNull()

    /**
     * 有待安装包且已获"安装未知应用"权限时触发安装，返回是否触发。
     * 供用户从系统设置回来后重试。
     */
    fun tryInstallPending(): Boolean {
        val file = pendingApk ?: return false
        // 暂存期间文件可能已被新一轮下载覆盖、或因校验失败被删除；对不存在的包发起安装
        // 只会静默失败，还会让 pendingApk 一直挂着。
        if (!file.exists()) {
            pendingApk = null
            return false
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) return false
        pendingApk = null
        fireInstallIntent(file)
        return true
    }

    /** 拉起安装界面。返回 `true` 表示因缺权限而未安装、已跳系统设置、需稍后重试。 */
    private fun installApk(file: File): Boolean {
        // Android 8+：光有 REQUEST_INSTALL_PACKAGES 还不够，用户必须在系统设置里
        // 给本应用打开"允许安装未知应用"，否则 startActivity 直接失败。
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
            return true
        }
        fireInstallIntent(file)
        return false
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

    private fun unregisterQuietly(receiver: BroadcastReceiver) {
        runCatching { appContext.unregisterReceiver(receiver) }
            .onFailure { AppLog.w("Polaris-Update", "注销接收器失败: ${sanitizeLog(it.message ?: "Unknown")}") }
    }

    private fun report(result: DownloadResult) {
        _results.tryEmit(result)
    }

    /** 下载结果：下载终态，外加"已开始下载"和"已有下载在跑、本次请求被忽略"。 */
    sealed interface DownloadResult {
        /** 已成功入队，下载开始。让提示只由这里发出，调用方不必自己猜"是否真的开始了"。 */
        data object Started : DownloadResult

        /** 已有一个下载在进行中，本次请求被忽略，调用方应提示"正在下载中"。 */
        data object AlreadyDownloading : DownloadResult

        /** 下载成功、校验通过，已拉起系统安装界面。 */
        data class Installed(
            val file: File,
        ) : DownloadResult

        /** 下载成功但缺少"安装未知应用"授权，已跳系统设置，授权回来后由 `tryInstallPending()` 补装。 */
        data class NeedsPermission(
            val file: File,
        ) : DownloadResult

        /** 下载或校验失败，[reason] 是已本地化、可直接展示给用户的短原因。 */
        data class Failed(
            val reason: String,
        ) : DownloadResult
    }
}
