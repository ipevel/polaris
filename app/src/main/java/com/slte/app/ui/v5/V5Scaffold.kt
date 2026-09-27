// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 页面骨架，去除原型菜单路由）。

package com.slte.app.ui.v5

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.V5Spacing

/** 页面骨架：氛围底 + 状态栏避让 + 可选悬浮胶囊导航。 */
@Composable
fun V5PageScaffold(
    tab: NavTab?,
    modifier: Modifier = Modifier,
    onNavSelect: (NavTab) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().v5Aurora()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            content()
        }
        if (tab != null) {
            FloatingPillNav(
                active = tab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = V5Spacing.dp14, end = V5Spacing.dp14, bottom = V5Spacing.dp16)
                    .navigationBarsPadding(),
                onSelect = onNavSelect,
            )
        }
    }
}

/** 页签页的可滚动主体（自动为悬浮导航留出底部空间）。 */
@Composable
fun V5ScrollBody(
    tab: NavTab?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = V5Spacing.dp16, end = V5Spacing.dp16, bottom = (if (tab != null) 118.dp else V5Spacing.dp20) + navInset),
        verticalArrangement = Arrangement.spacedBy(V5Spacing.dp14),
        content = content,
    )
}

/** 二级页的可滚动主体（无导航，配 V5TopBar(onBack) 使用）。 */
@Composable
fun V5PageBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = V5Spacing.dp16, end = V5Spacing.dp16, bottom = 24.dp + navInset),
        verticalArrangement = Arrangement.spacedBy(V5Spacing.dp14),
        content = content,
    )
}
