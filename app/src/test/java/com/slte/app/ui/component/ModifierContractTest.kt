// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「组件必须尊重调用方传入的 `modifier`」回归护栏。
 *
 * 为什么要有这个测试文件：第 14 轮真机走查查出 `V5Button` **声明了 `modifier` 形参却从未使用**，
 * 内部从零拼修饰符链，于是全仓 21 处调用传的 `fillMaxWidth()` / `weight(1f)` 全部被静默丢弃。
 * 表现是"按钮缩在左边一小块"，而且**自动化点击按满宽中心去点会点空**（走查时点登录没反应）。
 *
 * 这类缺陷单元测试与截图都抓不到：文字仍然可见、截图里按钮"看起来还在"，只有量几何尺寸才现形。
 * 所以这里直接用**几何断言**把契约钉死——传满宽就必须是满宽。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class ModifierContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * `V5Button` 必须撑满父容器给它的宽度。
     *
     * 断言方式：让按钮在固定宽度（320dp）的容器里 `fillMaxWidth()`，然后量它的实际宽度。
     * 若组件丢弃了 modifier，按钮会退化成内容宽度（远小于 320dp），这条立刻红。
     */
    @Test
    fun `V5Button 尊重调用方的 fillMaxWidth`() {
        val containerWidth = 320.dp
        composeRule.setContent {
            SlteTheme {
                Box(modifier = Modifier.width(containerWidth)) {
                    V5Button(
                        text = "登录",
                        style = ButtonStyle.PRIMARY,
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithText("登录").fetchSemanticsNode()
        val density = composeRule.density.density
        val expectedPx = containerWidth.value * density
        val actualPx = node.boundsInRoot.width

        assertEquals(
            "V5Button 未撑满父容器：期望 ${expectedPx.toInt()}px（${containerWidth.value}dp），" +
                "实际 ${actualPx.toInt()}px。若两者相差很大，说明组件把调用方的 modifier 丢了。",
            expectedPx,
            actualPx,
            2f, // 允许 2px 的取整误差
        )
    }

    /** 窄内容的按钮仍要能被显式收窄（避免上面那条逼出"永远满宽"的错误实现）。 */
    @Test
    fun `V5Button 在未传宽度约束时保持内容宽度`() {
        composeRule.setContent {
            SlteTheme {
                V5Button(text = "确定", style = ButtonStyle.TONAL, small = true, onClick = {})
            }
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithText("确定").fetchSemanticsNode()
        val widthDp = node.boundsInRoot.width / composeRule.density.density
        // 内容宽度应当明显小于一个常规手机宽度（360dp），且不至于塌成 0。
        // 实测小号按钮的「确定」约 37dp（2 个 CJK 字形 + 左右各若干 padding），
        // 因此下界放到 30dp：再窄就说明尺寸约束被丢了或 padding 被吃掉。
        org.junit.Assert.assertTrue(
            "无宽度约束时按钮宽度异常：${widthDp}dp（应落在内容宽度区间 30..200dp）",
            widthDp in 30f..200f,
        )
    }

    /** 组件仍能正常渲染（防止上面两条因为组件崩了而"通过"）。 */
    @Test
    fun `V5Button 正常渲染文本`() {
        composeRule.setContent {
            SlteTheme {
                V5Button(text = "创建账号", style = ButtonStyle.NEUTRAL, onClick = {})
            }
        }
        composeRule.onNodeWithText("创建账号").assertIsDisplayed()
    }
}
