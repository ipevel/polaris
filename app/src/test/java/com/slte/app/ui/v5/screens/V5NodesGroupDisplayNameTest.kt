// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.kernel.KernelProxyMemberKind
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.screen.server.NodeItem
import com.slte.app.ui.screen.server.ServerData
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 节点页组名显示映射的渲染门禁。
 *
 * 硬门禁三条：标题显示当前名；折叠回调与选组回调拿到的仍是**内核原名**；
 * 自建组同名时标题显示原名。另加一条防御性调用点的保守行为。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class V5NodesGroupDisplayNameTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val legacyName = "Ⓜ️ 微软"
    private val currentName = "Ⓜ️ 微软云盘"
    private val microsoftMember = "微软专用节点"

    private fun setNodesScreen(
        customGroupNames: Set<String> = emptySet(),
        collapsedSections: Set<String> = setOf(legacyName),
        onToggleSection: (String) -> Unit = {},
        onSelectInGroup: (String, String) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            SlteTheme {
                V5NodesScreen(
                    data = ServerData(nodes = listOf(NodeItem(name = "香港 01", delay = 32, countryCode = "HK"))),
                    groups =
                    listOf(
                        KernelProxyGroupInfo(
                            name = "🚀 节点选择",
                            type = "Selector",
                            now = "香港 01",
                            selectable = true,
                            members = listOf(KernelProxyMember("香港 01", false, 32)),
                        ),
                        KernelProxyGroupInfo(
                            name = legacyName,
                            type = "Selector",
                            now = "DIRECT",
                            selectable = true,
                            members = listOf(KernelProxyMember(microsoftMember, false, 88)),
                        ),
                    ),
                    isLoadingGroups = false,
                    testingGroup = null,
                    isTestingAll = false,
                    onSelectPrimary = {},
                    onSelectInGroup = onSelectInGroup,
                    onTestGroup = {},
                    onStartSpeedTest = {},
                    onRefreshSubscription = {},
                    onRoutingRules = {},
                    onNavSelect = {},
                    collapsedSections = collapsedSections,
                    onToggleSection = onToggleSection,
                    customGroupNames = customGroupNames,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `内核旧名在卡片标题显示当前名，折叠回调仍是内核原名`() {
        val toggled = mutableListOf<String>()
        setNodesScreen(onToggleSection = { toggled += it })

        composeRule.onNodeWithText(currentName).assertExists()
        composeRule.onNodeWithText(legacyName).assertDoesNotExist()

        composeRule.onNodeWithText(currentName).performClick()
        assertEquals("折叠键必须仍是内核原名，否则折叠态与内核对不上", listOf(legacyName), toggled)
    }

    @Test
    fun `展开后点成员，选组回调拿到的仍是内核原名`() {
        val selected = mutableListOf<Pair<String, String>>()
        setNodesScreen(collapsedSections = emptySet(), onSelectInGroup = { group, member -> selected += group to member })

        composeRule.onNodeWithText(microsoftMember).performClick()

        assertEquals("selectInGroup 的组名入参必须是内核原名", listOf(legacyName to microsoftMember), selected)
    }

    @Test
    fun `自建组与旧内置组同名时标题显示原名`() {
        setNodesScreen(customGroupNames = setOf(legacyName))

        composeRule.onNodeWithText(legacyName).assertExists()
        composeRule.onNodeWithText(currentName).assertDoesNotExist()
    }

    @Test
    fun `防御性调用点拿不到集合时保守显示原名`() {
        composeRule.setContent {
            SlteTheme {
                MemberRow(
                    member =
                    KernelProxyMember(
                        name = legacyName,
                        isGroup = true,
                        delay = null,
                        kind = KernelProxyMemberKind.GROUP,
                    ),
                    selected = false,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(legacyName).assertExists()
    }
}
