// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.slte.app.R
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * APK 下载器（应用单例）。
 *
 * 为什么是单例：下载不能绑在任何页面/ViewModel 的生命周期上。用户点"立即更新"后
 * 离开关于页、甚至把 App 切到后台，下载都得继续，进度与终态也必须还收得到，
 * 所以放进 `@Singleton`。
 *
 * 为什么自己下载而不是 `DownloadManager`：`DownloadManager` 是系统进程里的后台任务，
 * 它只能往通知栏写进度，应用内拿不到实时字节数，也没法干净地取消（只能 `remove(id)`，
 * 且删文件与广播时序都要额外兜）。用户要的是"就在应用内显示进度条下载 + 取消按钮"，
 * 所以这里用 OkHttp 流式下载：边写盘边把进度推给 UI，取消就是中断请求 + 删半成品。
 *
 * 目标目录仍是 app 专属外部下载目录（FileProvider 已覆盖），不需要任何存储权限。
 */
@Singleton
class AppUpdateDownloader
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
) {
    /**
     * 下载进度快照。`null` 表示当前没有进行中的下载。
     *
     * [totalBytes] 为 0 表示服务端没给 `Content-Length`，界面应显示不确定进度条。
     */
    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
    ) {
        /** 0f..1f 的完成比例；无法确定总长时为 `null`。 */
        val fraction: Float?
            get() =
                if (totalBytes > 0) {
                    (bytesRead.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
                } else {
                    null
                }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 只服务于 APK 下载的客户端。
     *
     * 读超时按"单次 read"计时而非总时长：慢速网络下每秒几十 KB 也应当能下完 36 MB，
     * 但服务端彻底不动时要尽快失败，让用户看到原因去重试。连接超时给短一点同理。
     */
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val _progress = MutableStateFlow<Progress?>(null)

    /** 下载进度；没有下载在进行时为 `null`。 */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

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

    /** 进行中的下载任务；`null` 表示空闲。只在上锁时写，读用 `@Volatile` 保证可见性。 */
    @Volatile
    private var running: Job? = null

    /**
     * 进行中的网络调用。
     *
     * 取消必须靠它：`Job.cancel()` 只能让协程在下一个挂起点退出，而 OkHttp 的
     * 阻塞式 `read()` 不是挂起点——用户点"取消"后要等当前网络分片自己返回才生效，
     * 卡在慢速连接上就是几十秒。`Call.cancel()` 能立刻解开它。
     */
    @Volatile
    private var activeCall: Call? = null

    /**
     * 用户主动取消的旗标：**必须在 [activeCall] 之前立起来**（见 [cancelDownload]）。
     *
     * `Call.cancel()` 会让正在阻塞的 `read()` 抛 IOException，而 `Job.cancel()` 要晚一步
     * 才生效。只看 `isActive` 的话，这两步之间有一个窗口会让"用户取消"被误判成
     * "网络故障"并弹错。旗标先立，判定就不再依赖那两次调用的时序。
     */
    @Volatile
    private var cancelled = false

    /** 保证"检查是否已有下载 → 占位 → 启动"是原子的，连点两次不会起两个下载。 */
    private val mutex = Mutex()

    /** 已下载待安装的 APK（用户未授予"安装未知应用"权限时暂存，授权后重试）。 */
    @Volatile
    private var pendingApk: File? = null

    /**
     * 启动下载。已有下载在跑时只回报 [DownloadResult.AlreadyDownloading]，不重下。
     *
     * 与旧实现（`DownloadManager`）一致地保持"正在下载就不重来"：此前这里是
     * "取消旧的再入队新的"，于是每点一次"立即更新"就从头重下一遍，用户看到的就是
     * "点一次下载一次"。
     */
    fun startDownload(info: ReleaseInfo) {
        scope.launch {
            mutex.withLock {
                if (running != null) {
                    AppLog.i("Polaris-Update", "已有下载在进行中，忽略重复请求")
                    report(DownloadResult.AlreadyDownloading)
                    return@withLock
                }
                val destDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (destDir == null) {
                    report(DownloadResult.Failed(appContext.getString(R.string.update_download_dir_unavailable)))
                    return@withLock
                }
                // 新一轮下载让上一轮暂存的待安装包作废：它可能马上被同名文件覆盖或删除，
                // 留着会让 onResume 的 tryInstallPending() 拿一个不存在的包去发起安装。
                pendingApk = null
                val destFile = File(destDir, info.apkFileName)
                // 新一轮开始前清掉上一轮的取消旗标。放在这里而不是 runDownload 开头：
                // runDownload 是异步起的，若在它开头清，用户在它真正跑起来之前点的取消会被抹掉。
                cancelled = false
                // 先把进度置成"已开始"，界面才能立刻切到进度态，不必等第一个网络分片回来；
                // 总长未知（0）时进度条走不确定态，拿到响应头后再补上真实总长。
                _progress.value = Progress(0L, 0L)
                running = scope.launch { runDownload(info, destFile) }
                AppLog.i("Polaris-Update", "开始下载: ${sanitizeLog(info.apkFileName)}")
                report(DownloadResult.Started)
            }
        }
    }

    /**
     * 取消当前下载：中断网络请求、删掉半成品文件、清空进度。
     *
     * 没有进行中的下载时是空操作，所以按钮连点、或下载刚好结束时的点击都安全。
     * 真正的清理在 [runDownload] 的 `finally` 里做——那里才知道文件该不该留。
     */
    fun cancelDownload() {
        val job = running ?: return
        // 三步顺序都不能反：
        // 1) 先立旗标——`Call.cancel()` 会立刻让 read() 抛 IOException，而 `job.cancel()`
        //    要晚一步才生效；旗标不先立，那个窗口里的 IOException 会被判成网络故障，
        //    用户就会看到一条莫名其妙的"网络错误"。
        // 2) 再断网络——反过来的话协程先进入取消态，卡在 read() 上仍要等超时。
        // 3) 最后取消协程——让分片循环里的 ensureActive() 退出。
        cancelled = true
        activeCall?.cancel()
        job.cancel()
    }

    /** 整个下载流程；负责把终态推给 [results]，并保证退出时清理干净。 */
    private suspend fun runDownload(
        info: ReleaseInfo,
        destFile: File,
    ) {
        val self = currentCoroutineContext()[Job]
        var keep = false
        try {
            val failure = transfer(info, destFile)
            if (failure != null) {
                report(DownloadResult.Failed(failure))
                return
            }
            // 校验与安装放进 NonCancellable：文件此刻已完整落盘，用户恰在这一瞬间点取消
            // 不该留下"下好了却没装"的残局——要么按校验结果清掉，要么交给系统安装界面。
            withContext(NonCancellable) {
                val expected = info.expectedSha256
                if (expected != null && !expected.equals(sha256Of(destFile), ignoreCase = true)) {
                    AppLog.w("Polaris-Update", "SHA-256 校验不通过: 期望 ${sanitizeLog(expected)}")
                    report(DownloadResult.Failed(appContext.getString(R.string.update_download_checksum_failed)))
                    return@withContext
                }
                keep = true
                AppLog.i("Polaris-Update", "下载完成，跳转安装")
                val needsPermission = installApk(destFile)
                report(
                    if (needsPermission) {
                        DownloadResult.NeedsPermission(destFile)
                    } else {
                        DownloadResult.Installed(destFile)
                    },
                )
            }
        } catch (e: IOException) {
            // 取消也会让 OkHttp 抛 IOException（`Call.cancel()` 的表现形式），
            // 那不是网络故障，不能报成"下载失败"让用户以为网络有问题。
            // 两个条件都要看：[cancelled] 覆盖"Call 已断、Job 还没标记取消"的那个窗口，
            // `isActive` 覆盖协程被别的途径取消（如 scope 结束）的情况。
            if (!cancelled && currentCoroutineContext().isActive) {
                AppLog.w("Polaris-Update", "下载失败: ${sanitizeLog(e.message ?: "Unknown")}")
                report(DownloadResult.Failed(appContext.getString(R.string.update_download_network_error)))
            }
        } finally {
            withContext(NonCancellable) {
                // keep 为假 = 失败、取消或校验不过，半成品必须删掉：
                // 留着会占 36 MB，还可能被下次下载当成"已存在"跳过。
                if (!keep) destFile.delete()
                mutex.withLock {
                    // 身份判断：万一取消后用户立刻重开了一轮，新任务不能被这里清掉。
                    if (running === self) {
                        running = null
                        activeCall = null
                        _progress.value = null
                    }
                }
            }
        }
    }

    /**
     * 流式下载到 [destFile]，边下边把进度写进 [progress]。
     *
     * @return 已本地化的失败原因；`null` 表示文件已完整落盘。
     */
    private suspend fun transfer(
        info: ReleaseInfo,
        destFile: File,
    ): String? {
        if (destFile.exists() && !destFile.delete()) {
            AppLog.w("Polaris-Update", "删除旧安装包失败: ${sanitizeLog(destFile.name)}")
        }
        val call = client.newCall(Request.Builder().url(info.apkUrl).build())
        activeCall = call
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w("Polaris-Update", "下载响应异常: HTTP ${response.code}")
                    return@use appContext.getString(R.string.update_download_incomplete)
                }
                val body = response.body
                if (body == null) {
                    return@use appContext.getString(R.string.update_download_incomplete)
                }
                // 服务端没给 Content-Length 时是 -1，此时进度条走不确定态。
                val declared = body.contentLength()
                val total = if (declared > 0) declared else 0L
                _progress.value = Progress(0L, total)
                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                        var written = 0L
                        while (true) {
                            // 每个分片前检查一次取消。这是"取消"能在 1 秒内生效的原因之一
                            // （另一半是 activeCall.cancel()，负责解开正在阻塞的那次 read）。
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            written += n
                            _progress.value = Progress(written, total)
                        }
                        output.flush()
                    }
                }
                null
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
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

    private fun report(result: DownloadResult) {
        _results.tryEmit(result)
    }

    /** 下载结果：下载终态，外加"已开始下载"和"已有下载在跑、本次请求被忽略"。 */
    sealed interface DownloadResult {
        /** 已成功开始下载。让提示只由这里发出，调用方不必自己猜"是否真的开始了"。 */
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

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L

        /** 64 KB：比默认的 8 KB 少一半 syscall，又小到足以让进度条每 100ms 上下动一次。 */
        const val TRANSFER_BUFFER_BYTES = 64 * 1024
    }
}
