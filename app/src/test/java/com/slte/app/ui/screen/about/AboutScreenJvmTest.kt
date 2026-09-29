// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.slte.app.BuildConfig
import com.slte.app.domain.model.SiteInfo
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.utils.Constants
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 关于页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢设置项）。
 *
 * 盯的是迁移清单里可在 JVM 断言的三项：应用版本、内核版本、导出日志入口。
 * 应用内更新已整体移除，故另有一条**负向断言**防止「检查更新」入口被重新加回来。
 * 不覆盖到位的：导出日志的真实分享 Intent（依赖 `AppLog.export` 与 FileProvider，
 * 属 Android 运行时时序，用 mock 断言"点了没崩"没有证据价值，留给雷电模拟器走查）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class AboutScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(
        kernelVersion: String? = "1.9.2-alpha",
        siteInfo: SiteInfo = SiteInfo(),
    ): UpdateViewModel {
        val vm = mockk<UpdateViewModel>(relaxed = true)
        every { vm.kernelVersion } returns MutableStateFlow(kernelVersion)
        every { vm.siteInfo } returns MutableStateFlow<SiteInfo?>(siteInfo)
        return vm
    }

    private fun content(vm: UpdateViewModel) {
        composeRule.setContent {
            SlteTheme {
                AboutScreen(onBack = {}, viewModel = vm)
            }
        }
    }

    @Test
    fun 渲染应用版本与内核版本() {
        content(viewModel())

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("1.9.2-alpha").assertIsDisplayed()
    }

    @Test
    fun 内核版本缺失时显示占位符() {
        content(viewModel(kernelVersion = null))

        composeRule.onNodeWithText(Constants.PLACEHOLDER_DASH).assertIsDisplayed()
    }

    @Test
    fun 面板下发站点名时覆盖默认应用名() {
        content(viewModel(siteInfo = SiteInfo(appName = "北辰 Polaris", appDescription = "稳定加速客户端")))

        composeRule.onNodeWithText("北辰 Polaris").assertIsDisplayed()
        composeRule.onNodeWithText("稳定加速客户端").assertIsDisplayed()
    }

    @Test
    fun 面板未下发站点名时回落到本地文案() {
        content(viewModel(siteInfo = SiteInfo()))

        // 未下发时用 app_name 资源（"Polaris"）与 about_app_desc 资源兜底。
        composeRule.onNodeWithText("Polaris").assertIsDisplayed()
    }

    @Test
    fun 检查更新入口已移除() {
        content(viewModel())

        // 应用内更新已整体移除：关于页不得再出现任何更新/升级入口，
        // 这条负向断言用于防止后续误把入口加回来。
        composeRule.onNodeWithText("检查更新").assertDoesNotExist()
    }

    @Test
    fun 渲染导出日志入口() {
        content(viewModel())

        composeRule.onNodeWithText("日志导出").assertIsDisplayed()
    }

    @Test
    fun 关于页标题可见() {
        content(viewModel())

        composeRule.onNodeWithText("关于软件").assertIsDisplayed()
    }
}
