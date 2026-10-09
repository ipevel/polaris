// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.kernel.KernelProxyMemberKind
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Divider
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.screens.MemberRow
import com.slte.app.utils.Constants
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 组件级截图回归基线（Roborazzi，**仅本地 verify**）。
 *
 * 与 [PageSweepV5ScreenshotTest] 等"整页体检"测试的分工：
 * - 那些测试只把 PNG 写到 `design/screenshots-sweep/`（被 .gitignore 忽略、零断言），
 *   是给人看的评审证据；
 * - 这里把**组件级**渲染结果与入库基线逐像素比对，是能拦住回归的门禁。
 *
 * 用法（CI 与 `.scripts/precheck.ps1` 都不接，见仓库 .gitignore 的例外说明）：
 * - 比对：`.\gradlew.bat :app:verifyRoborazziDebug`
 * - 界面有意改动后重录：`.\gradlew.bat :app:recordRoborazziDebug`
 * 基线目录：`app/src/test/snapshots/images/`。
 *
 * 基线一律用**合成数据**（假节点名、假延迟），不含任何真实账号/面板/订阅信息 ——
 * 这正是它能进仓库、而整页真机截图不能的原因。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class V5ComponentsRoborazziTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val imageDir: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "settings.gradle.kts").isFile) cwd else cwd.parentFile ?: cwd
        File(root, "app/src/test/snapshots/images").apply { mkdirs() }
    }

    /**
     * 渲染 [content] 并与基线比对。
     *
     * 传绝对路径（而不是 Roborazzi 默认的 `build/outputs/roborazzi`）是为了让基线
     * 落在 `app/src/test/snapshots/` 这个能被 git 跟踪的位置，不依赖任何
     * `roborazzi.outputDir` 配置，换机器/换插件版本都不会漂。
     */
    private fun capture(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            SlteTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(V5ThemeColors.current.bg),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.activity.window.decorView.captureRoboImage(File(imageDir, "$name.png"))
    }

    @Test
    fun 按钮_全样式与禁用态() {
        capture("v5_button_styles") {
            V5Button(text = "主要", style = ButtonStyle.PRIMARY, onClick = {})
            V5Button(text = "危险", style = ButtonStyle.DANGER, onClick = {})
            V5Button(text = "中性", style = ButtonStyle.NEUTRAL, onClick = {})
            V5Button(text = "禁用", style = ButtonStyle.PRIMARY, onClickEnabled = false, onClick = {})
        }
    }

    @Test
    fun 行项_图标开关与箭头() {
        capture("v5_row_items") {
            V5RowItem(icon = Icons.Outlined.Info, title = "关于软件", chevron = true, onClick = {})
            V5RowItem(title = "自动更新", sub = "仅在 Wi-Fi 下检查", value = "开启", onClick = {})
            V5RowItem(
                title = "本地分流方案",
                switchState = true,
                trailing = { V5Switch(checked = true) },
                onClick = {},
            )
            V5RowItem(title = "退出登录", danger = true, chevron = true, onClick = {})
        }
    }

    @Test
    fun 输入框_默认与只读() {
        capture("v5_input_states") {
            V5Input(value = "", onValueChange = {}, placeholder = "请输入邮箱")
            V5Input(value = "user@example.com", onValueChange = {}, placeholder = "请输入邮箱")
            V5Input(value = "只读内容", onValueChange = {}, placeholder = "占位", readOnly = true)
        }
    }

    @Test
    fun 徽标_五种色调() {
        capture("v5_chips") {
            V5Chip(ChipTone.OK, "已连接")
            V5Chip(ChipTone.WARN, "剩余 7 天")
            V5Chip(ChipTone.DANGER, "已过期")
            V5Chip(ChipTone.NEUTRAL, "未测")
            V5Chip(ChipTone.ACCENT, "使用中", dot = true)
        }
    }

    /**
     * 节点行的全部延迟/离线状态。
     *
     * `offline = true`（探测确认不可达）是 2026-10-09 跟进上游新增的状态，
     * 与灰色的「超时」在视觉上必须能区分开——这条基线就是那次改动的凭据。
     */
    @Test
    fun 节点行_延迟与离线状态() {
        val alive = KernelProxyMember(name = "🇺🇸 ＵＳ · LA", isGroup = false, delay = 157)
        val slow = KernelProxyMember(name = "🇯🇵 ＪＰ · Tokyo", isGroup = false, delay = 480)
        val pending = KernelProxyMember(name = "🇸🇬 ＳＧ · Singapore", isGroup = false, delay = null)
        val timedOut =
            KernelProxyMember(name = "🇭🇰 ＨＫ · HongKong", isGroup = false, delay = Constants.DELAY_TIMEOUT)

        capture("v5_node_member_rows") {
            MemberRow(member = alive, selected = true)
            MemberRow(member = slow, selected = false)
            MemberRow(member = pending, selected = false)
            MemberRow(member = timedOut, selected = false)
            MemberRow(member = timedOut, selected = false, offline = true)
            V5Divider()
            MemberRow(
                member = KernelProxyMember(
                    name = "自动选择",
                    isGroup = true,
                    delay = null,
                    kind = KernelProxyMemberKind.GROUP,
                ),
                selected = false,
            )
            MemberRow(
                member = KernelProxyMember(
                    name = "DIRECT",
                    isGroup = false,
                    delay = null,
                    kind = KernelProxyMemberKind.DIRECT,
                ),
                selected = false,
            )
        }
    }

    @Test
    fun 按钮_暗色主题() {
        capture("v5_button_styles_dark", dark = true) {
            V5Button(text = "主要", style = ButtonStyle.PRIMARY, onClick = {})
            V5Button(text = "危险", style = ButtonStyle.DANGER, onClick = {})
            V5Button(text = "禁用", style = ButtonStyle.PRIMARY, onClickEnabled = false, onClick = {})
        }
    }
}
