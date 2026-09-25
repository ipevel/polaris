// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.screen.main.DashboardData
import com.slte.app.ui.screen.profile.ProfileData
import com.slte.app.ui.screen.server.NodeItem
import com.slte.app.ui.screen.server.ServerData
import com.slte.app.ui.screen.traffic.TrafficData
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.screens.V5HomeScreen
import com.slte.app.ui.v5.screens.V5MeScreen
import com.slte.app.ui.v5.screens.V5NodesScreen
import com.slte.app.ui.v5.screens.V5TrafficScreen
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v5 四栏导航界面的渲染截图（供 UI 评审确认）。
 *
 * 用 Robolectric 原生图形 + Compose 在 JVM 渲染页面，规避 arm64-only 原生库
 * 无法在 x86_64 模拟器运行的限制。v5 屏幕直接接收纯数据参数，无需 mock
 * ViewModel；不触碰真实网络/内核。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class FourTabsScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File by lazy {
        val explicit = System.getProperty("slte.screenshot.dir")
        val base =
            if (!explicit.isNullOrBlank()) {
                File(explicit)
            } else {
                val cwd = File(System.getProperty("user.dir") ?: ".")
                val root = if (File(cwd, "settings.gradle.kts").isFile) cwd else cwd.parentFile ?: cwd
                File(root, "design/screenshots")
            }
        base.apply { mkdirs() }
    }

    private fun snapshot(tag: String, dark: Boolean, page: @Composable () -> Unit) {
        composeRule.setContent {
            SlteTheme(darkTheme = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    page()
                }
            }
        }
        composeRule.waitForIdle()

        // Robolectric 未实现 PixelCopy，onRoot().captureToImage() 永远等不到回调；
        // 改为把 decor view 直接画进软件 Canvas（NATIVE 图形模式下可真实栅格化）
        val decor = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
        if (decor.width <= 0 || decor.height <= 0) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val bitmap =
            Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        decor.draw(Canvas(bitmap))
        val file = File(outDir, "$tag-${if (dark) "dark" else "light"}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_SAVED=${file.absolutePath}")
    }

    private fun futureEpoch(days: Long): Long = System.currentTimeMillis() / 1000L + days * 86_400L

    private fun dashboardData(connected: Boolean = true): DashboardData = DashboardData(
        usedBytes = 128_000_000_000L,
        totalBytes = 1_024_000_000_000L,
        isValid = true,
        hasPlan = true,
        planName = "Pro 月付套餐",
        daysUntilExpired = 18,
        expiredAt = futureEpoch(18),
        serverName = "香港 01",
        proxyMode = "规则",
        currentIp = "203.0.113.7",
        ipCountryCode = "US",
        isConnected = connected,
        isConnecting = false,
        uploadSpeedBps = if (connected) 3_200_000L else 0L,
        downloadSpeedBps = if (connected) 18_400_000L else 0L,
        speedHistory = if (connected) sampleSpeedHistory() else emptyList(),
        sessionUploadBytes = if (connected) 512_000_000L else 0L,
        sessionDownloadBytes = if (connected) 2_400_000_000L else 0L,
        lanIp = "192.168.1.100",
        appMemoryUsedMb = 168,
        connectedSinceElapsedMs = if (connected) SystemClock.elapsedRealtime() - 2_700_000L else 0L,
    )

    /** 生成 60 点速度波形假数据（上传 bps, 下载 bps），模拟真实流量起伏。 */
    private fun sampleSpeedHistory(points: Int = 60): List<Pair<Long, Long>> = List(points) { i ->
        val wave = kotlin.math.sin(i / 4.5)
        val download = 12_000_000L + (wave * 6_000_000L).toLong() + (i % 7) * 400_000L
        val upload = 2_400_000L + (wave * 1_200_000L).toLong() + (i % 5) * 150_000L
        upload to download.coerceAtLeast(1_000_000L)
    }

    // region 首页

    @Test
    fun 首页亮色() {
        snapshot("home", dark = false) {
            V5HomeScreen(
                data = dashboardData(),
                onToggleConnection = {},
                onVpnPermissionDenied = {},
                vpnRequestIntent = { null },
                onRenew = {},
                onNavSelect = {},
                refreshKernelInfo = {},
            )
        }
    }

    @Test
    fun 首页暗色() {
        snapshot("home", dark = true) {
            V5HomeScreen(
                data = dashboardData(),
                onToggleConnection = {},
                onVpnPermissionDenied = {},
                vpnRequestIntent = { null },
                onRenew = {},
                onNavSelect = {},
                refreshKernelInfo = {},
            )
        }
    }

    @Test
    fun 首页未连接亮色() {
        snapshot("home-off", dark = false) {
            V5HomeScreen(
                data = dashboardData(connected = false),
                onToggleConnection = {},
                onVpnPermissionDenied = {},
                vpnRequestIntent = { null },
                onRenew = {},
                onNavSelect = {},
                refreshKernelInfo = {},
            )
        }
    }

    @Test
    fun 首页未连接暗色() {
        snapshot("home-off", dark = true) {
            V5HomeScreen(
                data = dashboardData(connected = false),
                onToggleConnection = {},
                onVpnPermissionDenied = {},
                vpnRequestIntent = { null },
                onRenew = {},
                onNavSelect = {},
                refreshKernelInfo = {},
            )
        }
    }

    // endregion

    // region 节点

    private fun serverData(): ServerData = ServerData(
        nodes = listOf(
            NodeItem(name = "香港 01", delay = 32, countryCode = "HK"),
            NodeItem(name = "香港 02", delay = 45, countryCode = "HK"),
            NodeItem(name = "日本 01", delay = 68, countryCode = "JP"),
            NodeItem(name = "新加坡 01", delay = 55, countryCode = "SG"),
        ),
    )

    private val proxyGroups = listOf(
        KernelProxyGroupInfo(
            name = "🚀 节点选择",
            type = "Selector",
            now = "香港 01",
            selectable = true,
            members = listOf(
                KernelProxyMember("香港 01", false, 32),
                KernelProxyMember("香港 02", false, 45),
                KernelProxyMember("日本 01", false, 68),
                KernelProxyMember("新加坡 01", false, 55),
            ),
        ),
        KernelProxyGroupInfo(
            name = "自动选择",
            type = "URLTest",
            now = "香港 01",
            selectable = false,
            members = listOf(
                KernelProxyMember("香港 01", false, 32),
                KernelProxyMember("香港 02", false, 45),
            ),
        ),
        KernelProxyGroupInfo(
            name = "流媒体",
            type = "Selector",
            now = "日本 01",
            selectable = true,
            members = listOf(
                KernelProxyMember("日本 01", false, 68),
                KernelProxyMember("新加坡 01", false, 55),
                KernelProxyMember("香港 01", false, 32),
            ),
        ),
    )

    @Test
    fun 节点亮色() {
        snapshot("server", dark = false) {
            V5NodesScreen(
                data = serverData(),
                groups = proxyGroups,
                isLoadingGroups = false,
                testingGroup = null,
                isTestingAll = false,
                onQuickSelect = {},
                onSelectNode = {},
                onSelectInGroup = { _, _ -> },
                onTestGroup = {},
                onStartSpeedTest = {},
                onRefreshSubscription = {},
                onRoutingRules = {},
                onNavSelect = {},
            )
        }
    }

    @Test
    fun 节点暗色() {
        snapshot("server", dark = true) {
            V5NodesScreen(
                data = serverData(),
                groups = proxyGroups,
                isLoadingGroups = false,
                testingGroup = null,
                isTestingAll = false,
                onQuickSelect = {},
                onSelectNode = {},
                onSelectInGroup = { _, _ -> },
                onTestGroup = {},
                onStartSpeedTest = {},
                onRefreshSubscription = {},
                onRoutingRules = {},
                onNavSelect = {},
            )
        }
    }

    // endregion

    // region 流量

    private fun trafficData(): TrafficData = TrafficData(
        records = listOf(
            TrafficLogRecord("2026-09-22", 320_000_000L, 2_400_000_000L),
            TrafficLogRecord("2026-09-21", 180_000_000L, 1_600_000_000L),
            TrafficLogRecord("2026-09-20", 410_000_000L, 3_100_000_000L),
            TrafficLogRecord("2026-09-19", 95_000_000L, 720_000_000L),
            TrafficLogRecord("2026-09-18", 260_000_000L, 1_900_000_000L),
            TrafficLogRecord("2026-09-17", 150_000_000L, 1_100_000_000L),
            TrafficLogRecord("2026-09-16", 75_000_000L, 540_000_000L),
        ),
        isLoading = false,
    )

    @Test
    fun 流量亮色() {
        snapshot("traffic", dark = false) {
            V5TrafficScreen(data = trafficData(), onNavSelect = {})
        }
    }

    @Test
    fun 流量暗色() {
        snapshot("traffic", dark = true) {
            V5TrafficScreen(data = trafficData(), onNavSelect = {})
        }
    }

    // endregion

    // region 我的

    @Test
    fun 我的亮色() {
        snapshot("profile", dark = false) { profile() }
    }

    @Test
    fun 我的暗色() {
        snapshot("profile", dark = true) { profile() }
    }

    private val profileData: ProfileData
        get() = ProfileData(
            subscribeInfo = SubscribeInfo(
                planName = "Pro 月付套餐",
                transferEnable = 1_024_000_000_000L,
                usedTraffic = 128_000_000_000L,
                expiredAt = futureEpoch(18),
                planId = 1,
            ),
            isLoading = false,
            email = "user@example.com",
            balance = "25.00",
            daysUntilExpired = 18,
            telegramDiscussLink = "https://t.me/polaris_support",
        )

    @Composable
    private fun profile() {
        V5MeScreen(
            data = profileData,
            onPlans = {},
            onOrders = {},
            onInvite = {},
            onTickets = {},
            onNotices = {},
            onSettings = {},
            onAbout = {},
            onLogout = {},
            onNavSelect = {},
        )
    }

    // endregion
}
