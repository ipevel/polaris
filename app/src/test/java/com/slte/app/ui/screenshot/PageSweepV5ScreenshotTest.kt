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
import com.slte.app.R
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.kernel.KernelProxyMemberKind
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
 * v5 界面逐页渲染体检（评审用，不改产品代码）。
 *
 * 覆盖：4 个根 Tab 的全状态 × 亮/暗 × 窄屏(320dp)/宽屏(600dp)/三语言，
 * 用于回答"每个页面是否都正常"，并为主理人核验 UX 研究员的呈现层结论提供截图证据。
 *
 * 沿用 [FourTabsScreenshotTest] 的 Robolectric NATIVE 栅格化方案（PixelCopy 在
 * Robolectric 下不可用，直接把 decorView 画进软件 Canvas）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class PageSweepV5ScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "settings.gradle.kts").isFile) cwd else cwd.parentFile ?: cwd
        File(root, "design/screenshots-sweep").apply { mkdirs() }
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

        // 空白/失败渲染体检：统计唯一样本色与"非出现最多的颜色"占比
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val counts = HashMap<Int, Int>()
        pixels.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val top = counts.maxByOrNull { it.value }?.value ?: pixels.size
        val nonBgRatio = 1.0 - top.toDouble() / pixels.size.toDouble()
        val blank = if (counts.size <= 2 || nonBgRatio < 0.002) " SWEEP_BLANK=YES" else ""
        println(
            "SWEEP_OK=$tag size=${bitmap.width}x${bitmap.height} uniq=${counts.size} " +
                "nonBg=${"%.4f".format(nonBgRatio)} file=${file.absolutePath}$blank",
        )
    }

    private fun futureEpoch(days: Long): Long = System.currentTimeMillis() / 1000L + days * 86_400L

    private fun dashboardData(
        connected: Boolean = true,
        connecting: Boolean = false,
        siteName: String = "北极星加速",
    ): DashboardData = DashboardData(
        usedBytes = 128_000_000_000L,
        totalBytes = 1_024_000_000_000L,
        isValid = true,
        hasPlan = true,
        planName = "Pro 月付套餐",
        daysUntilExpired = 18,
        expiredAt = futureEpoch(18),
        siteName = siteName,
        serverName = "香港 01",
        proxyMode = "规则",
        currentIp = "203.0.113.7",
        ipCountryCode = "US",
        isConnected = connected,
        isConnecting = connecting,
        uploadSpeedBps = if (connected) 3_200_000L else 0L,
        downloadSpeedBps = if (connected) 18_400_000L else 0L,
        speedHistory = if (connected) sampleSpeedHistory() else emptyList(),
        sessionUploadBytes = if (connected) 512_000_000L else 0L,
        sessionDownloadBytes = if (connected) 2_400_000_000L else 0L,
        lanIp = "192.168.1.100",
        appMemoryUsedMb = 168,
        connectedSinceElapsedMs = if (connected) SystemClock.elapsedRealtime() - 2_700_000L else 0L,
    )

    private fun sampleSpeedHistory(points: Int = 60): List<Pair<Long, Long>> = List(points) { i ->
        val wave = kotlin.math.sin(i / 4.5)
        val download = 12_000_000L + (wave * 6_000_000L).toLong() + (i % 7) * 400_000L
        val upload = 2_400_000L + (wave * 1_200_000L).toLong() + (i % 5) * 150_000L
        upload to download.coerceAtLeast(1_000_000L)
    }

    @Composable
    private fun home(connected: Boolean = true, connecting: Boolean = false, siteName: String = "北极星加速") {
        V5HomeScreen(
            data = dashboardData(connected = connected, connecting = connecting, siteName = siteName),
            onToggleConnection = {},
            onVpnPermissionDenied = {},
            vpnRequestIntent = { null },
            onRenew = {},
            onNavSelect = {},
            refreshKernelInfo = {},
        )
    }

    /**
     * 节点页的默认折叠态：主组卡（专用键）+ 全部分流组名。
     *
     * 与 ServerViewModel.syncCollapsedSections 的"首见即收起"一致——本组用例是纯屏幕渲染，
     * 不经过 ViewModel，所以这里显式给出真实默认态，用来把"打开节点页是什么样"钉进基线图。
     */
    private fun defaultCollapsedOf(groups: List<KernelProxyGroupInfo>): Set<String> = setOf(com.slte.app.kernel.PRIMARY_SECTION_KEY) + groups.map { it.name }

    @Composable
    private fun nodes(
        groups: List<KernelProxyGroupInfo> = proxyGroups,
        nodes: List<NodeItem> = serverNodes,
        isTestingAll: Boolean = false,
        testingGroup: String? = null,
        collapsedSections: Set<String> = defaultCollapsedOf(groups),
    ) {
        V5NodesScreen(
            data = ServerData(nodes = nodes),
            groups = groups,
            isLoadingGroups = false,
            testingGroup = testingGroup,
            isTestingAll = isTestingAll,
            onSelectPrimary = {},
            onSelectInGroup = { _, _ -> },
            onTestGroup = {},
            onStartSpeedTest = {},
            onRefreshSubscription = {},
            onRoutingRules = {},
            onNavSelect = {},
            collapsedSections = collapsedSections,
        )
    }

    @Composable
    private fun traffic(data: TrafficData) {
        V5TrafficScreen(data = data, onNavSelect = {})
    }

    @Composable
    private fun me(data: ProfileData) {
        V5MeScreen(
            data = data,
            onPlans = {},
            onGiftCard = {},
            onOrders = {},
            onInvite = {},
            onTickets = {},
            onNotices = {},
            onSettings = {},
            onAbout = {},
            onLogout = {},
            onNavSelect = {},
            // 透传非空：Telegram 入口在 v5 重写时丢失，这里让基线图证明它回来了
            onTelegram = {},
        )
    }

    private val serverNodes = listOf(
        NodeItem(name = "香港 01", delay = 32, countryCode = "HK"),
        NodeItem(name = "香港 02", delay = 45, countryCode = "HK"),
        NodeItem(name = "日本 01", delay = 68, countryCode = "JP"),
        NodeItem(name = "新加坡 01", delay = 55, countryCode = "SG"),
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
                KernelProxyMember(name = "自动选择", isGroup = true, delay = null, kind = KernelProxyMemberKind.GROUP),
                KernelProxyMember(name = "故障转移", isGroup = true, delay = null, kind = KernelProxyMemberKind.GROUP),
                KernelProxyMember(name = "DIRECT", isGroup = false, delay = null, kind = KernelProxyMemberKind.DIRECT),
                KernelProxyMember("未测节点", false, 0),
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
            ),
        ),
    )

    private fun trafficData(): TrafficData = TrafficData(
        records = listOf(
            TrafficLogRecord("2026-09-22", 320_000_000L, 2_400_000_000L),
            TrafficLogRecord("2026-09-21", 180_000_000L, 1_600_000_000L),
            TrafficLogRecord("2026-09-20", 410_000_000L, 3_100_000_000L),
        ),
        isLoading = false,
    )

    private fun profileData(
        email: String = "user@example.com",
        withPlan: Boolean = true,
    ): ProfileData = ProfileData(
        subscribeInfo =
        if (withPlan) {
            SubscribeInfo(
                planName = "Pro 月付套餐",
                transferEnable = 1_024_000_000_000L,
                usedTraffic = 128_000_000_000L,
                expiredAt = futureEpoch(18),
                planId = 1,
            )
        } else {
            null
        },
        isLoading = false,
        email = email,
        balance = "25.00",
        daysUntilExpired = if (withPlan) 18 else null,
        telegramDiscussLink = "https://t.me/polaris_support",
    )

    // ==================== 首页 ====================

    @Test
    fun `01-首页-已连接-亮色`() = snapshot("01-home-on-light", dark = false) { home() }

    @Test
    fun `02-首页-已连接-暗色`() = snapshot("02-home-on-dark", dark = true) { home() }

    @Test
    fun `03-首页-未连接-亮色`() = snapshot("03-home-off-light", dark = false) { home(connected = false) }

    // 关键：DashboardData.isConnecting 全 App 只有首页会显示它。若本图与「未连接」完全一致，
    // 即证明"连接中无任何反馈"（UX 研究员 P0）。
    @Test
    fun `04-首页-连接中-亮色`() = snapshot("04-home-connecting-light", dark = false) { home(connected = false, connecting = true) }

    @Test
    fun `05-首页-超长站点名-亮色`() = snapshot("05-home-longsite-light", dark = false) {
        home(siteName = "北极星加速器 · 全球专线 · 官方唯一直营站点 · 高速稳定")
    }

    @Config(qualifiers = "zh-rCN-w320dp-h640dp-420dpi")
    @Test
    fun `06-首页-窄屏320dp-亮色`() = snapshot("06-home-320dp-light", dark = false) { home() }

    @Config(qualifiers = "zh-rCN-w600dp-h960dp-420dpi")
    @Test
    fun `07-首页-宽屏600dp-亮色`() = snapshot("07-home-600dp-light", dark = false) { home() }

    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    @Test
    fun `08-首页-英文-亮色`() = snapshot("08-home-en-light", dark = false) { home() }

    @Config(qualifiers = "b+zh+Hant-w411dp-h891dp-420dpi")
    @Test
    fun `09-首页-繁体-亮色`() = snapshot("09-home-hant-light", dark = false) { home() }

    // ==================== 节点 ====================

    @Test
    fun `10-节点-正常-亮色`() = snapshot("10-nodes-light", dark = false) { nodes() }

    @Test
    fun `11-节点-正常-暗色`() = snapshot("11-nodes-dark", dark = true) { nodes() }

    @Test
    fun `12-节点-无策略组-亮色`() = snapshot("12-nodes-empty-light", dark = false) { nodes(groups = emptyList(), nodes = emptyList()) }

    @Test
    fun `13-节点-测速中-亮色`() = snapshot("13-nodes-testing-light", dark = false) { nodes(isTestingAll = true, testingGroup = "🚀 节点选择") }

    @Config(qualifiers = "zh-rCN-w320dp-h640dp-420dpi")
    @Test
    fun `14-节点-窄屏320dp-亮色`() = snapshot("14-nodes-320dp-light", dark = false) { nodes() }

    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    @Test
    fun `15-节点-英文-亮色`() = snapshot("15-nodes-en-light", dark = false) { nodes() }

    // ==================== 流量 ====================

    @Test
    fun `16-流量-有数据-亮色`() = snapshot("16-traffic-light", dark = false) { traffic(trafficData()) }

    @Test
    fun `17-流量-有数据-暗色`() = snapshot("17-traffic-dark", dark = true) { traffic(trafficData()) }

    @Test
    fun `18-流量-空态-亮色`() = snapshot("18-traffic-empty-light", dark = false) { traffic(TrafficData(records = emptyList(), isLoading = false)) }

    @Test
    fun `19-流量-加载中-亮色`() = snapshot("19-traffic-loading-light", dark = false) { traffic(TrafficData(records = emptyList(), isLoading = true)) }

    // 关键：UX 研究员称"流量页错误态无重试按钮、整页无刷新，失败即死路"。本图用于证实/证伪。
    @Test
    fun `20-流量-加载失败-亮色`() = snapshot("20-traffic-error-light", dark = false) {
        traffic(TrafficData(records = emptyList(), isLoading = false, errorMessageRes = R.string.traffic_load_failed))
    }

    @Config(qualifiers = "zh-rCN-w320dp-h640dp-420dpi")
    @Test
    fun `21-流量-窄屏320dp-亮色`() = snapshot("21-traffic-320dp-light", dark = false) { traffic(trafficData()) }

    // ==================== 我的 ====================

    @Test
    fun `22-我的-有套餐-亮色`() = snapshot("22-me-light", dark = false) { me(profileData()) }

    @Test
    fun `23-我的-有套餐-暗色`() = snapshot("23-me-dark", dark = true) { me(profileData()) }

    @Test
    fun `24-我的-无套餐-亮色`() = snapshot("24-me-noplan-light", dark = false) { me(profileData(withPlan = false)) }

    // 关键：UX 研究员称邮箱 maxLines=1 且无 overflow，长邮箱会硬切。本图用于证实/证伪。
    @Test
    fun `25-我的-超长邮箱-亮色`() = snapshot("25-me-longmail-light", dark = false) {
        me(profileData(email = "averyveryverylongaccountname.for.testing@subdomain.example.com"))
    }

    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    @Test
    fun `26-我的-英文-亮色`() = snapshot("26-me-en-light", dark = false) { me(profileData()) }

    @Config(qualifiers = "b+zh+Hant-w411dp-h891dp-420dpi")
    @Test
    fun `27-我的-繁体-亮色`() = snapshot("27-me-hant-light", dark = false) { me(profileData()) }
}
