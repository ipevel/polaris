// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.V5TopIconButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设计系统组件的语义/行为回归。
 *
 * 组件已随 v5 收敛（原 v4 的 `SlteSwitch` / `SlteInput` / `SlteButton` / `SlteRowCard`
 * 在 `00cb5d4` 被删除），这里跟着改写到现存的 v5 组件，用例意图一一对应：
 * 开关语义 → [V5RowItem] 的 `switchState` 行、输入回传 → [V5Input]、
 * 按钮禁用态 → [V5Button] 的 `onClickEnabled`、整行可点 → [V5RowItem] 的 `onClick`。
 */
@RunWith(AndroidJUnit4::class)
class SlteComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun toggleState(expected: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, expected)

    @Test
    fun 开关行_带开关语义与状态描述() {
        var checked by mutableStateOf(false)

        composeRule.setContent {
            SlteTheme {
                V5RowItem(
                    title = "自动更新",
                    switchState = checked,
                    trailing = { V5Switch(checked = checked) },
                    onClick = { checked = !checked },
                )
            }
        }

        composeRule
            .onNode(toggleState(ToggleableState.Off))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue("点击开关行应切换为开启", checked)
        composeRule.onNode(toggleState(ToggleableState.On)).assertIsDisplayed()
    }

    @Test
    fun 输入框_展示占位符且输入可回传() {
        var value by mutableStateOf("")

        composeRule.setContent {
            SlteTheme {
                V5Input(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "请输入邮箱",
                )
            }
        }

        composeRule.onNodeWithText("请输入邮箱").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("user@example.com")
        composeRule.waitForIdle()

        assertEquals("user@example.com", value)
    }

    @Test
    fun 按钮_禁用态不触发点击() {
        var clicks = 0

        composeRule.setContent {
            SlteTheme {
                Column {
                    V5Button(
                        text = "禁用",
                        style = ButtonStyle.PRIMARY,
                        onClickEnabled = false,
                        onClick = { clicks++ },
                    )
                    V5Button(
                        text = "可用",
                        style = ButtonStyle.PRIMARY,
                        onClick = { clicks++ },
                    )
                }
            }
        }

        // v5 禁用语义是「看得见、按不动」：禁用态连点击动作都不挂，所以这里只能断言动作缺失。
        val disabled = composeRule.onNodeWithText("禁用").fetchSemanticsNode()
        assertFalse("禁用按钮不应挂点击动作", disabled.config.contains(SemanticsActions.OnClick))
        assertEquals("禁用按钮不应触发回调", 0, clicks)

        composeRule.onNodeWithText("可用").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun 行项_整行可点击() {
        var clicked = false

        composeRule.setContent {
            SlteTheme {
                V5RowItem(
                    icon = Icons.Outlined.Info,
                    title = "关于软件",
                    chevron = true,
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("关于软件").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue("行卡片应可点击", clicked)
    }

    @Test
    fun 顶栏图标按钮_有无障碍名称且可点() {
        var clicks = 0

        composeRule.setContent {
            SlteTheme {
                V5TopIconButton(
                    icon = Icons.Outlined.Info,
                    onClick = { clicks++ },
                    contentDescription = "关于",
                )
            }
        }

        composeRule.onNodeWithContentDescription("关于").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }
}
