// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.login

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class RememberMeRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val label = "记住密码"

    private fun render(
        checked: Boolean = false,
        enabled: Boolean = true,
        onToggle: () -> Unit = {},
    ) {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                RememberMeRow(
                    checked = checked,
                    enabled = enabled,
                    label = label,
                    onToggle = onToggle,
                )
            }
        }
    }

    private fun dp(value: Int) = value * composeRule.density.density

    private fun rowNode() = composeRule
        .onNode(isToggleable() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
        .fetchSemanticsNode()

    /**
     * 开关与文字间距。
     *
     * 这条断言**随 v5 迁移变过**，理由写清楚：
     * v4 这里是 Material `Checkbox`（自带 48dp 触控框），所以原来是"间距应为 48dp"；
     * v5 语言里没有方形复选框，改用 [com.slte.app.ui.v5.V5Switch]（48dp 宽 + 28dp 高 + 9dp 间距），
     * 所以文字起点约在 57dp 处。断言改成"文字在开关右侧、且间距与开关宽度同量级"，
     * 不再钉死某个 v4 专属尺寸。
     *
     * 触控标准的退化是**已知且已登记**的：用户本轮明确要求"不改变无障碍/触控目标"这项工作，
     * 所以这里不假装它仍然达标（详见交付报告的「已知缺口」）。
     */
    @Test
    fun `开关与文字间距合理且文字在右侧`() {
        render()

        val row = rowNode()
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val gap = text.boundsInRoot.left - row.boundsInRoot.left

        assertTrue("文字应在开关右侧，实际间距 $gap px", gap > 0)
        // V5Switch 宽 48dp、其后 9dp 间距 ⇒ 文字起点约 57dp；给 48..72dp 的容差区间。
        assertTrue(
            "间距应落在 48..72dp（开关宽 48dp + 间距），实际 $gap px = ${gap / composeRule.density.density}dp",
            gap in dp(48)..dp(72),
        )
    }

    /**
     * 行高。
     *
     * 同 `开关与文字间距合理且文字在右侧`：v4 的 48dp 来自 Material Checkbox 的触控框，
     * v5 换成 `V5Switch` + 文字后行高变矮。这里改为要求"行高不塌"（不低于 32dp），
     * 而不是继续声称满足 48dp 触控标准——那是用户本轮明确不做的范围。
     */
    @Test
    fun `行高不塌陷`() {
        render()

        val row = rowNode()

        assertTrue("行高 ${row.boundsInRoot.height} px 过小，可能塌陷", row.boundsInRoot.height >= dp(32))
    }

    @Test
    fun `点击文字同样切换勾选`() {
        var toggled = false
        render(onToggle = { toggled = true })

        composeRule.onNodeWithText(label).performClick()

        assertTrue("文字应属于可点击区域", toggled)
    }

    @Test
    fun `合并且可读的单个切换节点`() {
        render(checked = false)

        composeRule
            .onNode(hasText(label) and isToggleable())
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        assertEquals(1, composeRule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
    }

    @Test
    fun `禁用时点击不触发切换`() {
        var toggled = false
        render(enabled = false, onToggle = { toggled = true })

        composeRule.onNodeWithText(label).performClick()

        assertFalse("禁用态不应触发回调", toggled)
    }
}
