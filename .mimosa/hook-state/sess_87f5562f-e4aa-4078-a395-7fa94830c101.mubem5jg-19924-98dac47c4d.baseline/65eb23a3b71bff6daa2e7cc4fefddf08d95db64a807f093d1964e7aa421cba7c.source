package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.utils.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class FormControlHeightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun height(tag: String): Float = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height

    private fun dp(value: androidx.compose.ui.unit.Dp) = value.value * composeRule.density.density

    @Test
    fun `页面级表单输入框与主按钮同高`() {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                Column {
                    SlteInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "账号",
                        bordered = false,
                        modifier = Modifier.testTag("field"),
                    )
                    SltePasswordInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "密码",
                        bordered = false,
                        modifier = Modifier.testTag("password"),
                    )
                    SlteButton(
                        text = "登录",
                        onClick = {},
                        style = SlteButtonStyle.Primary,
                        height = Dimens.size.row,
                        modifier = Modifier.testTag("cta"),
                    )
                }
            }
        }

        val field = height("field")
        val password = height("password")
        val cta = height("cta")

        assertEquals("账号框 $field px 与密码框 $password px 不一致", field, password, 1f)
        assertEquals("输入框 $field px 与主按钮 $cta px 不一致", field, cta, 1f)
        assertEquals(dp(Dimens.size.row).toFloat(), field, 1f)
    }

    @Test
    fun `弹层级表单输入框与主按钮同高`() {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                Column {
                    SltePasswordInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "原密码",
                        size = SlteInputSize.Compact,
                        modifier = Modifier.testTag("field"),
                    )
                    SlteButton(
                        text = "保存",
                        onClick = {},
                        style = SlteButtonStyle.Primary,
                        modifier = Modifier.testTag("cta"),
                    )
                }
            }
        }

        val field = height("field")
        val cta = height("cta")

        assertEquals("输入框 $field px 与主按钮 $cta px 不一致", field, cta, 1f)
        assertEquals(dp(Dimens.size.button).toFloat(), field, 1f)
    }
}
