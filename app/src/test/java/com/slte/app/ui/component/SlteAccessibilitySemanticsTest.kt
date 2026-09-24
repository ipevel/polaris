// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.repository.InviteRepository
import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.domain.model.InviteInfo
import com.slte.app.domain.model.InviteStat
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.screen.invite.InviteScreen
import com.slte.app.ui.screen.invite.InviteViewModel
import com.slte.app.ui.screen.order.OrdersScreen
import com.slte.app.ui.screen.order.OrdersViewModel
import com.slte.app.ui.screen.settings.SettingsSwitchRow
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class SlteAccessibilitySemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun SemanticsNode.collectViolations(found: MutableList<String>) {
        val clickable = config.contains(SemanticsActions.OnClick)
        val hasText = config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true
        val described = config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true
        val role = config.getOrNull(SemanticsProperties.Role)
        val state = config.getOrNull(SemanticsProperties.StateDescription)
        if (clickable && !hasText && !described) {
            found += "可点击但无可读标签 role=$role bounds=$boundsInRoot"
        }
        if (role == Role.Switch && state == null) {
            found += "开关缺少 stateDescription bounds=$boundsInRoot"
        }
        children.forEach { it.collectViolations(found) }
    }

    private fun ComposeContentTestRule.auditAccessibility(label: String) {
        val found = mutableListOf<String>()
        onRoot().fetchSemanticsNode().collectViolations(found)
        assertTrue("$label 无障碍标签缺失：\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun 开关触控区不低于48dp() {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                SlteSwitch(checked = false, onCheckedChange = {})
            }
        }

        val height = composeRule.onRoot().fetchSemanticsNode().children.first().boundsInRoot.height
        val minimum = 48 * composeRule.density.density
        assertTrue("开关触控高度 $height px 低于 48dp（$minimum px）", height >= minimum)
    }

    @Test
    fun 核心组件无未标注的可点击节点() {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                Column {
                    SlteButton(text = "保存", onClick = {})
                    SlteRowCard(
                        icon = Icons.Outlined.Info,
                        title = "关于软件",
                        chevron = true,
                        onClick = {},
                    )
                }
            }
        }

        composeRule.auditAccessibility("核心组件")
    }

    @Test
    fun 设置开关行合并标题与开关状态() {
        var checked by mutableStateOf(false)
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Info,
                    title = "自动测速",
                    checked = checked,
                    enabled = true,
                    onCheckedChange = { checked = it },
                )
            }
        }

        composeRule.onNodeWithText("自动测速").assertIsDisplayed()
        composeRule.auditAccessibility("设置开关行")
    }

    @Test
    fun 订单页无未标注的可点击节点() {
        val api = FakeAuthApi()
        api.orders =
            listOf(
                OrderInfoDto(
                    id = 1,
                    tradeNo = "TN-1",
                    planName = "进阶套餐",
                    totalAmount = 5_000,
                    status = 3,
                    createdAt = 1_700_000_000L,
                    expiredAt = 1_800_000_000L,
                ),
            )
        val viewModel = OrdersViewModel(OrderRepository(api))
        viewModel.enterAndRefresh()
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                OrdersScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.auditAccessibility("订单页")
    }

    @Test
    fun 邀请页无未标注的可点击节点() {
        val api = FakeAuthApi()
        api.inviteInfo =
            InviteInfo(
                stat = InviteStat(availableBalance = 56_700, registeredUsers = 3, commissionRate = 25),
                codes = listOf(InviteCodeInfo(code = "ABC123", pv = 0)),
            )
        api.commissionRecords = emptyList()
        api.withdrawMethods = listOf("USDT")
        val viewModel = InviteViewModel(InviteRepository(api))
        viewModel.enterAndRefresh()
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                InviteScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.stat.availableBalance == 56_700 }

        composeRule.auditAccessibility("邀请页")
    }

    @Test
    fun 浅色主题文字对比度达可读下限() {
        auditContrast("浅色", schemeFor(darkTheme = false))
    }

    @Test
    fun 深色主题文字对比度达可读下限() {
        auditContrast("深色", schemeFor(darkTheme = true))
    }

    private fun auditContrast(
        label: String,
        scheme: ColorScheme,
    ) {
        val problems = mutableListOf<String>()
        problems += checkPair(label, "正文", scheme.onSurface, scheme.surface, BODY_TEXT_MIN)
        problems += checkPair(label, "背景正文", scheme.onBackground, scheme.background, BODY_TEXT_MIN)
        problems += checkPair(label, "错误文字", scheme.onError, scheme.error, BODY_TEXT_MIN)
        problems += checkPair(label, "主色按钮大字", scheme.onPrimary, scheme.primary, LARGE_TEXT_MIN)
        problems += checkPair(label, "次要文字大字", scheme.onSurfaceVariant, scheme.surfaceVariant, LARGE_TEXT_MIN)
        assertTrue("对比度不足：\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun schemeFor(darkTheme: Boolean): ColorScheme {
        var captured: ColorScheme? = null
        composeRule.setContent {
            SlteTheme(darkTheme = darkTheme) {
                captured = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(captured) { "未取到 $darkTheme 主题色板" }
    }

    private fun checkPair(
        theme: String,
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ): List<String> {
        val ratio = contrastRatio(foreground, background)
        return if (ratio < minimum) {
            listOf("$theme 主题 $name 对比度 ${"%.2f".format(ratio)} 低于 $minimum")
        } else {
            emptyList()
        }
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        private const val BODY_TEXT_MIN = 4.5

        private const val LARGE_TEXT_MIN = 3.0
    }
}
