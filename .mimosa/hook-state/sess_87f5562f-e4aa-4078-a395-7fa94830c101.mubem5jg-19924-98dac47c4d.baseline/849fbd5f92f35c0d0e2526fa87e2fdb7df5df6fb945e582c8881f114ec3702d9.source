package com.slte.app.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 顶部栏的「正在代理」标识。
 *
 * 仅当 [isConnected] 为 true 时渲染——`DashboardData.isConnected` 已由内核就绪门控
 * （[com.slte.app.kernel.KernelReadiness]）保证为「流量确实走代理」的可信信号，
 * 因此本组件不会有假阳性。未连接 / 连接中一律不渲染任何标识。
 *
 * 颜色与尺寸全部复用既有设计令牌，不硬编码色值。
 */
@Composable
internal fun ProxyStatusBadge(
    isConnected: Boolean,
    proxyMode: String,
    modifier: Modifier = Modifier,
) {
    if (!isConnected) return

    val labelRes = proxyModeLabelRes(proxyMode) ?: R.string.dashboard_proxy_rule

    Row(
        modifier =
        modifier
            .border(
                width = Dimens.strokeMedium,
                color = SlteColors.current.statusSuccess,
                shape = RoundedCornerShape(Dimens.planStatusChipCornerRadius),
            )
            .background(
                color = SlteColors.current.statusSuccessBg,
                shape = RoundedCornerShape(Dimens.planStatusChipCornerRadius),
            )
            .padding(horizontal = Dimens.gap.sm, vertical = Dimens.planStatusPaddingV)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap.xs),
    ) {
        Icon(
            imageVector = SlteIcons.CurrentIp,
            contentDescription = null,
            tint = SlteColors.current.statusSuccess,
            modifier = Modifier.size(Dimens.icon.sm),
        )
        Text(
            text = stringResource(R.string.status_connected),
            style = SlteType.caption,
            fontWeight = FontWeight.SemiBold,
            color = SlteColors.current.statusSuccess,
        )
        Text(
            text = stringResource(R.string.plan_separator),
            style = SlteType.caption,
            color = SlteColors.current.statusSuccess,
        )
        Text(
            text = stringResource(labelRes),
            style = SlteType.caption,
            fontWeight = FontWeight.SemiBold,
            color = SlteColors.current.statusSuccess,
        )
    }
}
