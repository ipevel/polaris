package com.slte.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 项目统一下拉刷新容器（基于 Material3 PullToRefreshBox）。
 *
 * 用法：包裹列表内容，传入 isRefreshing 与 onRefresh；
 * 指示器使用 Material3 默认样式（primary 配色），与全局主题一致。
 *
 * @param isRefreshing 是否正在刷新（驱动指示器显示）
 * @param onRefresh 下拉触发回调（需防重入：回调内先置 isRefreshing=true）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SltePullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        content()
    }
}
