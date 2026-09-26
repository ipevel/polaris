// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SlteRowCard
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
internal fun UserInfoCard(
    email: String,
    balance: String,
) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            InfoRow(
                icon = SlteIcons.Email,
                text = "${stringResource(R.string.profile_email_label)} ${email.ifBlank { stringResource(R.string.profile_not_logged_in) }}",
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.gap.lg),
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            InfoRow(
                icon = SlteIcons.Balance,
                text =
                "${stringResource(R.string.purchase_balance)} " +
                    stringResource(R.string.currency_symbol) + balance,
            )
        }
    }
}

@Composable
internal fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(Dimens.size.row)
            .padding(horizontal = Dimens.gap.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.icon.lg),

            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Dimens.gap.md))
        Text(
            text = text,
            style = SlteType.title,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ErrorCard(
    messageRes: Int,
    onRetry: () -> Unit,
) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ErrorState(
            message = stringResource(messageRes),
            onRetry = onRetry,
            modifier =
            Modifier.padding(
                horizontal = Dimens.gap.lg,
                vertical = Dimens.gap.lg,
            ),
        )
    }
}

/*
 * 这里原本有一个 `@Composable internal fun LoadingCard()`（`SlteCard` + `LottieLoadingIcon`），
 * **已删除**：全仓零调用（v5 的「我的」页 `V5MeScreen` 有自己的一套骨架，不用它）。
 *
 * 顺带解决一个隐患：`LottieLoadingIcon` 在 Robolectric 下**根本不渲染**（第 12 轮实测：
 * 整个正文区一个像素都没有），留着这个"死掉但看起来像在用的"加载卡片，下次谁把它接回去
 * 就会得到一个"加载中但页面空白"的坑。需要加载态请用 `V5LoadingState`。
 */

@Composable
internal fun NavigateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) = SlteRowCard(
    icon = icon,
    title = title,
    chevron = true,
    onClick = onClick,
)

@Composable
internal fun LogoutCard(onClick: () -> Unit) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SlteShapes.medium,
        onClick = onClick,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimens.size.row)
                .padding(horizontal = Dimens.gap.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = SlteIcons.Logout,
                contentDescription = null,
                modifier = Modifier.size(Dimens.icon.lg),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(Dimens.gap.md))
            Text(
                text = stringResource(R.string.profile_logout),
                style = SlteType.title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
