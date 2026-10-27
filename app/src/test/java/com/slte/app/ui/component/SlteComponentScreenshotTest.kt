package com.slte.app.ui.component

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 组件截图回归基线（Robolectric NATIVE 渲染，无需模拟器）。
 *
 * 重建基线：`./gradlew :app:recordRoborazziDebug --tests "*SlteComponentScreenshotTest*"`
 * 回归比对：`./gradlew :app:verifyRoborazziDebug`
 * 基线图入库于 `src/test/snapshots/images`，改动视觉规格时随 PR 一并提交。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlteComponentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun disableAccessibilityOverlay() {
        // 防御性关闭无障碍：Robolectric 默认开启 a11y 时 Compose 会绘制语义高亮，
        // 截图回归要的是纯 UI，不能有任何叠加层
        val am =
            RuntimeEnvironment
                .getApplication()
                .getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        shadowOf(am).setEnabled(false)
        shadowOf(am).setTouchExplorationEnabled(false)
    }

    private fun capture(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        // 基线图由 macOS 渲染生成；Linux runner 的字体/栅格化不同，会在 CI 产生假失败
        Assume.assumeFalse(
            "截图基线仅在本地 macOS 校验",
            System.getenv("CI_SKIP_SCREENSHOT") == "true",
        )
        composeRule.setContent {
            SlteTheme(darkTheme = dark) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/$name.png")
    }

    @Test
    fun button_primary_亮色() = capture("slte_button_primary_light", dark = false) {
        SlteButton(text = "立即连接", onClick = {}, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun button_primary_暗色() = capture("slte_button_primary_dark", dark = true) {
        SlteButton(text = "立即连接", onClick = {}, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun button_danger_亮色() = capture("slte_button_danger_light", dark = false) {
        SlteButton(text = "退出登录", onClick = {}, style = SlteButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun button_danger_暗色() = capture("slte_button_danger_dark", dark = true) {
        SlteButton(text = "退出登录", onClick = {}, style = SlteButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun switch_开_亮色() = capture("slte_switch_on_light", dark = false) {
        SlteSwitch(checked = true, onCheckedChange = {})
    }

    @Test
    fun switch_关_暗色() = capture("slte_switch_off_dark", dark = true) {
        SlteSwitch(checked = false, onCheckedChange = {})
    }

    @Test
    fun input_亮色() = capture("slte_input_light", dark = false) {
        SlteInput(value = "", onValueChange = {}, placeholder = "请输入邮箱")
    }

    @Test
    fun input_暗色() = capture("slte_input_dark", dark = true) {
        SlteInput(value = "", onValueChange = {}, placeholder = "请输入邮箱")
    }

    @Test
    fun row_card_亮色() = capture("slte_row_card_light", dark = false) {
        SlteRowCard(icon = SlteIcons.Support, title = "在线客服", subtitle = "工作日 10:00 - 18:00", chevron = true)
    }

    @Test
    fun row_card_暗色() = capture("slte_row_card_dark", dark = true) {
        SlteRowCard(icon = SlteIcons.Support, title = "在线客服", subtitle = "工作日 10:00 - 18:00", chevron = true)
    }
}
