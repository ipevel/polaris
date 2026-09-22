// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class CurrencyRenderingEnglishTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `英文下金额只带货币符号不带货币代码`() {
        composeRule.setContent {
            SlteTheme(darkTheme = false) {
                Text(formatCurrency(34_091))
                Text(formatPlusCurrency(56_700))
                Text(formatNegCurrency(1_200))
            }
        }

        composeRule.onNodeWithText("¥340.91").assertExists()
        composeRule.onNodeWithText("+¥567.00").assertExists()
        composeRule.onNodeWithText("-¥12.00").assertExists()

        listOf("CNY", "RMB", "USD").forEach { code ->
            val found = composeRule.onAllNodes(hasText(code, substring = true)).fetchSemanticsNodes().size
            assertEquals("英文金额不应出现货币代码 $code", 0, found)
        }
    }
}
