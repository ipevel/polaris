// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.V5ThemeColors

/**
 * 「有新版本」提示点的全局开关。
 *
 * 提示点要出现在三个互不相邻的位置（底部「我的」Tab、我的页「关于软件」行、
 * 关于页「检查更新」行），分属不同页面、不同 ViewModel；逐层透传要改四处调用链，
 * 而它本质上是一个全局事实。所以用 CompositionLocal 承载，值由 `MainActivity`
 * 从 `UpdateAvailability` 单例喂入——那里是唯一事实源，这里只做分发。
 *
 * 默认 `false`：截图测试与任何未显式提供该值的渲染都不显示提示点。
 */
val LocalHasAppUpdate = staticCompositionLocalOf { false }

/**
 * 有新版本的提示点：8dp 实心圆，用危险色。
 *
 * [description] 是给读屏用的文案。提示点只靠颜色表达状态，不补文本的话
 * 读屏用户完全感知不到——传 `null` 表示该处已有等价文字（例如行内的 value）。
 */
@Composable
fun UpdateDot(modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(V5ThemeColors.current.danger)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    )
}
