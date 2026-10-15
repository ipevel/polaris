// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.update

import android.os.Environment
import com.slte.app.R
import com.slte.app.support.RobolectricTestApplication
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 应用内流式下载：进度上报、取消清理、校验拦截。
 *
 * 回归背景：旧实现走系统 `DownloadManager`，应用内拿不到字节数，也没有取消按钮。
 * 用户在 v1.7.0 反馈过"重复点击更新会丢弃前一个下载回调""下载失败完全静默"
 * "APK 无完整性校验"三个问题，这里把这几条钉成测试：进度必须真的在动、
 * 取消必须删掉半成品且不误报失败、校验不过必须删文件、重复点击不能起第二个下载。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class AppUpdateDownloaderTest {

    private lateinit var server: MockWebServer

    private lateinit var downloader: AppUpdateDownloader

    /** 纯 ASCII，字节数等于字符数，方便按长度断言落盘大小。 */
    private val payload = "polaris-apk-payload"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        downloader = AppUpdateDownloader(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun releaseInfo(
        fileName: String = APK_FILE_NAME,
        sha256: String? = null,
    ) = ReleaseInfo(
        version = "9.9.9",
        changelog = "",
        apkUrl = server.url("/$fileName").toString(),
        apkFileName = fileName,
        expectedSha256 = sha256,
    )

    private fun apkFile(): File {
        val dir = checkNotNull(
            RuntimeEnvironment.getApplication().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        )
        return File(dir, APK_FILE_NAME)
    }

    /** 轮询到条件成立为止；超时即失败，避免测试挂死。 */
    private fun awaitCondition(
        message: String,
        timeoutMs: Long = 20_000,
        condition: () -> Boolean,
    ) {
        runBlocking {
            withTimeout(timeoutMs) {
                while (!condition()) {
                    delay(20)
                }
            }
        }
    }

    /** 等到"响应头已到"的进度（`totalBytes` 由 `Content-Length` 填上）。 */
    private fun awaitProgressStarted(): AppUpdateDownloader.Progress = runBlocking {
        checkNotNull(
            withTimeout(20_000) {
                downloader.progress.first { it != null && it.totalBytes > 0L }
            },
        )
    }

    private fun awaitResult(): AppUpdateDownloader.DownloadResult = runBlocking {
        withTimeout(20_000) {
            downloader.results.first { it !is AppUpdateDownloader.DownloadResult.Started }
        }
    }

    private fun failedReasons(): List<String> = downloader.results.replayCache
        .filterIsInstance<AppUpdateDownloader.DownloadResult.Failed>()
        .map { it.reason }

    @Test
    fun `下载成功会落盘并回报终态`() {
        server.enqueue(MockResponse().setBody(payload))
        val info = releaseInfo(sha256 = sha256Hex(payload))

        downloader.startDownload(info)
        val result = awaitResult()

        val file = apkFile()
        assertTrue("落盘的安装包应存在", file.exists())
        assertEquals("文件内容应与响应体一致", payload.length.toLong(), file.length())
        // 缺"安装未知应用"权限时是 NeedsPermission，已授权时是 Installed；两者都算下载成功。
        assertTrue(
            "终态应是已下载完成，实际 $result",
            result is AppUpdateDownloader.DownloadResult.NeedsPermission ||
                result is AppUpdateDownloader.DownloadResult.Installed,
        )
        awaitCondition(message = "下载结束后进度应清空") { downloader.progress.value == null }
        assertTrue("下载成功不应报失败，实际 ${failedReasons()}", failedReasons().isEmpty())
    }

    @Test
    fun `下载过程中会持续上报进度`() {
        val body = "a".repeat(256 * 1024)
        server.enqueue(
            MockResponse()
                .setBody(body)
                .throttleBody(8 * 1024, 40, TimeUnit.MILLISECONDS),
        )

        downloader.startDownload(releaseInfo())
        val started = awaitProgressStarted()

        assertEquals("总长应来自 Content-Length", body.length.toLong(), started.totalBytes)
        awaitCondition(message = "进度应真的往前走了") {
            (downloader.progress.value?.bytesRead ?: 0L) > 0L
        }
        val mid = checkNotNull(downloader.progress.value)
        assertTrue("进行中已下载字节应小于总长", mid.bytesRead < mid.totalBytes)
        val fraction = checkNotNull(mid.fraction)
        assertTrue("进行中比例应在 0 与 1 之间，实际 $fraction", fraction > 0f && fraction < 1f)
        downloader.cancelDownload()
    }

    @Test
    fun `取消会中断下载清空进度并删除半成品`() {
        val body = "b".repeat(512 * 1024)
        server.enqueue(
            MockResponse()
                .setBody(body)
                .throttleBody(4 * 1024, 50, TimeUnit.MILLISECONDS),
        )
        val file = apkFile()

        downloader.startDownload(releaseInfo())
        awaitProgressStarted()
        awaitCondition(message = "应已有字节落盘") { file.exists() && file.length() > 0L }

        downloader.cancelDownload()

        awaitCondition(message = "取消后进度应清空") { downloader.progress.value == null }
        awaitCondition(message = "取消后应删除半成品") { !file.exists() }
        assertTrue(
            "取消不是网络故障，不应报下载失败，实际 ${failedReasons()}",
            failedReasons().isEmpty(),
        )
    }

    @Test
    fun `校验不通过会报失败并删除文件`() {
        server.enqueue(MockResponse().setBody(payload))
        // 故意给一个不匹配的摘要：APK 被换掉时必须拦下，绝不能拉起安装。
        val info = releaseInfo(sha256 = "0".repeat(64))

        downloader.startDownload(info)
        val result = awaitResult()

        assertTrue("终态应是失败，实际 $result", result is AppUpdateDownloader.DownloadResult.Failed)
        // 删除发生在 finally 里，比 Failed 事件晚一拍，所以这里要等而不是直接断言。
        awaitCondition(message = "校验不过的安装包必须删掉") { !apkFile().exists() }
    }

    @Test
    fun `已有下载在跑时重复请求不重下`() {
        val body = "c".repeat(512 * 1024)
        server.enqueue(
            MockResponse()
                .setBody(body)
                .throttleBody(4 * 1024, 50, TimeUnit.MILLISECONDS),
        )

        downloader.startDownload(releaseInfo())
        awaitProgressStarted()
        downloader.startDownload(releaseInfo())

        awaitCondition(message = "第二次请求应被回报为已有下载") {
            downloader.results.replayCache.any {
                it is AppUpdateDownloader.DownloadResult.AlreadyDownloading
            }
        }
        assertEquals("服务端只应收到一次请求", 1, server.requestCount)
        downloader.cancelDownload()
    }

    @Test
    fun `总长未知时进度比例为空`() {
        assertNull(
            "服务端没给 Content-Length 时不能编造比例",
            AppUpdateDownloader.Progress(bytesRead = 128, totalBytes = 0).fraction,
        )
        assertEquals(
            "比例应按已下载/总长计算",
            0.5f,
            AppUpdateDownloader.Progress(bytesRead = 50, totalBytes = 100).fraction,
        )
    }

    @Test
    fun `缺安装权限时暂存安装包并回报 NeedsPermission`() {
        val file = apkFile().apply {
            parentFile?.mkdirs()
            writeText(payload)
        }
        downloader.canRequestPackageInstalls = { false }
        downloader.launchInstallIntent = { throw IllegalStateException("缺权限时不应拉起安装界面") }

        val outcome = downloader.installApk(file)

        assertEquals(AppUpdateDownloader.InstallOutcome.NEEDS_PERMISSION, outcome)
        assertTrue("缺权限时应把安装包暂存下来等授权", file.exists())
        assertTrue(
            "缺权限是「等授权」而不是失败",
            downloader.installResult(outcome, file) is AppUpdateDownloader.DownloadResult.NeedsPermission,
        )
    }

    @Test
    fun `拉起安装界面失败会删除安装包并回报失败`() {
        val file = apkFile().apply {
            parentFile?.mkdirs()
            writeText(payload)
        }
        downloader.canRequestPackageInstalls = { false }
        downloader.launchInstallIntent = { throw IllegalStateException("缺权限时不应拉起安装界面") }
        assertEquals(AppUpdateDownloader.InstallOutcome.NEEDS_PERMISSION, downloader.installApk(file))

        // 用户从系统设置回来补装，但系统安装器起不来（没有可处理 ACTION_VIEW 的 Activity）：
        // 旧实现在这里吞掉异常并 return true，用户只看到"点了更新什么都没发生"。
        downloader.canRequestPackageInstalls = { true }
        downloader.launchInstallIntent = { throw IllegalStateException("no installer activity") }

        assertFalse("拉起失败必须如实返回 false", downloader.tryInstallPending())
        assertFalse("拉起失败必须删掉已下好的安装包，不能占着下载目录", file.exists())
        val expected = RuntimeEnvironment.getApplication().getString(R.string.update_download_install_failed)
        assertTrue("必须回报可展示的失败原因，实际 ${failedReasons()}", failedReasons().contains(expected))
    }

    @Test
    fun `授权后补装成功会拉起安装界面并保留安装包`() {
        val file = apkFile().apply {
            parentFile?.mkdirs()
            writeText(payload)
        }
        downloader.canRequestPackageInstalls = { false }
        downloader.launchInstallIntent = { throw IllegalStateException("缺权限时不应拉起安装界面") }
        assertEquals(AppUpdateDownloader.InstallOutcome.NEEDS_PERMISSION, downloader.installApk(file))

        var launched: File? = null
        downloader.canRequestPackageInstalls = { true }
        downloader.launchInstallIntent = { launched = it }

        assertTrue("授权后补装应成功", downloader.tryInstallPending())
        assertEquals("拉起的必须是暂存的那个包", file, launched)
        assertTrue("包已交给系统安装器，成功路径不应删除", file.exists())
        assertTrue("成功路径不应报失败，实际 ${failedReasons()}", failedReasons().isEmpty())
    }

    @Test
    fun `安装终态映射：拉起失败绝不能报成功`() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "mapping-test.apk")

        assertTrue(
            downloader.installResult(AppUpdateDownloader.InstallOutcome.LAUNCHED, file) is
                AppUpdateDownloader.DownloadResult.Installed,
        )
        assertTrue(
            downloader.installResult(AppUpdateDownloader.InstallOutcome.NEEDS_PERMISSION, file) is
                AppUpdateDownloader.DownloadResult.NeedsPermission,
        )
        assertTrue(
            "D3：拉起失败必须回报 Failed",
            downloader.installResult(AppUpdateDownloader.InstallOutcome.LAUNCH_FAILED, file) is
                AppUpdateDownloader.DownloadResult.Failed,
        )
    }

    private fun sha256Hex(text: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val APK_FILE_NAME = "Polaris-test.apk"
    }
}
