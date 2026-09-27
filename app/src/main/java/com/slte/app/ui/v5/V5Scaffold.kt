// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 页面骨架，去除原型菜单路由）。

package com.slte.app.ui.v5

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.V5Spacing
import kotlinx.coroutines.delay

/** 页面骨架：氛围底 + 状态栏避让 + 可选悬浮胶囊导航。 */
@Composable
fun V5PageScaffold(
    tab: NavTab?,
    modifier: Modifier = Modifier,
    breathing: Boolean = false,
    connected: Boolean = false,
    onNavSelect: (NavTab) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().v5Aurora(breathing, connected)) {
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

/**
 * 悬浮胶囊导航为可滚动主体预留的底部高度。
 *
 * 组成：导航胶囊 52dp + 外边距 上下各 16dp + 视觉呼吸余量。
 * 118dp 是旧值，在首页加了 SectionTitle 起头后不够用——最后一块内容会滑到胶囊底下
 * 被半透明导航压住（实测：会话信息的 IPv6 行与导航文字叠在一起）。
 * 提到 150dp 让最后一块内容能完整滑出导航区之上，仍为纯底部留白，不影响首屏观感。
 */
private val BottomNavContentInset = 150.dp

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
            .padding(start = V5Spacing.dp16, end = V5Spacing.dp16, bottom = (if (tab != null) BottomNavContentInset else V5Spacing.dp20) + navInset),
        verticalArrangement = Arrangement.spacedBy(V5Spacing.dp14),
        content = content,
    )
}

/**
 * 一次性错峰入场：进入页面时整块内容淡入并轻微上移，不再逐段各自动。
 *
 * [index] 是区块序号，用它错开各块的启动时间（0/60/120/180ms），形成一次编排式入场而不是
 * 每个 section 各自 fade。只跑一次（`remember` 记住进度），回滚/滚动不会重播。
 */
@Composable
fun Modifier.v5Enter(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        progress.animateTo(1f, animationSpec = tween(420))
    }
    val p = progress.value
    return this
        .alpha(p)
        .offset(y = V5Spacing.dp12 * (1f - p))
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
