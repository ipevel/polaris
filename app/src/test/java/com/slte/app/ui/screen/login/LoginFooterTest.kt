package com.slte.app.ui.screen.login

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.slte.app.BuildConfig
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class LoginFooterTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var websiteCalls = 0
    private var supportCalls = 0
    private var exportCalls = 0

    private fun content(websiteUrl: String = "https://www.example.com") {
        composeRule.setContent {
            SlteTheme {
                LoginFooter(
                    websiteUrl = websiteUrl,
                    onWebsite = { websiteCalls++ },
                    onSupport = { supportCalls++ },
                    onExportLog = { exportCalls++ },
                )
            }
        }
    }

    @Test
    fun 显示版本号与官网客服按钮() {
        content()

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("官网").assertIsDisplayed()
        composeRule.onNodeWithText("客服").assertIsDisplayed()
    }

    @Test
    fun 官网未配置时只显示客服按钮() {
        content(websiteUrl = "")

        composeRule.onNodeWithText("官网").assertDoesNotExist()
        composeRule.onNodeWithText("客服").assertIsDisplayed()
    }

    @Test
    fun 点击按钮触发对应回调() {
        content()

        composeRule.onNodeWithText("官网").performClick()
        composeRule.onNodeWithText("客服").performClick()

        assertEquals(1, websiteCalls)
        assertEquals(1, supportCalls)
        assertEquals(0, exportCalls)
    }

    @Test
    fun 版本号长按六秒触发日志导出() {
        content()

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(6_100)
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(1, exportCalls)
    }

    @Test
    fun 长按期间手指滑出小文本区域仍能触发导出() {
        content()

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput {
            down(center)
            moveTo(center + Offset(0f, -100f))
        }
        composeRule.mainClock.advanceTimeBy(6_100)
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(1, exportCalls)
    }

    @Test
    fun 长按不足六秒不触发导出() {
        content()

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(0, exportCalls)
    }
}
