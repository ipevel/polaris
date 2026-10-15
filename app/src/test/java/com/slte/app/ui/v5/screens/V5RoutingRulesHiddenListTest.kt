// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.slte.app.R
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.screen.settings.CustomGroupState
import com.slte.app.ui.screen.settings.RoutingRulesData
import com.slte.app.ui.screen.settings.RoutingRulesViewModel
import com.slte.app.ui.theme.SlteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.12.1 止血：被隐藏的条目必须在设置页**看得见**，并给出导出入口。
 *
 * 只报「本次读丢了几个」是不够的——丢弃先由别的路径触发时用户永远看不到，那就是静默。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class V5RoutingRulesHiddenListTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `被隐藏条目在设置页可见且给出导出入口`() {
        val viewModel = mockk<RoutingRulesViewModel>(relaxed = true)
        every { viewModel.data } returns
            MutableStateFlow(
                RoutingRulesData(
                    errorMessageRes = R.string.routing_custom_cleaned_invalid,
                    hiddenCustomNames = listOf("Ⓜ️ 微软云盘"),
                    hiddenDomainNames = listOf("bad domain"),
                ),
            )
        every { viewModel.customGroupState } returns MutableStateFlow(CustomGroupState.Closed)

        composeRule.setContent { SlteTheme { V5RoutingRulesScreen(onBack = {}, viewModel = viewModel) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ⓜ️ 微软云盘").assertExists()
        composeRule.onNodeWithText("bad domain").assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.routing_export_backup)).assertExists()
    }
}
