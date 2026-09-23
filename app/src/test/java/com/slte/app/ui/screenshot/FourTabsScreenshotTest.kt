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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.component.SlteBottomNavBar
import com.slte.app.ui.navigation.RootTab
import com.slte.app.ui.screen.giftcard.GiftCardRedeemState
import com.slte.app.ui.screen.giftcard.GiftCardRedeemViewModel
import com.slte.app.ui.screen.main.DashboardData
import com.slte.app.ui.screen.main.MainScreen
import com.slte.app.ui.screen.main.MainViewModel
import com.slte.app.ui.screen.profile.ProfileData
import com.slte.app.ui.screen.profile.ProfileScreen
import com.slte.app.ui.screen.profile.ProfileViewModel
import com.slte.app.ui.screen.server.ServerScreen
import com.slte.app.ui.screen.server.ServerViewModel
import com.slte.app.ui.screen.traffic.TrafficData
import com.slte.app.ui.screen.traffic.TrafficScreen
import com.slte.app.ui.screen.traffic.TrafficViewModel
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.utils.Dimens
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 四栏导航界面的渲染截图（供 UI 评审确认）。
 *
 * 用 Robolectric 原生图形 + Compose captureToImage 在 JVM 渲染页面，
 * 规避 arm64-only 原生库无法在 x86_64 模拟器运行的限制。所有 ViewModel
 * 均以 relaxed mock + 显式打桩 StateFlow 注入，不触碰真实网络/内核。
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

    private fun snapshot(tag: String, tab: RootTab, dark: Boolean, page: @Composable () -> Unit) {
        composeRule.setContent {
            SlteTheme(darkTheme = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = Dimens.bottomNavHeight),
                    ) {
                        page()
                    }
                    SlteBottomNavBar(
                        currentTab = tab,
                        onTabSelected = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
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

    /** 生成 60 点速度波形假数据（上传 bps, 下载 bps），模拟真实流量起伏。 */
    private fun sampleSpeedHistory(points: Int = 60): List<Pair<Long, Long>> = List(points) { i ->
        val wave = kotlin.math.sin(i / 4.5)
        val download = 12_000_000L + (wave * 6_000_000L).toLong() + (i % 7) * 400_000L
        val upload = 2_400_000L + (wave * 1_200_000L).toLong() + (i % 5) * 150_000L
        upload to download.coerceAtLeast(1_000_000L)
    }

    private fun dashboardData(connected: Boolean = true): DashboardData = DashboardData(
        usedBytes = 128_000_000_000L,
        totalBytes = 1_024_000_000_000L,
        isValid = true,
        hasPlan = true,
        planName = "Pro 月付套餐",
        daysUntilExpired = 18,
        expiredAt = futureEpoch(18),
        proxyMode = "规则",
        currentIp = "203.0.113.7",
        ipCountryCode = "US",
        isConnected = connected,
        isConnecting = false,
        uploadSpeedBps = if (connected) 3_200_000L else 0L,
        downloadSpeedBps = if (connected) 18_400_000L else 0L,
        siteName = "北极星 Polaris",
        siteDescription = "稳定 · 快速 · 安全的全球网络",
        speedHistory = if (connected) sampleSpeedHistory() else emptyList(),
        sessionUploadBytes = if (connected) 512_000_000L else 0L,
        sessionDownloadBytes = if (connected) 2_400_000_000L else 0L,
        lanIp = "192.168.1.100",
        appMemoryUsedMb = 168,
        // 计时点 = 当前时刻往前 45 分钟，使启动时长卡显示确定性的 "45m 0s"
        connectedSinceElapsedMs = if (connected) SystemClock.elapsedRealtime() - 2_700_000L else 0L,
    )

    // region 首页

    @Test
    fun 首页亮色() {
        val vm = mockk<MainViewModel>(relaxed = true)
        snapshot("home", RootTab.Home, dark = false) {
            MainScreen(mainViewModel = vm, data = dashboardData(), onRenew = {})
        }
    }

    @Test
    fun 首页暗色() {
        val vm = mockk<MainViewModel>(relaxed = true)
        snapshot("home", RootTab.Home, dark = true) {
            MainScreen(mainViewModel = vm, data = dashboardData(), onRenew = {})
        }
    }

    @Test
    fun 首页未连接亮色() {
        val vm = mockk<MainViewModel>(relaxed = true)
        snapshot("home-off", RootTab.Home, dark = false) {
            MainScreen(mainViewModel = vm, data = dashboardData(connected = false), onRenew = {})
        }
    }

    @Test
    fun 首页未连接暗色() {
        val vm = mockk<MainViewModel>(relaxed = true)
        snapshot("home-off", RootTab.Home, dark = true) {
            MainScreen(mainViewModel = vm, data = dashboardData(connected = false), onRenew = {})
        }
    }

    // endregion

    // region 节点

    private fun serverViewModel(): ServerViewModel {
        val vm = mockk<ServerViewModel>(relaxed = true)
        every { vm.proxyGroups } returns
            MutableStateFlow(
                listOf(
                    KernelProxyGroupInfo(
                        name = "节点选择",
                        type = "Selector",
                        now = "香港 01",
                        selectable = true,
                        members =
                        listOf(
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
                        members =
                        listOf(
                            KernelProxyMember("香港 01", false, 32),
                            KernelProxyMember("香港 02", false, 45),
                        ),
                    ),
                    KernelProxyGroupInfo(
                        name = "流媒体",
                        type = "Selector",
                        now = "日本 01",
                        selectable = true,
                        members =
                        listOf(
                            KernelProxyMember("日本 01", false, 68),
                            KernelProxyMember("新加坡 01", false, 55),
                            KernelProxyMember("香港 01", false, 32),
                        ),
                    ),
                ),
            )
        every { vm.isLoadingGroups } returns MutableStateFlow(false)
        every { vm.testingGroup } returns MutableStateFlow(null)
        return vm
    }

    @Test
    fun 节点亮色() {
        snapshot("server", RootTab.Server, dark = false) {
            ServerScreen(
                subscriptionName = "Pro 月付套餐",
                usedBytes = 128_000_000_000L,
                totalBytes = 1_024_000_000_000L,
                isValid = true,
                hasPlan = true,
                daysUntilExpired = 18,
                expiredAt = futureEpoch(18),
                viewModel = serverViewModel(),
            )
        }
    }

    @Test
    fun 节点暗色() {
        snapshot("server", RootTab.Server, dark = true) {
            ServerScreen(
                subscriptionName = "Pro 月付套餐",
                usedBytes = 128_000_000_000L,
                totalBytes = 1_024_000_000_000L,
                isValid = true,
                hasPlan = true,
                daysUntilExpired = 18,
                expiredAt = futureEpoch(18),
                viewModel = serverViewModel(),
            )
        }
    }

    // endregion

    // region 流量

    private fun trafficViewModel(): TrafficViewModel {
        val vm = mockk<TrafficViewModel>(relaxed = true)
        every { vm.data } returns
            MutableStateFlow(
                TrafficData(
                    records =
                    listOf(
                        TrafficLogRecord("2026-09-22", 320_000_000L, 2_400_000_000L),
                        TrafficLogRecord("2026-09-21", 180_000_000L, 1_600_000_000L),
                        TrafficLogRecord("2026-09-20", 410_000_000L, 3_100_000_000L),
                        TrafficLogRecord("2026-09-19", 95_000_000L, 720_000_000L),
                        TrafficLogRecord("2026-09-18", 260_000_000L, 1_900_000_000L),
                        TrafficLogRecord("2026-09-17", 150_000_000L, 1_100_000_000L),
                        TrafficLogRecord("2026-09-16", 75_000_000L, 540_000_000L),
                    ),
                    isLoading = false,
                    planName = "Pro 月付套餐",
                    usedBytes = 128_000_000_000L,
                    totalBytes = 1_024_000_000_000L,
                    isValid = true,
                    hasPlan = true,
                    daysUntilExpired = 18,
                    expiredAt = futureEpoch(18),
                ),
            )
        return vm
    }

    @Test
    fun 流量亮色() {
        snapshot("traffic", RootTab.Traffic, dark = false) {
            TrafficScreen(onRenew = {}, viewModel = trafficViewModel())
        }
    }

    @Test
    fun 流量暗色() {
        snapshot("traffic", RootTab.Traffic, dark = true) {
            TrafficScreen(onRenew = {}, viewModel = trafficViewModel())
        }
    }

    // endregion

    // region 我的

    @Test
    fun 我的亮色() {
        snapshot("profile", RootTab.Profile, dark = false) { profile() }
    }

    @Test
    fun 我的暗色() {
        snapshot("profile", RootTab.Profile, dark = true) { profile() }
    }

    @Composable
    private fun profile() {
        val vm = mockk<ProfileViewModel>(relaxed = true)
        val gift = mockk<GiftCardRedeemViewModel>(relaxed = true)
        every { vm.data } returns
            MutableStateFlow(
                ProfileData(
                    subscribeInfo =
                    SubscribeInfo(
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
                ),
            )
        every { vm.errorMessageRes } returns MutableStateFlow(null)
        every { gift.state } returns MutableStateFlow(GiftCardRedeemState())
        every { gift.tip } returns MutableStateFlow(null)
        every { gift.redeemed } returns MutableStateFlow(0)

        ProfileScreen(viewModel = vm, giftCardViewModel = gift)
    }

    // endregion
}
