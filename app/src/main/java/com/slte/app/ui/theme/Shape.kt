// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角收敛为三档（原先 4/8/10/14/24/50 六档混用，同为主卡片却有 24dp 与 10dp 两种）：
 * - [SlteRadii.card] 卡片
 * - [SlteRadii.inner] 卡内小容器 / 输入框 / 按钮
 * - [SlteRadii.sheet] 底部面板
 * 胶囊（徽标、分段控件、进度条）一律用 RoundedCornerShape(50)。
 *
 * 新增界面请引用这里的常量，不要在各屏硬编码 RoundedCornerShape(24.dp) 之类的字面量。
 */
object SlteRadii {
    val card = 18.dp
    val inner = 12.dp
    val sheet = 22.dp
    val pill = 50
}

val SlteShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(SlteRadii.inner),
        medium = RoundedCornerShape(SlteRadii.inner),
        large = RoundedCornerShape(SlteRadii.card),
        extraLarge = RoundedCornerShape(SlteRadii.sheet),
    )
