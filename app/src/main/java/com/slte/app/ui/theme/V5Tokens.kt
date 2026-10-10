// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// v5 设计令牌：间距 / 圆角 / 字号。
//
// 这些令牌把此前散落在各页的字面量收敛到一处，取值与收敛前**完全一致**，
// 因此本次迁移不产生任何视觉变化（迁移前后截图应逐像素一致）。
// 约定：新增界面优先复用这里的档位，不要新造字面量；确实需要新档位时，
// 先在 V5Tokens 里补一档并说明用途，再在界面中使用。
//
// 命名采用「值即名」（dp14 就是 14.dp），这样评审时可以直接核对取值未变。
// 语义化命名留给颜色（见 V5Theme.kt 的 V5Colors），因为颜色的语义是稳定的，
// 而尺寸的语义随布局变化。

package com.slte.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 间距档位：用于 padding / Arrangement.spacedBy / 分隔线厚度等留白。 */
object V5Spacing {
    val dp1 = 1.dp
    val dp2 = 2.dp
    val dp4 = 4.dp
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = 8.dp
    val dp10 = 10.dp
    val dp12 = 12.dp
    val dp14 = 14.dp
    val dp16 = 16.dp
    val dp18 = 18.dp
    val dp20 = 20.dp
    val dp22 = 22.dp
    val dp26 = 26.dp
}

/** 圆角档位：pill 为 50% 胶囊（百分比，配合 RoundedCornerShape 使用）。 */
object V5Radius {
    const val pill = 50

    val r10 = 10.dp
    val r12 = 12.dp
    val r13 = 13.dp
    val r14 = 14.dp
    val r16 = 16.dp
    val r18 = 18.dp
    val r22 = 22.dp
    val r26 = 26.dp
}

/** 字号与字距档位。 */
object V5Type {
    val sp9_5 = 9.5.sp
    val sp10_5 = 10.5.sp
    val sp11 = 11.sp
    val sp11_5 = 11.5.sp
    val sp12 = 12.sp
    val sp12_5 = 12.5.sp
    val sp13 = 13.sp
    val sp13_5 = 13.5.sp
    val sp14 = 14.sp
    val sp15 = 15.sp
    val sp16 = 16.sp
    val sp17 = 17.sp
    val sp18 = 18.sp
    val sp21 = 21.sp
    val sp22 = 22.sp
    val sp26 = 26.sp

    /** 常规字距（导航标签等）。 */
    val tracking = 0.3.sp

    /** 加宽字距（大写短标签等）。 */
    val trackingWide = 1.1.sp
}
