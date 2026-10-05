// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5PasswordInput
import com.slte.app.utils.Dimens

@Preview(name = "按钮 · 浅色", showBackground = true, widthDp = 360)
@Preview(name = "按钮 · 深色", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewV5Button() {
    SlteTheme {
        Column(
            modifier = Modifier.padding(Dimens.gap.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        ) {
            V5Button(text = "主要操作", style = ButtonStyle.PRIMARY, onClick = {})
            V5Button(text = "成功操作", style = ButtonStyle.GREEN, onClick = {})
            V5Button(text = "中性操作", style = ButtonStyle.NEUTRAL, onClick = {})
            V5Button(text = "弱强调", style = ButtonStyle.TONAL, onClick = {})
            V5Button(text = "危险操作", style = ButtonStyle.DANGER, onClick = {})
            V5Button(text = "禁用态", style = ButtonStyle.PRIMARY, onClickEnabled = false, onClick = {})
            V5Button(text = "加载中", style = ButtonStyle.PRIMARY, loading = true, onClick = {})
        }
    }
}

@Preview(name = "输入框 · 浅色", showBackground = true, widthDp = 360)
@Preview(name = "输入框 · 深色", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewV5Input() {
    SlteTheme {
        Column(
            modifier = Modifier.padding(Dimens.gap.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        ) {
            V5Input(
                value = "",
                onValueChange = {},
                placeholder = "请输入邮箱",
                icon = SlteIcons.Email,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            )
            V5PasswordInput(
                value = "••••••••",
                onValueChange = {},
                placeholder = "密码",
            )
            V5Input(
                value = "utun",
                onValueChange = {},
                placeholder = "紧凑档",
                small = true,
            )
            V5Input(
                value = "只读内容",
                onValueChange = {},
                placeholder = "只读",
                readOnly = true,
            )
        }
    }
}

@Preview(name = "行卡片 · 浅色", showBackground = true, widthDp = 360)
@Preview(name = "行卡片 · 深色", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSlteRowCard() {
    SlteTheme {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimens.gap.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
        ) {
            SlteRowCard(
                icon = SlteIcons.Server,
                title = "节点列表",
                value = "12 个",
                chevron = true,
                onClick = {},
            )
            SlteRowCard(
                icon = SlteIcons.Expiry,
                title = "到期时间",
                subtitle = "2026-12-31",
            )
            SlteRowCard(
                icon = SlteIcons.SyncSubscription,
                title = "更新订阅",
                onClick = {},
            )
        }
    }
}

@Preview(name = "开关 · 浅色", showBackground = true)
@Preview(name = "开关 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSlteSwitch() {
    SlteTheme {
        Column(
            modifier = Modifier.padding(Dimens.gap.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        ) {
            SlteSwitch(checked = true, onCheckedChange = {})
            SlteSwitch(checked = false, onCheckedChange = {})
            SlteSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@Preview(name = "圆形图标按钮 · 浅色", showBackground = true)
@Preview(name = "圆形图标按钮 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCircleIconButton() {
    SlteTheme {
        Column(
            modifier = Modifier.padding(Dimens.gap.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        ) {
            CircleIconButton(icon = SlteIcons.Back, description = "返回", onClick = {})
            CircleIconButton(icon = SlteIcons.Support, description = "客服", onClick = {}, showBackground = false)
        }
    }
}
