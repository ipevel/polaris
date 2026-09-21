package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.slte.app.R
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class SlteComponentsJvmTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun switch_带开关语义与状态描述() {
        var checked by mutableStateOf(false)
        val offLabel = context.getString(R.string.switch_state_off)

        composeRule.setContent {
            SlteTheme {
                SlteSwitch(checked = checked, onCheckedChange = { checked = it })
            }
        }

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, offLabel))
            .assertIsDisplayed()
            .performClick()

        assertTrue("点击开关应切换为开启", checked)
    }

    @Test
    fun input_展示占位符且输入可回传() {
        var value by mutableStateOf("")
        composeRule.setContent {
            SlteTheme {
                SlteInput(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "请输入邮箱",
                )
            }
        }

        composeRule.onNodeWithText("请输入邮箱").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("user@example.com")

        assertEquals("user@example.com", value)
    }

    @Test
    fun button_禁用态不触发点击() {
        var clicks = 0
        composeRule.setContent {
            SlteTheme {
                Column {
                    SlteButton(text = "禁用", onClick = { clicks++ }, enabled = false)
                    SlteButton(text = "可用", onClick = { clicks++ }, enabled = true)
                }
            }
        }

        composeRule.onNodeWithText("禁用").performClick()
        assertEquals("禁用按钮不应触发回调", 0, clicks)

        composeRule.onNodeWithText("可用").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun rowCard_整行可点击() {
        var clicked = false
        composeRule.setContent {
            SlteTheme {
                SlteRowCard(
                    icon = Icons.Outlined.Info,
                    title = "关于软件",
                    chevron = true,
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("关于软件").assertIsDisplayed().performClick()
        assertTrue("行卡片应可点击", clicked)
    }
}
