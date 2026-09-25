// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 页面骨架，去除原型菜单路由）。

package com.slte.app.ui.v5

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
                    .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = if (tab != null) 118.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 二级页的可滚动主体（无导航，配 V5TopBar(onBack) 使用）。 */
@Composable
fun V5PageBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
