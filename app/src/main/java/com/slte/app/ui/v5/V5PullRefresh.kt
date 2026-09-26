// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.slte.app.ui.theme.V5ThemeColors

/**
 * v5 下拉刷新容器。
 *
 * 行为与 v4 的 `SltePullRefresh` 完全一致（同一个 Material3 `PullToRefreshBox`：同样的下拉
 * 触发阈值、同样的滚动嵌套、同样的无障碍语义），只把指示器的取色从 Material 主题换成 v5 令牌
 * ——否则 v5 页面上会出现一枚 v4 配色的转圈。
 *
 * 注意：`PullToRefreshBox` 依赖子节点的嵌套滚动来接收下拉手势，因此**内容不可滚动时下拉无效**。
 * 空态/错态这类不滚动的居中内容，必须包一层可滚动的脚手架（见公告页 `StateScrollable`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V5PullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = V5ThemeColors.current
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = c.accent,
                containerColor = c.surface,
            )
        },
    ) {
        content()
    }
}
