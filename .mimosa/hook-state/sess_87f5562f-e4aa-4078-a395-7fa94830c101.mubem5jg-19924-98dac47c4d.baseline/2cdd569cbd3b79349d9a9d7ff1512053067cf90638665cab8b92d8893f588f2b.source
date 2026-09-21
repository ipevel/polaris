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

    @Test
    fun `勾选框与文字间距达到控件标签标准`() {
        render()

        val row = rowNode()
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val gap = text.boundsInRoot.left - row.boundsInRoot.left

        assertEquals(
            "勾选框占用宽度应为 48dp 触控区，实际 $gap px",
            dp(48).toFloat(),
            gap,
            dp(1).toFloat(),
        )
    }

    @Test
    fun `触控区高度不低于48dp`() {
        render()

        val row = rowNode()

        assertTrue("触控区高度 ${row.boundsInRoot.height} px 低于 ${dp(48)} px", row.boundsInRoot.height >= dp(48))
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
